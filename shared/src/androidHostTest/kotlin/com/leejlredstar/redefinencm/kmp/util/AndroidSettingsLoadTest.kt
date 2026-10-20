package com.leejlredstar.redefinencm.kmp.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings that cannot be read must not stop the app for good: a damaged file starts over, and a
 * read that failed for another reason is tried again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class AndroidSettingsLoadTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun aDamagedSettingsFileStartsOverWithDefaults() = runBlocking {
        val file = folder.newFile("settings.preferences_pb")
        // A map entry that claims 127 bytes and has three.
        file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x01, 0x02, 0x03))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = SettingsCorruptionHandler,
                scope = scope,
                produceFile = { file },
            )
            val settings = PlatformSettings(store)

            settings.awaitLoaded()
            assertEquals("", settings.getString(SettingKeys.COOKIE, ""))
            assertTrue(StartupNotice.SettingsReset in StartupNotices.notices.value)

            settings.setString(SettingKeys.COOKIE, "MUSIC_U=1")
            settings.flush()
            assertEquals("MUSIC_U=1", store.data.first()[stringPreferencesKey(SettingKeys.COOKIE)])
        } finally {
            StartupNotices.consume(StartupNotice.SettingsReset)
            scope.cancel()
        }
    }

    @Test
    fun aFailedReadIsReadAgainAndNothingIsWrittenBeforeIt() = runBlocking {
        val store = FlakyStore(preferencesOf(stringPreferencesKey("server") to "http://stored/"))
        store.failing = true
        val settings = PlatformSettings(store)

        assertFailsWith<IOException> { settings.awaitLoaded() }
        settings.setString("cookie", "lost")
        assertFailsWith<IOException> { settings.flush() }
        // The write was refused rather than laid over values nobody could read.
        assertEquals("", settings.getString("cookie", ""))
        assertEquals(0, store.writes)

        store.failing = false
        settings.awaitLoaded()
        assertEquals("http://stored/", settings.getString("server", ""))
        settings.setString("cookie", "kept")
        settings.flush()
        assertEquals("kept", store.current[stringPreferencesKey("cookie")])
        assertEquals("http://stored/", store.current[stringPreferencesKey("server")])
    }

    private class FlakyStore(initial: Preferences) : DataStore<Preferences> {
        @Volatile
        var failing = false

        @Volatile
        var writes = 0

        @Volatile
        var current: Preferences = initial

        override val data: Flow<Preferences> = flow {
            if (failing) throw IOException("storage unavailable")
            emit(current)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failing) throw IOException("storage unavailable")
            writes++
            return transform(current).also { current = it }
        }
    }
}
