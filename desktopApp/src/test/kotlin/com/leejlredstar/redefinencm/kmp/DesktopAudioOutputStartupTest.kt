package com.leejlredstar.redefinencm.kmp

import com.leejlredstar.redefinencm.kmp.player.SYSTEM_DEFAULT_AUDIO_OUTPUT_ID
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAudioOutputStartupTest {

    @Test
    fun aDevicePinnedByAnEarlierSessionDoesNotSurviveTheRestart() {
        val stored = mutableMapOf(
            SettingKeys.AUDIO_OUTPUT_DEVICE to "扬声器 (High Definition Audio Device)|Unknown Vendor",
        )

        startFromTheSystemAudioOutput(
            getString = { key, default -> stored[key] ?: default },
            setString = { key, value -> stored[key] = value },
        )

        assertEquals(
            SYSTEM_DEFAULT_AUDIO_OUTPUT_ID,
            stored[SettingKeys.AUDIO_OUTPUT_DEVICE],
        )
    }

    @Test
    fun anUnpinnedInstallIsLeftUntouched() {
        var writes = 0

        startFromTheSystemAudioOutput(
            getString = { _, default -> default },
            setString = { _, _ -> writes++ },
        )

        // Every launch runs this, so the common case must not spend a settings write — and on
        // java.util.prefs a write is a registry flush that can fail.
        assertEquals(0, writes)
    }

    @Test
    fun aStoreThatRefusesTheResetDoesNotStopStartup() {
        val stored = mutableMapOf(SettingKeys.AUDIO_OUTPUT_DEVICE to "Headset (WH-1000XM4)|Unknown Vendor")
        var attempted = false

        startFromTheSystemAudioOutput(
            getString = { key, default -> stored[key] ?: default },
            setString = { _, _ -> attempted = true; error("prefs unavailable") },
        )

        assertTrue(attempted)
        assertEquals("Headset (WH-1000XM4)|Unknown Vendor", stored[SettingKeys.AUDIO_OUTPUT_DEVICE])
    }
}
