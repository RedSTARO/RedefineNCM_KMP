package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.lyric.LyricSourceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsBackupTest {
    @Test
    fun serverUserInfoIsExcludedFromSharedBackupsWithoutLosingTheEndpoint() {
        val json = encodeSettingsBackup(
            getString = { key, default -> when (key) {
                SettingKeys.SERVER -> "https://proxy-user:NCM_SECRET@backend.test/api%20one/"
                SettingKeys.QQ_SERVER -> "http://qq-user:QQ%40SECRET@[::1]:8080/qq/"
                else -> default
            } },
            getBoolean = { _, default -> default },
        )
        assertFalse("proxy-user" in json || "NCM_SECRET" in json)
        assertFalse("qq-user" in json || "QQ%40SECRET" in json)
        val data = Json.parseToJsonElement(json).jsonObject
        assertEquals("https://backend.test/api%20one/", data.getValue("server").jsonPrimitive.content)
        assertEquals("http://[::1]:8080/qq/", data.getValue("qqServer").jsonPrimitive.content)
        assertTrue(applySettingsBackup(json, { _, _ -> }, { _, _ -> }))
    }

    @Test
    fun legacyNonBaseServerUrlsAreNotExportedWithSensitiveParameters() {
        val json = encodeSettingsBackup(
            getString = { key, default -> if (key == SettingKeys.SERVER) "https://backend.test/?cookie=OLD_SECRET" else default },
            getBoolean = { _, default -> default },
        )
        assertFalse("OLD_SECRET" in json)
        assertTrue(applySettingsBackup(json, { _, _ -> }, { _, _ -> }))
    }


    @Test
    fun invalidServerAddressesRejectTheBackupBeforeAnySettingIsWritten() {
        var writes = 0
        val applied = applySettingsBackup(
            """{"server":"https://valid.example.test/","qqServer":"localhost:8080"}""",
            setString = { _, _ -> writes++ },
            setBoolean = { _, _ -> writes++ },
            setLong = { _, _ -> writes++ },
        )
        assertFalse(applied)
        assertEquals(0, writes)
    }

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
    fun anUnsetQualityIsBackedUpAsTheQualityThatPlays() {
        val json = encodeSettingsBackup(
            getString = { _, default -> default },
            getBoolean = { _, default -> default },
        )

        val document = Json.parseToJsonElement(json).jsonObject
        // Check the file itself: a round trip with the same decoder hid missing fields before.
        assertEquals("EXHIGH", document.getValue("onlinePlayQuality").jsonPrimitive.content)
        assertEquals("STANDARD", document.getValue("downloadQuality").jsonPrimitive.content)
        val restored = mutableMapOf<String, String>()
        assertTrue(applySettingsBackup(json, setString = { key, value -> restored[key] = value }, setBoolean = { _, _ -> }))

        // Restoring this on another device must not lower what plays there.
        assertEquals(SoundQuality.EXHIGH.name, restored[SettingKeys.ONLINE_PLAY_QUALITY])
        assertEquals(SoundQuality.STANDARD.name, restored[SettingKeys.DOWNLOAD_QUALITY])
    }

    @Test
    fun oldBackupsWithoutQualityDoNotChangeTheDestinationsChoice() {
        val written = mutableMapOf<String, String>()
        assertTrue(applySettingsBackup("{}", setString = { key, value -> written[key] = value }, setBoolean = { _, _ -> }))
        assertFalse(SettingKeys.ONLINE_PLAY_QUALITY in written)
        assertFalse(SettingKeys.DOWNLOAD_QUALITY in written)
    }

    @Test
    fun explicitStandardSurvivesExportAndImport() {
        val json = encodeSettingsBackup(
            getString = { key, default -> if (key == SettingKeys.ONLINE_PLAY_QUALITY) "STANDARD" else default },
            getBoolean = { _, default -> default },
        )
        assertEquals("STANDARD", Json.parseToJsonElement(json).jsonObject.getValue("onlinePlayQuality").jsonPrimitive.content)
        val written = mutableMapOf<String, String>()
        assertTrue(applySettingsBackup(json, setString = { key, value -> written[key] = value }, setBoolean = { _, _ -> }))
        assertEquals("STANDARD", written[SettingKeys.ONLINE_PLAY_QUALITY])
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
        val writtenBooleans = mutableMapOf<String, Boolean>()
        val applied = applySettingsBackup(
            json = """{"server":"http://server/"}""",
            setString = { key, value -> writtenStrings[key] = value },
            setBoolean = { key, value -> writtenBooleans[key] = value },
        )

        assertTrue(applied)
        assertFalse(SettingKeys.LIBRARY_AGGREGATION_MODE in writtenStrings)
        assertFalse(SettingKeys.QQ_SERVER in writtenStrings)
        // Such a backup never said anything about QQ, so importing it must not switch QQ off.
        assertFalse(SettingKeys.QQ_ENABLED in writtenBooleans)
    }

    @Test
    fun aBackupThatNamesTheQqSwitchRestoresIt() {
        val writtenBooleans = mutableMapOf<String, Boolean>()
        assertTrue(
            applySettingsBackup(
                json = """{"qqEnabled":false}""",
                setString = { _, _ -> },
                setBoolean = { key, value -> writtenBooleans[key] = value },
            ),
        )
        assertEquals(false, writtenBooleans[SettingKeys.QQ_ENABLED])
    }

    @Test
    fun theLocalAccountSwitchTravelsWithABackupAndAnOlderOneLeavesItAlone() {
        val json = encodeSettingsBackup(
            getString = { _, default -> default },
            getBoolean = { key, default -> if (key == SettingKeys.LOCAL_ACCOUNT_ENABLED) false else default },
        )
        assertTrue(json.contains("\"localAccountEnabled\":false"))

        val restored = mutableMapOf<String, Boolean>()
        assertTrue(
            applySettingsBackup(
                json = json,
                setString = { _, _ -> },
                setBoolean = { key, value -> restored[key] = value },
            ),
        )
        assertEquals(false, restored[SettingKeys.LOCAL_ACCOUNT_ENABLED])

        // A backup made before the switch existed says nothing about it, so importing it must not
        // switch the local account on or off.
        val older = mutableMapOf<String, Boolean>()
        assertTrue(
            applySettingsBackup(
                json = """{"localAccountName":"我的设备"}""",
                setString = { _, _ -> },
                setBoolean = { key, value -> older[key] = value },
            ),
        )
        assertFalse(SettingKeys.LOCAL_ACCOUNT_ENABLED in older)
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
        // The app has one lyric renderer and no `useNativeAmllRenderer` preference. Files
        // exported while the AMLL WebView renderer existed beside it still carry the key, so
        // importing one must neither fail nor write a setting the app does not read.
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

    @Test
    fun metadataCacheLimitTravelsWithABackupAndOldFilesKeepTheCurrentLimit() {
        val json = encodeSettingsBackup(
            getString = { _, default -> default },
            getBoolean = { _, default -> default },
            getLong = { key, default -> if (key == SettingKeys.METADATA_CACHE_LIMIT_MIB) 256L else default },
        )
        assertTrue(json.contains("\"metadataCacheLimitMiB\":256"))
        val written = mutableMapOf<String, Long>()
        assertTrue(applySettingsBackup(json, { _, _ -> }, { _, _ -> }, { k, v -> written[k] = v }))
        assertEquals(256L, written[SettingKeys.METADATA_CACHE_LIMIT_MIB])
        written.clear()
        assertTrue(applySettingsBackup("{}", { _, _ -> }, { _, _ -> }, { k, v -> written[k] = v }))
        assertFalse(SettingKeys.METADATA_CACHE_LIMIT_MIB in written)
    }

    @Test
    fun invalidCacheLimitsAreRejectedBeforeAnyWrite() {
        val writes = mutableListOf<String>()
        for (limit in listOf(-1L, 0L, 1L, Long.MAX_VALUE)) {
            assertFalse(applySettingsBackup(
                """{"metadataCacheLimitMiB":$limit}""",
                { k, _ -> writes += k }, { k, _ -> writes += k }, { k, _ -> writes += k },
            ))
        }
        assertTrue(writes.isEmpty())
    }

    @Test
    fun songTransitionsTravelWithABackup() {
        val json = encodeSettingsBackup(
            getString = { key, default -> if (key == SettingKeys.SONG_TRANSITION_MODE) "smart" else default },
            getBoolean = { _, default -> default },
            getLong = { key, default -> if (key == SettingKeys.SONG_TRANSITION_CROSSFADE_SECONDS) 9L else default },
        )
        assertTrue(json.contains("\"songTransitionMode\":\"smart\""))
        assertTrue(json.contains("\"songTransitionCrossfadeSeconds\":9"))

        val writtenStrings = mutableMapOf<String, String>()
        val writtenLongs = mutableMapOf<String, Long>()
        assertTrue(
            applySettingsBackup(
                json = json,
                setString = { key, value -> writtenStrings[key] = value },
                setBoolean = { _, _ -> },
                setLong = { key, value -> writtenLongs[key] = value },
            ),
        )
        assertEquals("smart", writtenStrings[SettingKeys.SONG_TRANSITION_MODE])
        assertEquals(9L, writtenLongs[SettingKeys.SONG_TRANSITION_CROSSFADE_SECONDS])
    }

    @Test
    fun anOlderBackupLeavesSongTransitionsAlone() {
        val writtenStrings = mutableMapOf<String, String>()
        val writtenLongs = mutableMapOf<String, Long>()
        assertTrue(
            applySettingsBackup(
                json = """{"server":"http://server/"}""",
                setString = { key, value -> writtenStrings[key] = value },
                setBoolean = { _, _ -> },
                setLong = { key, value -> writtenLongs[key] = value },
            ),
        )
        assertFalse(SettingKeys.SONG_TRANSITION_MODE in writtenStrings)
        assertFalse(SettingKeys.SONG_TRANSITION_CROSSFADE_SECONDS in writtenLongs)
        // An unknown mode is a damaged backup, not a reason to switch transitions off.
        assertFalse(
            applySettingsBackup(
                json = """{"songTransitionMode":"automix"}""",
                setString = { _, _ -> },
                setBoolean = { _, _ -> },
            ),
        )
    }
}
