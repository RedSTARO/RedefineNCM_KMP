package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.data.provider.LibraryAggregationMode
import com.leejlredstar.redefinencm.kmp.lyric.LyricSourceMode
import com.leejlredstar.redefinencm.kmp.notification.LyricSurfaceAlignment
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Serializable snapshot of user-configurable settings used for export / import. */
@Serializable
data class SettingsBackupData(
    /** Kept only so older exported files decode; auth cookies are no longer exported/imported. */
    val cookie: String = "",
    val server: String = "",
    // The QQ backend address and view preference travel with a backup the way `server` does.
    // Its cookie deliberately does not — same rule as `cookie` above.
    /** Null keeps the current choice when importing a backup made before multi-provider support. */
    val qqEnabled: Boolean? = null,
    val qqServer: String = "",
    /** Null keeps the current choice when importing a backup made before multi-provider support. */
    val libraryAggregationMode: String? = null,
    /** The device-local account's name; null keeps the current one for an older backup. */
    val localAccountName: String? = null,
    /** Null keeps the current choice when importing a backup made before the setting existed. */
    val mergeSameSongs: Boolean? = null,
    val onlinePlayQuality: String = SoundQuality.STANDARD.name,
    val downloadQuality: String = SoundQuality.STANDARD.name,
    val replacePlaylist: Boolean = SettingKeys.REPLACE_PLAYLIST_DEFAULT,
    val checkUpdate: Boolean = false,
    val searchPrediction: Boolean = true,
    val showDownloadStatus: Boolean = SettingKeys.SHOW_DOWNLOAD_STATUS_DEFAULT,
    val adaptOriginalAndroidLyric: Boolean = false,
    /** The desktop lyric window's lock; false where there is no such window to lock. */
    val desktopLyricLocked: Boolean = false,
    /** Null keeps the current choice when importing a backup made before the window had one. */
    val desktopLyricAlignment: String? = null,
    val desktopCloseToTray: Boolean = SettingKeys.DESKTOP_CLOSE_TO_TRAY_DEFAULT,
    val showTranslatedLyric: Boolean = SettingKeys.SHOW_TRANSLATED_LYRIC_DEFAULT,
    val showRomanLyric: Boolean = false,
    /** Null keeps the current choice when importing a backup made before lyric-source support. */
    val lyricSourceMode: String? = null,
    val useDynamicCover: Boolean = false,
    /** Null keeps the current choice when importing a backup made before the theme setting. */
    val themeMode: String? = null,
    val dynamicColor: Boolean = false,
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
        qqEnabled = getBoolean(SettingKeys.QQ_ENABLED, false),
        qqServer = getString(SettingKeys.QQ_SERVER, SettingKeys.QQ_SERVER_DEFAULT),
        libraryAggregationMode = LibraryAggregationMode.fromWireValueOrDefault(
            getString(SettingKeys.LIBRARY_AGGREGATION_MODE, ""),
        ).wireValue,
        localAccountName = getString(SettingKeys.LOCAL_ACCOUNT_NAME, ""),
        mergeSameSongs = getBoolean(SettingKeys.MERGE_SAME_SONGS, SettingKeys.MERGE_SAME_SONGS_DEFAULT),
        onlinePlayQuality = getString(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.STANDARD.name),
        downloadQuality = getString(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name),
        replacePlaylist = getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT),
        checkUpdate = getBoolean(SettingKeys.CHECK_UPDATE, false),
        searchPrediction = getBoolean(SettingKeys.SEARCH_PREDICTION, true),
        showDownloadStatus = getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, SettingKeys.SHOW_DOWNLOAD_STATUS_DEFAULT),
        adaptOriginalAndroidLyric = getBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, false),
        desktopLyricLocked = getBoolean(SettingKeys.DESKTOP_LYRIC_LOCKED, false),
        desktopLyricAlignment = LyricSurfaceAlignment.fromWireValueOrDefault(
            getString(SettingKeys.DESKTOP_LYRIC_ALIGNMENT, ""),
        ).wireValue,
        desktopCloseToTray = getBoolean(
            SettingKeys.DESKTOP_CLOSE_TO_TRAY,
            SettingKeys.DESKTOP_CLOSE_TO_TRAY_DEFAULT,
        ),
        showTranslatedLyric = getBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, SettingKeys.SHOW_TRANSLATED_LYRIC_DEFAULT),
        showRomanLyric = getBoolean(SettingKeys.SHOW_ROMAN_LYRIC, false),
        lyricSourceMode = LyricSourceMode.fromStoredWireValue(
            getString(SettingKeys.LYRIC_SOURCE_MODE, LyricSourceMode.DEFAULT.wireValue),
        ).wireValue,
        useDynamicCover = getBoolean(SettingKeys.USE_DYNAMIC_COVER, false),
        themeMode = ThemeMode.fromWireValue(getString(SettingKeys.THEME_MODE, "")).wireValue,
        dynamicColor = getBoolean(SettingKeys.DYNAMIC_COLOR, false),
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
    val lyricSourceMode = data.lyricSourceMode?.let { stored ->
        LyricSourceMode.fromWireValueOrNull(stored) ?: return false
    }
    val desktopLyricAlignment = data.desktopLyricAlignment?.let { stored ->
        LyricSurfaceAlignment.fromWireValueOrNull(stored) ?: return false
    }
    if (data.server.isNotEmpty()) setString(SettingKeys.SERVER, data.server)
    data.qqEnabled?.let { setBoolean(SettingKeys.QQ_ENABLED, it) }
    if (data.qqServer.isNotEmpty()) setString(SettingKeys.QQ_SERVER, data.qqServer)
    data.libraryAggregationMode?.let { setString(SettingKeys.LIBRARY_AGGREGATION_MODE, it) }
    data.localAccountName?.let { setString(SettingKeys.LOCAL_ACCOUNT_NAME, it.trim()) }
    data.mergeSameSongs?.let { setBoolean(SettingKeys.MERGE_SAME_SONGS, it) }
    setString(SettingKeys.ONLINE_PLAY_QUALITY, data.onlinePlayQuality)
    setString(SettingKeys.DOWNLOAD_QUALITY, data.downloadQuality)
    setBoolean(SettingKeys.REPLACE_PLAYLIST, data.replacePlaylist)
    setBoolean(SettingKeys.CHECK_UPDATE, data.checkUpdate)
    setBoolean(SettingKeys.SEARCH_PREDICTION, data.searchPrediction)
    setBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, data.showDownloadStatus)
    setBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, data.adaptOriginalAndroidLyric)
    setBoolean(SettingKeys.DESKTOP_LYRIC_LOCKED, data.desktopLyricLocked)
    desktopLyricAlignment?.let { setString(SettingKeys.DESKTOP_LYRIC_ALIGNMENT, it.wireValue) }
    setBoolean(SettingKeys.DESKTOP_CLOSE_TO_TRAY, data.desktopCloseToTray)
    setBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, data.showTranslatedLyric)
    setBoolean(SettingKeys.SHOW_ROMAN_LYRIC, data.showRomanLyric)
    lyricSourceMode?.let { setString(SettingKeys.LYRIC_SOURCE_MODE, it.wireValue) }
    setBoolean(SettingKeys.USE_DYNAMIC_COVER, data.useDynamicCover)
    data.themeMode?.let { setString(SettingKeys.THEME_MODE, ThemeMode.fromWireValue(it).wireValue) }
    setBoolean(SettingKeys.DYNAMIC_COLOR, data.dynamicColor)
    true
} catch (_: Exception) {
    false
}
