package com.leejlredstar.redefinencm.kmp.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Serializable snapshot of user-configurable settings used for export / import. */
@Serializable
data class SettingsBackupData(
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
)

private val backupJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Reads current settings and returns a JSON string ready to write to a file. */
fun encodeSettingsBackup(settings: PlatformSettings): String = backupJson.encodeToString(
    SettingsBackupData(
        cookie = settings.getString(SettingKeys.COOKIE, ""),
        server = settings.getString(SettingKeys.SERVER, ""),
        onlinePlayQuality = settings.getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name),
        downloadQuality = settings.getString(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name),
        replacePlaylist = settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, false),
        checkUpdate = settings.getBoolean(SettingKeys.CHECK_UPDATE, false),
        searchPrediction = settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true),
        showDownloadStatus = settings.getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, false),
        adaptOriginalAndroidLyric = settings.getBoolean(SettingKeys.ADAPT_ORIGINAL_ANDROID_LYRIC, false),
        showTranslatedLyric = settings.getBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, false),
        showRomanLyric = settings.getBoolean(SettingKeys.SHOW_ROMAN_LYRIC, false),
    )
)

/** Parses [json] and writes values into [settings]. Returns false on any parse error. */
fun applySettingsBackup(json: String, settings: PlatformSettings): Boolean = try {
    val data = backupJson.decodeFromString<SettingsBackupData>(json)
    if (data.cookie.isNotEmpty()) settings.setString(SettingKeys.COOKIE, data.cookie)
    if (data.server.isNotEmpty()) settings.setString(SettingKeys.SERVER, data.server)
    settings.setString(SettingKeys.ONLINE_PLAY_QUALITY, data.onlinePlayQuality)
    settings.setString(SettingKeys.DOWNLOAD_QUALITY, data.downloadQuality)
    settings.setBoolean(SettingKeys.REPLACE_PLAYLIST, data.replacePlaylist)
    settings.setBoolean(SettingKeys.CHECK_UPDATE, data.checkUpdate)
    settings.setBoolean(SettingKeys.SEARCH_PREDICTION, data.searchPrediction)
    settings.setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, data.showDownloadStatus)
    settings.setBoolean(SettingKeys.ADAPT_ORIGINAL_ANDROID_LYRIC, data.adaptOriginalAndroidLyric)
    settings.setBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, data.showTranslatedLyric)
    settings.setBoolean(SettingKeys.SHOW_ROMAN_LYRIC, data.showRomanLyric)
    true
} catch (_: Exception) {
    false
}
