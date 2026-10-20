package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.data.db.BrowserDatabaseStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** localStorage that is blocked or full must not stop the app or lose a setting silently. */
class WebPlatformSettingsStorageTest {
    private class FakeStorage(var blocked: Boolean = false) : BrowserDatabaseStorage {
        val values = linkedMapOf<String, String>()
        var capacity = Int.MAX_VALUE

        override fun keys(): List<String> {
            check(!blocked)
            return values.keys.toList()
        }

        override fun get(key: String): String? {
            check(!blocked)
            return values[key]
        }

        override fun set(key: String, value: String) {
            check(!blocked)
            check(values.filterKeys { it != key }.values.sumOf { it.length } + value.length <= capacity)
            values[key] = value
        }

        override fun remove(key: String) {
            check(!blocked)
            values.remove(key)
        }
    }

    @Test
    fun blockedStorageKeepsSettingsForTheSession() = runTest {
        val settings = PlatformSettings(keyPrefix = "", storage = FakeStorage(blocked = true))

        assertEquals("default", settings.getString("cookie", "default"))
        settings.setString("cookie", "MUSIC_U=1")
        settings.setBoolean("flag", true)

        assertEquals("MUSIC_U=1", settings.getString("cookie", ""))
        assertEquals(true, settings.getBoolean("flag", false))
        // Nothing can be stored beyond this page, so there is no failed write to report.
        settings.flush()
    }

    @Test
    fun aFullStoreDropsCacheRowsToKeepASetting() = runTest {
        val storage = FakeStorage()
        storage.values["redefinencm.db.CachedLyric:1"] = "x".repeat(100)
        storage.values["redefinencm.db.LocalLibrary:0"] = "library"
        storage.capacity = storage.values.values.sumOf { it.length } + 5
        val settings = PlatformSettings(keyPrefix = "", storage = storage)

        settings.setString("cookie", "MUSIC_U=" + "y".repeat(50))
        settings.flush()

        assertEquals("MUSIC_U=" + "y".repeat(50), storage.values["cookie"])
        assertNull(storage.values["redefinencm.db.CachedLyric:1"])
        assertEquals("library", storage.values["redefinencm.db.LocalLibrary:0"])
    }

    @Test
    fun aWriteThatStillDoesNotFitKeepsTheOldValueAndFlushReportsItOnce() = runTest {
        val storage = FakeStorage()
        storage.values["cookie"] = "old"
        storage.capacity = 10
        val settings = PlatformSettings(keyPrefix = "", storage = storage)

        settings.setString("cookie", "z".repeat(50))

        assertEquals("old", settings.getString("cookie", ""))
        assertFailsWith<IllegalStateException> { settings.flush() }
        settings.flush()
    }
}
