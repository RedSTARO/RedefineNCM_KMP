package com.leejlredstar.redefinencm.kmp.data.db

import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserStorageQuotaTest {
    private class LimitedStorage : BrowserDatabaseStorage {
        val values = linkedMapOf<String, String>()
        val removals = mutableListOf<String>()
        var capacity = Int.MAX_VALUE
        var blocked = false

        override fun keys(): List<String> {
            check(!blocked)
            return values.keys.toList()
        }
        override fun get(key: String): String? = values[key]
        override fun set(key: String, value: String) {
            check(!blocked)
            check(values.filterKeys { it != key }.values.sumOf { it.length } + value.length <= capacity)
            values[key] = value
        }
        override fun remove(key: String) {
            check(!blocked)
            removals += key
            values.remove(key)
        }
    }

    private fun AppDatabase.seedUserData() {
        localLibraryQueries.upsert("playlists-and-favourites-".repeat(40))
        playerStatusQueries.upsert("saved-play-queue")
        downloadQueueQueries.upsert("saved-download-queue")
    }

    private fun AppDatabase.assertUserData() {
        assertEquals("playlists-and-favourites-".repeat(40), localLibraryQueries.select().executeAsOne())
        assertEquals("saved-play-queue", playerStatusQueries.select().executeAsOne())
        assertEquals("saved-download-queue", downloadQueueQueries.select().executeAsOne())
    }

    @Test
    fun cacheEvictionNeverSelectsUserDataEvenWhenItIsTheLargestRow() {
        val storage = LimitedStorage()
        val db = AppDatabase(BrowserStorageSqlDriver(storage))
        db.seedUserData()
        db.cachedLyricQueries.upsert(1L, "old-cache".repeat(10))
        storage.capacity = storage.values.values.sumOf { it.length }

        db.cachedLyricQueries.upsert(2L, "new-cache".repeat(10))

        val reopened = AppDatabase(BrowserStorageSqlDriver(storage))
        reopened.assertUserData()
        assertEquals("new-cache".repeat(10), reopened.cachedLyricQueries.selectBySongId(2L).executeAsOne())
        assertNull(reopened.cachedLyricQueries.selectBySongId(1L).executeAsOneOrNull())
        assertTrue(storage.removals.all { it.startsWith("redefinencm.db.Cached") })
    }

    @Test
    fun oversizedCacheStaysInMemoryWithoutDisablingLaterUserWrites() {
        val storage = LimitedStorage()
        val db = AppDatabase(BrowserStorageSqlDriver(storage))
        db.seedUserData()
        storage.capacity = storage.values.values.sumOf { it.length } + 20

        db.cachedLyricQueries.upsert(99L, "x".repeat(10_000))
        assertEquals(10_000, db.cachedLyricQueries.selectBySongId(99L).executeAsOne().length)
        AppDatabase(BrowserStorageSqlDriver(storage)).assertUserData()
        db.playerStatusQueries.upsert("updated-play-queue")

        val reopened = AppDatabase(BrowserStorageSqlDriver(storage))
        assertEquals("updated-play-queue", reopened.playerStatusQueries.select().executeAsOne())
        assertNull(reopened.cachedLyricQueries.selectBySongId(99L).executeAsOneOrNull())
    }

    @Test
    fun failedUserWritesKeepBothTheStoredAndPublishedValues() {
        val storage = LimitedStorage()
        val db = AppDatabase(BrowserStorageSqlDriver(storage))
        db.seedUserData()
        storage.capacity = storage.values.values.sumOf { it.length }

        assertFailsWith<IllegalStateException> { db.localLibraryQueries.upsert("x".repeat(10_000)) }
        assertFailsWith<IllegalStateException> { db.playerStatusQueries.upsert("x".repeat(10_000)) }
        assertFailsWith<IllegalStateException> { db.downloadQueueQueries.upsert("x".repeat(10_000)) }

        db.assertUserData()
        AppDatabase(BrowserStorageSqlDriver(storage)).assertUserData()
        storage.capacity += 10_000
        db.localLibraryQueries.upsert("saved-after-retry")
        assertEquals("saved-after-retry", AppDatabase(BrowserStorageSqlDriver(storage)).localLibraryQueries.select().executeAsOne())
    }

    @Test
    fun blockedStorageDoesNotPretendToSavePlaylistsOrDeleteDownloads() {
        val storage = LimitedStorage()
        val db = AppDatabase(BrowserStorageSqlDriver(storage))
        db.seedUserData()
        storage.blocked = true
        assertFailsWith<IllegalStateException> { db.downloadQueueQueries.clear() }
        db.assertUserData()

        val unavailable = AppDatabase(BrowserStorageSqlDriver(storage))
        assertFailsWith<IllegalStateException> { unavailable.localLibraryQueries.upsert("unsaved") }
        assertFailsWith<IllegalStateException> { unavailable.localLibraryQueries.select().executeAsOneOrNull() }
    }

    @Test
    fun rollingBackACacheTransactionNeverRewritesUnchangedUserData() {
        val storage = LimitedStorage()
        val db = AppDatabase(BrowserStorageSqlDriver(storage))
        db.seedUserData()
        storage.capacity = storage.values.values.sumOf { it.length }

        db.transaction {
            db.cachedLyricQueries.upsert(99L, "x".repeat(10_000))
            rollback()
        }

        db.assertUserData()
        AppDatabase(BrowserStorageSqlDriver(storage)).assertUserData()
        assertTrue(storage.removals.all { it.startsWith("redefinencm.db.Cached") })
    }

    @Test
    fun aLibraryBlockedAtStartupReportsAnErrorAndRetryRecoversTheSavedPlaylists() = runTest {
        val storage = LimitedStorage()
        val saved = LocalLibraryStore(AppDatabase(BrowserStorageSqlDriver(storage)))
            .createPlaylist("Saved before permission denial").getOrThrow()
        storage.blocked = true
        val database = AppDatabase(BrowserStorageSqlDriver(storage))
        val library = LocalLibraryStore(database)

        assertTrue(library.reload().isFailure)
        assertTrue(library.readFailed.value)
        assertTrue(library.createPlaylist("Must not replace saved data").isFailure)
        database.cachedLyricQueries.upsert(1L, "fresh-in-memory")

        storage.blocked = false
        library.reload().getOrThrow()
        assertEquals(listOf(saved), library.snapshot().playlists)
        assertEquals(false, library.readFailed.value)
        assertEquals("fresh-in-memory", database.cachedLyricQueries.selectBySongId(1L).executeAsOne())
        library.createPlaylist("After retry").getOrThrow()
        val reopened = LocalLibraryStore(AppDatabase(BrowserStorageSqlDriver(storage)))
        assertEquals(listOf(saved.name, "After retry"), reopened.snapshot().playlists.map { it.name })
    }

    @Test
    fun queueReadsAlsoDistinguishUnavailableStorageFromAnEmptyQueue() {
        val storage = LimitedStorage()
        AppDatabase(BrowserStorageSqlDriver(storage)).seedUserData()
        storage.blocked = true
        val database = AppDatabase(BrowserStorageSqlDriver(storage))
        assertFailsWith<IllegalStateException> { database.playerStatusQueries.select().executeAsOneOrNull() }
        assertFailsWith<IllegalStateException> { database.downloadQueueQueries.select().executeAsOneOrNull() }
        assertFailsWith<IllegalStateException> { database.downloadQueueQueries.clear() }
        storage.blocked = false
        database.assertUserData()
        database.downloadQueueQueries.clear()
        assertNull(AppDatabase(BrowserStorageSqlDriver(storage)).downloadQueueQueries.select().executeAsOneOrNull())
    }

    @Test
    fun recoveryCannotDowngradeANewerStoredSchemaOrOverwriteItsLibrary() {
        val storage = LimitedStorage()
        AppDatabase(BrowserStorageSqlDriver(storage)).seedUserData()
        storage.values["redefinencm.db.schemaVersion"] = (AppDatabase.Schema.version + 1L).toString()
        val original = storage.values.toMap()
        storage.blocked = true
        val driver = BrowserStorageSqlDriver(storage)
        // Startup can only initialize a volatile schema while browser permissions are denied.
        driver.schemaVersion = AppDatabase.Schema.version
        val database = AppDatabase(driver)

        storage.blocked = false
        assertFailsWith<IllegalStateException> { database.localLibraryQueries.select().executeAsOneOrNull() }
        assertFailsWith<IllegalStateException> { database.localLibraryQueries.upsert("must-not-overwrite") }
        assertEquals(original, storage.values)
    }

    @Test
    fun aTransactionRecoversBeforeItsSnapshotSoRollbackKeepsOwnedData() {
        val storage = LimitedStorage()
        AppDatabase(BrowserStorageSqlDriver(storage)).seedUserData()
        storage.blocked = true
        val database = AppDatabase(BrowserStorageSqlDriver(storage))
        storage.blocked = false
        database.transaction {
            database.cachedLyricQueries.upsert(1L, "discard")
            rollback()
        }
        database.assertUserData()
        AppDatabase(BrowserStorageSqlDriver(storage)).assertUserData()
    }

    @Test
    fun permissionReturningInsideACacheTransactionCannotChangeItsOwnedSnapshot() {
        val storage = LimitedStorage()
        AppDatabase(BrowserStorageSqlDriver(storage)).seedUserData()
        storage.blocked = true
        val database = AppDatabase(BrowserStorageSqlDriver(storage))
        database.transaction {
            database.cachedLyricQueries.upsert(1L, "discard")
            storage.blocked = false
            assertFailsWith<IllegalStateException> { database.localLibraryQueries.select().executeAsOneOrNull() }
            rollback()
        }
        database.assertUserData()
        AppDatabase(BrowserStorageSqlDriver(storage)).assertUserData()
    }
}
