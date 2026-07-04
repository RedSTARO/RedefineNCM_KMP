package com.leejlredstar.redefinencm.kmp.util

/**
 * Platform-independent settings keys and defaults.
 * Backed by multiplatform-settings on each platform.
 */
object SettingKeys {
    const val COOKIE = "cookie"
    const val SERVER = "server"
    const val UID = "uid"
    const val ONLINE_PLAY_QUALITY = "onlinePlayQuality"
    const val DOWNLOAD_QUALITY = "downloadQuality"
    const val REPLACE_PLAYLIST = "replacePlaylist"
    const val CHECK_UPDATE = "checkUpdate"
    const val SHOW_DOWNLOAD_STATUS = "showDownloadStatus"
    const val SEARCH_PREDICTION = "searchPrediction"
    const val USE_FULL_LYRIC = "useFullLyric"
    const val ADAPT_ORIGINAL_ANDROID_LYRIC = "adaptOriginalAndroidLyric"
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
