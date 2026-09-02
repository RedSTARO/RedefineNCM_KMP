package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What every [PlatformSettings] actual must answer the same way.
 *
 * The storage formats deliberately differ and cannot be unified — DataStore keeps typed
 * preferences, NSUserDefaults keeps a native boolean, `java.util.prefs` and `localStorage` keep
 * strings, and rewriting any of them would strand values existing installs already hold. What
 * can be unified is the behaviour callers are entitled to assume, and that had drifted: the Web
 * store read every unrecognised value as `false` where the others fell back to the caller's
 * default.
 *
 * Subclasses supply an isolated store so a test run never touches the developer's real
 * preferences. Android is absent: its DataStore delegate is bound to a fixed file name and its
 * host tests have no Context to hand, so isolating it needs more than a constructor parameter.
 */
abstract class PlatformSettingsContract {

    /** A fresh, empty, throwaway store. Called once per test. */
    protected abstract fun newSettings(): PlatformSettings

    @Test
    fun readsBackEveryStringItWrote() {
        val settings = newSettings()
        settings.setString("string", "hello")
        assertEquals("hello", settings.getString("string", "fallback"))
    }

    @Test
    fun readsBackAnEmptyStringRatherThanTheDefault() {
        // An empty cookie means "signed out", which is not the same as "never signed in".
        val settings = newSettings()
        settings.setString("empty", "")
        assertEquals("", settings.getString("empty", "fallback"))
    }

    @Test
    fun readsBackStringsCarryingSeparatorsAndNonAscii() {
        val settings = newSettings()
        val value = "a=1; b=2\nc\t中文=值"
        settings.setString("awkward", value)
        assertEquals(value, settings.getString("awkward", ""))
    }

    @Test
    fun readsBackBothBooleans() {
        val settings = newSettings()
        settings.setBoolean("boolTrue", true)
        settings.setBoolean("boolFalse", false)
        assertTrue(settings.getBoolean("boolTrue", false))
        assertFalse(settings.getBoolean("boolFalse", true))
    }

    @Test
    fun readsBackLongsAcrossTheRangeSettingsUse() {
        val settings = newSettings()
        // Volume percent, a UID, and the extremes a persisted counter could reach.
        for (value in listOf(0L, 100L, 1_900_000_000L, Long.MAX_VALUE, Long.MIN_VALUE, -1L)) {
            settings.setLong("long", value)
            assertEquals(value, settings.getLong("long", 42L))
        }
    }

    @Test
    fun returnsTheDefaultForAKeyThatWasNeverWritten() {
        val settings = newSettings()
        assertEquals("fallback", settings.getString("absent", "fallback"))
        assertTrue(settings.getBoolean("absentTrue", true))
        assertFalse(settings.getBoolean("absentFalse", false))
        assertEquals(7L, settings.getLong("absentLong", 7L))
    }

    @Test
    fun lastWriteWins() {
        val settings = newSettings()
        settings.setLong("overwrite", 1L)
        settings.setLong("overwrite", 2L)
        assertEquals(2L, settings.getLong("overwrite", 0L))

        settings.setBoolean("flag", true)
        settings.setBoolean("flag", false)
        assertFalse(settings.getBoolean("flag", true))
    }

    @Test
    fun aValueStoredUnderOneKeyDoesNotAnswerAnother() {
        val settings = newSettings()
        settings.setString("typed", "not-a-number")
        assertEquals(9L, settings.getLong("other", 9L))
    }

    @Test
    fun suspendingReadSeesAWriteThatPrecededIt() = runTest {
        val settings = newSettings()
        settings.setString("async", "written")
        settings.flush()
        assertEquals("written", settings.getStringAsync("async", "fallback"))
        assertEquals("fallback", settings.getStringAsync("asyncAbsent", "fallback"))
    }

    @Test
    fun suspendingReadsAgreeWithSynchronousOnesForEveryType() = runTest {
        val settings = newSettings()
        settings.setString("s", "v")
        settings.setBoolean("b", true)
        settings.setLong("l", 5L)
        settings.flush()
        assertEquals(settings.getString("s", ""), settings.getStringAsync("s", ""))
        assertEquals(settings.getBoolean("b", false), settings.getBooleanAsync("b", false))
        assertEquals(settings.getLong("l", 0L), settings.getLongAsync("l", 0L))
    }
}
