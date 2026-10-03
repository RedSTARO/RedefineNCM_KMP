package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackupImportTest {
    private val withLibrary = """{"localLibrary":[{"id":"imported","name":"Test","createdAtMillis":1,"updatedAtMillis":1}]}"""

    @Test
    fun privacySnapshotChangesBeforeAnyStorageWaitAndPlaylistsFollowDurableSettings() = runTest {
        val events = mutableListOf<String>()
        val result = importSettingsBackup(
            withLibrary,
            applySettings = { events += "write"; true },
            flushSettings = { events += "flush" },
            onSettingsApplied = { events += "privacy-snapshot" },
            reloadSettings = { events += "durable-snapshot" },
            importLibrary = { assertEquals("imported", it.single().id); events += "library"; Result.success(1) },
        )
        assertIs<SettingsBackupImportResult.Imported>(result)
        assertEquals(listOf("write", "privacy-snapshot", "flush", "durable-snapshot", "library"), events)
    }

    @Test
    fun libraryFailureIsAPartialResultAndCannotBeReportedAsComplete() = runTest {
        assertNotNull(decodeBackupLocalLibrary(withLibrary))
        val failure = IllegalStateException("storage full")
        var settingsSaved = false
        val result = importSettingsBackup(
            withLibrary, { true }, { settingsSaved = true }, {}, {}, { Result.failure(failure) },
        )
        assertTrue(settingsSaved)
        assertEquals(failure, assertIs<SettingsBackupImportResult.LibraryFailed>(result).cause)
    }

    @Test
    fun aSettingsSaveFailureRestoresTheSnapshotWithoutImportingPlaylists() = runTest {
        val events = mutableListOf<String>()
        val result = importSettingsBackup(
            withLibrary, { events += "write"; true },
            { events += "flush"; throw IllegalStateException("disk unavailable") },
            { events += "privacy-snapshot" }, { events += "rollback-snapshot" },
            { error("The library must not be imported after preferences failed") },
        )
        assertIs<SettingsBackupImportResult.SettingsSaveFailed>(result)
        assertEquals(listOf("write", "privacy-snapshot", "flush", "rollback-snapshot"), events)
    }

    @Test
    fun aRejectedWriteStillRefreshesAnyPartiallyAppliedPreferences() = runTest {
        var refreshed = false
        val result = importSettingsBackup(
            "{}", { false }, { error("No durable barrier for a rejected write") },
            { refreshed = true }, {}, { error("No library import") },
        )
        assertTrue(refreshed)
        assertIs<SettingsBackupImportResult.Rejected>(result)
    }

    @Test
    fun noLibraryOrAnEmptyLibraryDoesNotWriteAnOwnedDataRow() = runTest {
        for (json in listOf("{}", """{"localLibrary":[]}""")) {
            assertIs<SettingsBackupImportResult.Imported>(importSettingsBackup(
                json, { true }, {}, {}, {}, { error("There is nothing to merge") },
            ))
        }
    }

    @Test
    fun leavingThePageAfterWritesBeginDoesNotInterruptPersistenceAndRefresh() = runTest {
        val flushing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val worker = launch {
            importSettingsBackup(
                withLibrary, { events += "write"; true },
                { flushing.complete(Unit); release.await(); events += "persisted" },
                { events += "privacy-snapshot" }, { events += "durable-snapshot" },
                { events += "library"; Result.success(1) },
            )
        }
        try {
            flushing.await()
            assertEquals(1, settingsImportCount.value)
            worker.cancel()
            runCurrent()
        } finally { release.complete(Unit) }
        worker.join()
        assertEquals(listOf("write", "privacy-snapshot", "persisted", "durable-snapshot", "library"), events)
        assertEquals(0, settingsImportCount.value)
        assertIs<SettingsBackupImportResult.Imported>(settingsImportResult.value)
        consumeSettingsImportResult(assertNotNull(settingsImportResult.value))
        // The cancelled caller must also release the global import lock.
        assertIs<SettingsBackupImportResult.Imported>(importSettingsBackup("{}", { true }, {}, {}, {}, { Result.success(0) }))
    }
}
