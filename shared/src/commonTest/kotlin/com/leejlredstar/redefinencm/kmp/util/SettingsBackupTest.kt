package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsBackupTest {

    @Test
    fun exportedBackupDoesNotContainCookieCredential() {
        val json = encodeSettingsBackup(
            getString = { key, default ->
                when (key) {
                    SettingKeys.COOKIE -> "MUSIC_U=secret-token"
                    SettingKeys.SERVER -> "http://server/"
                    else -> default
                }
            },
            getBoolean = { _, default -> default },
        )

        assertFalse(json.contains("MUSIC_U=secret-token"))
        assertFalse(json.contains("\"cookie\""))
        assertTrue(json.contains("http://server/"))
    }

    @Test
    fun importedBackupDoesNotOverwriteCookieCredential() {
        val writtenStrings = mutableMapOf<String, String>()
        val json = """
            {
              "cookie": "MUSIC_U=old-exported-token",
              "server": "http://server/",
              "onlinePlayQuality": "EXHIGH",
              "downloadQuality": "LOSSLESS"
            }
        """.trimIndent()

        val applied = applySettingsBackup(
            json = json,
            setString = { key, value -> writtenStrings[key] = value },
            setBoolean = { _, _ -> },
        )

        assertTrue(applied)
        assertFalse(SettingKeys.COOKIE in writtenStrings)
        assertTrue(writtenStrings[SettingKeys.SERVER] == "http://server/")
    }

    @Test
    fun extraLyricSurfaceSettingKeepsLegacyBackupCompatibility() {
        val json = encodeSettingsBackup(
            getString = { _, default -> default },
            getBoolean = { key, default ->
                if (key == SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE) true else default
            },
        )
        val writtenBooleans = mutableMapOf<String, Boolean>()

        assertTrue(json.contains("\"adaptOriginalAndroidLyric\":true"))
        assertTrue(
            applySettingsBackup(
                json = json,
                setString = { _, _ -> },
                setBoolean = { key, value -> writtenBooleans[key] = value },
            ),
        )
        assertTrue(writtenBooleans[SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE] == true)
    }

    @Test
    fun dynamicCoverPreferenceRoundTripsAndDefaultsToOriginalCover() {
        val defaultWrites = mutableMapOf<String, Boolean>()
        assertTrue(
            applySettingsBackup(
                json = "{}",
                setString = { _, _ -> },
                setBoolean = { key, value -> defaultWrites[key] = value },
            ),
        )
        assertFalse(defaultWrites.getValue(SettingKeys.USE_DYNAMIC_COVER))

        val exported = encodeSettingsBackup(
            getString = { _, default -> default },
            getBoolean = { key, default ->
                if (key == SettingKeys.USE_DYNAMIC_COVER) true else default
            },
        )
        assertTrue(exported.contains("\"useDynamicCover\":true"))
    }
}
