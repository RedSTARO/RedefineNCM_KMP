package com.leejlredstar.redefinencm.kmp.util

/**
 * Platform-independent settings keys and defaults.
 * Backed by multiplatform-settings on each platform.
 */
object SettingKeys {
    const val COOKIE = "cookie"
    const val SERVER = "server"
    const val UID = "uid"
    const val UID_COOKIE_FINGERPRINT = "uidCookieFingerprint"
    const val ONLINE_PLAY_QUALITY = "onlinePlayQuality"
    const val DOWNLOAD_QUALITY = "downloadQuality"
    const val REPLACE_PLAYLIST = "replacePlaylist"
    const val CHECK_UPDATE = "checkUpdate"
    const val SHOW_DOWNLOAD_STATUS = "showDownloadStatus"
    const val SEARCH_PREDICTION = "searchPrediction"
    // Keep the legacy persisted key so existing preferences and exported backups remain valid.
    const val ENABLE_EXTRA_LYRIC_SURFACE = "adaptOriginalAndroidLyric"
    const val SHOW_TRANSLATED_LYRIC = "showTranslatedLyric"
    const val SHOW_ROMAN_LYRIC = "showRomanLyric"
    const val PLAYER_VOLUME = "playerVolume"
}

enum class SoundQuality(val displayName: String) {
    STANDARD("标准"),
    HIGHER("较高"),
    EXHIGH("极高"),
    LOSSLESS("无损"),
    HIRES("Hi-Res"),
    JYEFFECT("高清环绕声"),
    SKY("沉浸环绕声"),
    DOLBY("杜比全景声"),
    JYMASTER("超清母带");

    override fun toString(): String = displayName
}

expect class PlatformSettings {
    /** Wait until the platform's persisted settings have been loaded into its process snapshot. */
    suspend fun awaitLoaded()

    /** Wait until every settings write enqueued before this call has reached persistent storage. */
    suspend fun flush()

    fun getString(key: String, default: String): String
    suspend fun getStringAsync(key: String, default: String): String
    fun setString(key: String, value: String)

    fun getBoolean(key: String, default: Boolean): Boolean
    suspend fun getBooleanAsync(key: String, default: Boolean): Boolean
    fun setBoolean(key: String, value: Boolean)

    fun getLong(key: String, default: Long): Long
    suspend fun getLongAsync(key: String, default: Long): Long
    fun setLong(key: String, value: Long)
}
