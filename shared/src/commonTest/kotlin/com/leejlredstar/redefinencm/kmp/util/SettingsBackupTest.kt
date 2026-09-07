package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.lyric.LyricSourceMode
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun exportedBackupCarriesTheQqBackendButNotItsCookie() {
        val json = encodeSettingsBackup(
            getString = { key, default ->
                when (key) {
                    SettingKeys.QQ_COOKIE -> "qm_keyst=qq-secret-token"
                    SettingKeys.QQ_SERVER -> "http://[::1]:3200"
                    else -> default
                }
            },
            getBoolean = { key, default ->
                if (key == SettingKeys.QQ_ENABLED) true else default
            },
        )

        // A second provider must not reopen the hole the NetEase cookie exclusion closes.
        assertFalse(json.contains("qm_keyst=qq-secret-token"))
        assertFalse(json.contains("qqCookie"))
        // The backend address is not a credential and travels the way `server` does.
        assertTrue(json.contains("http://[::1]:3200"))
        assertTrue(json.contains("\"qqEnabled\":true"))
    }

    @Test
    fun importedBackupDoesNotOverwriteTheQqCookie() {
        val writtenStrings = mutableMapOf<String, String>()
        val json = """
            {
              "server": "http://server/",
              "qqCookie": "qm_keyst=old-exported-token",
              "qqServer": "http://[::1]:3200",
              "libraryAggregationMode": "perProvider"
            }
        """.trimIndent()

        val applied = applySettingsBackup(
            json = json,
            setString = { key, value -> writtenStrings[key] = value },
            setBoolean = { _, _ -> },
        )

        assertTrue(applied)
        assertFalse(SettingKeys.QQ_COOKIE in writtenStrings)
        assertTrue(writtenStrings[SettingKeys.QQ_SERVER] == "http://[::1]:3200")
        assertTrue(writtenStrings[SettingKeys.LIBRARY_AGGREGATION_MODE] == "perProvider")
    }

    @Test
    fun aBackupMadeBeforeMultiProviderSupportKeepsTheCurrentChoice() {
        val writtenStrings = mutableMapOf<String, String>()
        val applied = applySettingsBackup(
            json = """{"server":"http://server/"}""",
            setString = { key, value -> writtenStrings[key] = value },
            setBoolean = { _, _ -> },
        )

        assertTrue(applied)
        assertFalse(SettingKeys.LIBRARY_AGGREGATION_MODE in writtenStrings)
        assertFalse(SettingKeys.QQ_SERVER in writtenStrings)
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
    fun desktopLyricWindowLayoutRoundTripsAndOlderBackupsKeepTheCurrentAlignment() {
        val legacyStrings = mutableMapOf<String, String>()
        val legacyBooleans = mutableMapOf<String, Boolean>()
        assertTrue(
            applySettingsBackup(
                json = "{}",
                setString = { key, value -> legacyStrings[key] = value },
                setBoolean = { key, value -> legacyBooleans[key] = value },
            ),
        )
        assertFalse(legacyBooleans.getValue(SettingKeys.DESKTOP_LYRIC_LOCKED))
        assertFalse(legacyStrings.containsKey(SettingKeys.DESKTOP_LYRIC_ALIGNMENT))

        val exported = encodeSettingsBackup(
            getString = { key, default ->
                if (key == SettingKeys.DESKTOP_LYRIC_ALIGNMENT) "end" else default
            },
            getBoolean = { key, default ->
                if (key == SettingKeys.DESKTOP_LYRIC_LOCKED) true else default
            },
        )
        assertTrue(exported.contains("\"desktopLyricLocked\":true"))
        assertTrue(exported.contains("\"desktopLyricAlignment\":\"end\""))

        val strings = mutableMapOf<String, String>()
        val booleans = mutableMapOf<String, Boolean>()
        assertTrue(
            applySettingsBackup(
                json = exported,
                setString = { key, value -> strings[key] = value },
                setBoolean = { key, value -> booleans[key] = value },
            ),
        )
        assertTrue(booleans.getValue(SettingKeys.DESKTOP_LYRIC_LOCKED))
        assertEquals("end", strings[SettingKeys.DESKTOP_LYRIC_ALIGNMENT])

        assertFalse(
            applySettingsBackup(
                json = "{\"desktopLyricAlignment\":\"diagonal\"}",
                setString = { _, _ -> },
                setBoolean = { _, _ -> },
            ),
        )
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

    @Test
    fun lyricSourceModeRoundTripsWithoutChangingLegacyImports() {
        val exported = encodeSettingsBackup(
            getString = { key, default ->
                if (key == SettingKeys.LYRIC_SOURCE_MODE) {
                    LyricSourceMode.BACKEND_ONLY.wireValue
                } else {
                    default
                }
            },
            getBoolean = { _, default -> default },
        )
        val writtenStrings = mutableMapOf<String, String>()
        assertTrue(
            applySettingsBackup(
                json = exported,
                setString = { key, value -> writtenStrings[key] = value },
                setBoolean = { _, _ -> },
            ),
        )
        assertTrue(
            writtenStrings[SettingKeys.LYRIC_SOURCE_MODE] ==
                LyricSourceMode.BACKEND_ONLY.wireValue,
        )

        writtenStrings.clear()
        assertTrue(
            applySettingsBackup(
                json = "{}",
                setString = { key, value -> writtenStrings[key] = value },
                setBoolean = { _, _ -> },
            ),
        )
        assertFalse(SettingKeys.LYRIC_SOURCE_MODE in writtenStrings)
    }

    @Test
    fun backupsFromTheSelectableRendererEraStillImport() {
        // The AMLL WebView renderer and its `useNativeAmllRenderer` preference are gone. Files
        // exported while both renderers existed still carry the key, so importing one must
        // neither fail nor write a setting the app no longer reads.
        val writtenBooleans = mutableMapOf<String, Boolean>()

        assertTrue(
            applySettingsBackup(
                json = """{"server":"http://server/","useNativeAmllRenderer":true}""",
                setString = { _, _ -> },
                setBoolean = { key, value -> writtenBooleans[key] = value },
            ),
        )
        assertFalse("useNativeAmllRenderer" in writtenBooleans)
    }

    @Test
    fun exportsNoLongerCarryARendererPreference() {
        val exported = encodeSettingsBackup(
            getString = { _, default -> default },
            getBoolean = { _, _ -> true },
        )

        assertFalse(exported.contains("useNativeAmllRenderer"))
    }

    @Test
    fun invalidLyricSourceModeIsRejectedBeforeWriting() {
        val writes = mutableListOf<String>()
        val applied = applySettingsBackup(
            json = """{"server":"http://server/","lyricSourceMode":"unknown"}""",
            setString = { key, _ -> writes += key },
            setBoolean = { key, _ -> writes += key },
        )

        assertFalse(applied)
        assertTrue(writes.isEmpty())
    }
}
