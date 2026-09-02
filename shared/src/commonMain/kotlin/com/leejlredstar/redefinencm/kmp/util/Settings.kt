package com.leejlredstar.redefinencm.kmp.util

/**
 * Platform-independent settings keys and defaults.
 * Backed by multiplatform-settings on each platform.
 */
object SettingKeys {
    // The unprefixed credential keys are NetEase's. They are deliberately not renamed to
    // `cookieNcm` / `serverNcm`: a year of installs have them under these names, and rewriting
    // them buys nothing that adding provider-suffixed keys alongside does not.
    const val COOKIE = "cookie"
    const val SERVER = "server"

    // ── QQ Music ──
    // QQ Music has no public API, so it needs a self-hosted backend the same way NetEase does.
    const val QQ_ENABLED = "qqEnabled"
    const val QQ_SERVER = "qqServer"
    const val QQ_SERVER_DEFAULT = "http://localhost:3200"

    // Held here and sent per request, the same shape as [COOKIE], so one backend can serve several
    // clients and so the Android build can sign in at all — it has no access to the backend's own
    // config file. Like [COOKIE] it is deliberately absent from the settings backup.
    const val QQ_COOKIE = "qqCookie"

    /** Whether the library merges every provider into one view or keeps a tab per provider. */
    const val LIBRARY_AGGREGATION_MODE = "libraryAggregationMode"
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
    const val LYRIC_SOURCE_MODE = "lyricSourceMode"
    const val USE_NATIVE_AMLL_RENDERER = "useNativeAmllRenderer"
    const val USE_DYNAMIC_COVER = "useDynamicCover"
    const val PLAYER_VOLUME = "playerVolume"
    // Machine-specific, so deliberately left out of the settings backup: importing one
    // machine's speakers onto another would silently route playback at a device that is
    // not there.
    const val AUDIO_OUTPUT_DEVICE = "audioOutputDevice"
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

/**
 * A platform's persisted key-value store.
 *
 * Each target keeps its own storage format — DataStore stores typed preferences, NSUserDefaults
 * stores native booleans, `java.util.prefs` and `localStorage` store strings — so the typed
 * accessors stay in the expect surface rather than being derived from one raw string getter.
 * Rewriting them onto a common raw format would strand every existing install's saved values.
 *
 * The suspending reads are not part of that surface: on every platform they mean "wait for the
 * process snapshot, then read it", which [getStringAsync] and friends express once below.
 */
expect class PlatformSettings {
    /** Wait until the platform's persisted settings have been loaded into its process snapshot. */
    suspend fun awaitLoaded()

    /** Wait until every settings write enqueued before this call has reached persistent storage. */
    suspend fun flush()

    fun getString(key: String, default: String): String
    fun setString(key: String, value: String)

    fun getBoolean(key: String, default: Boolean): Boolean
    fun setBoolean(key: String, value: Boolean)

    fun getLong(key: String, default: Long): Long
    fun setLong(key: String, value: Long)
}

/**
 * Reads that wait for the persisted snapshot first.
 *
 * Callers that must see the durable value before continuing use these; the synchronous getters
 * are snapshot-only and would return the default before the first load completes. Every platform
 * implemented the same await-then-read three times over, once per value type.
 */
suspend fun PlatformSettings.getStringAsync(key: String, default: String): String {
    awaitLoaded()
    return getString(key, default)
}

suspend fun PlatformSettings.getBooleanAsync(key: String, default: Boolean): Boolean {
    awaitLoaded()
    return getBoolean(key, default)
}

suspend fun PlatformSettings.getLongAsync(key: String, default: Long): Long {
    awaitLoaded()
    return getLong(key, default)
}
