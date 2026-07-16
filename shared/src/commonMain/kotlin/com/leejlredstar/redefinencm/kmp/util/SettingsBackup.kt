package com.leejlredstar.redefinencm.kmp.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Serializable snapshot of user-configurable settings used for export / import. */
@Serializable
data class SettingsBackupData(
    /** Kept only so older exported files decode; auth cookies are no longer exported/imported. */
    val cookie: String = "",
    val server: String = "",
    val onlinePlayQuality: String = SoundQuality.STANDARD.name,
    val downloadQuality: String = SoundQuality.STANDARD.name,
    val replacePlaylist: Boolean = false,
    val checkUpdate: Boolean = false,
    val searchPrediction: Boolean = true,
    val showDownloadStatus: Boolean = false,
    val adaptOriginalAndroidLyric: Boolean = false,
    val showTranslatedLyric: Boolean = false,
    val showRomanLyric: Boolean = false,
    val useDynamicCover: Boolean = false,
)

private val backupJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Reads current settings and returns a JSON string ready to write to a file. */
fun encodeSettingsBackup(settings: PlatformSettings): String = encodeSettingsBackup(
    getString = settings::getString,
    getBoolean = settings::getBoolean,
)

internal fun encodeSettingsBackup(
    getString: (key: String, default: String) -> String,
    getBoolean: (key: String, default: Boolean) -> Boolean,
): String = backupJson.encodeToString(
    SettingsBackupData(
        cookie = "",
        server = getString(SettingKeys.SERVER, ""),
        onlinePlayQuality = getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name),
        downloadQuality = getString(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name),
        replacePlaylist = getBoolean(SettingKeys.REPLACE_PLAYLIST, false),
        checkUpdate = getBoolean(SettingKeys.CHECK_UPDATE, false),
        searchPrediction = getBoolean(SettingKeys.SEARCH_PREDICTION, true),
        showDownloadStatus = getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, false),
        adaptOriginalAndroidLyric = getBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, false),
        showTranslatedLyric = getBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, false),
        showRomanLyric = getBoolean(SettingKeys.SHOW_ROMAN_LYRIC, false),
        useDynamicCover = getBoolean(SettingKeys.USE_DYNAMIC_COVER, false),
    )
)

/** Parses [json] and writes values into [settings]. Returns false on any parse error. */
fun applySettingsBackup(json: String, settings: PlatformSettings): Boolean =
    applySettingsBackup(
        json = json,
        setString = settings::setString,
        setBoolean = settings::setBoolean,
    )

internal fun applySettingsBackup(
    json: String,
    setString: (key: String, value: String) -> Unit,
    setBoolean: (key: String, value: Boolean) -> Unit,
): Boolean = try {
    val data = backupJson.decodeFromString<SettingsBackupData>(json)
    if (data.server.isNotEmpty()) setString(SettingKeys.SERVER, data.server)
    setString(SettingKeys.ONLINE_PLAY_QUALITY, data.onlinePlayQuality)
    setString(SettingKeys.DOWNLOAD_QUALITY, data.downloadQuality)
    setBoolean(SettingKeys.REPLACE_PLAYLIST, data.replacePlaylist)
    setBoolean(SettingKeys.CHECK_UPDATE, data.checkUpdate)
    setBoolean(SettingKeys.SEARCH_PREDICTION, data.searchPrediction)
    setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, data.showDownloadStatus)
    setBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, data.adaptOriginalAndroidLyric)
    setBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, data.showTranslatedLyric)
    setBoolean(SettingKeys.SHOW_ROMAN_LYRIC, data.showRomanLyric)
    setBoolean(SettingKeys.USE_DYNAMIC_COVER, data.useDynamicCover)
    true
} catch (_: Exception) {
    false
}
