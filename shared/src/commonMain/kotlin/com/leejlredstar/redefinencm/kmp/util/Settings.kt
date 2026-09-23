package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings

/**
 * Platform-independent settings keys and defaults.
 * Each platform stores them through its [PlatformSettings] actual.
 */
object SettingKeys {
    // The unprefixed credential keys are NetEase's. They are deliberately not renamed to
    // `cookieNcm` / `serverNcm`: a year of installs have them under these names, and rewriting
    // them buys nothing that adding provider-suffixed keys alongside does not.
    const val COOKIE = "cookie"
    const val SERVER = "server"

    // ── QQ Music ──
    // QQ Music has no public API, so it needs a self-hosted backend the same way NetEase does:
    // the `L-1124/QQMusicApi` web gateway, which listens on 8080 by default.
    const val QQ_ENABLED = "qqEnabled"
    const val QQ_SERVER = "qqServer"
    const val QQ_SERVER_DEFAULT = "http://localhost:8080"

    // Held here and sent per request, the same shape as [COOKIE], so one backend can serve several
    // clients and so the Android build can sign in at all: it has no access to the backend's own
    // config file. The value is the gateway's cookie form of its credential (`musicid=…;
    // musickey=…; …`, see QQCredential), whichever login method produced it. Like [COOKIE] it is
    // deliberately absent from the settings backup.
    const val QQ_COOKIE = "qqCookie"

    /** Whether the library merges every provider into one view or keeps a tab per provider. */
    const val LIBRARY_AGGREGATION_MODE = "libraryAggregationMode"

    /** Whether the merged view folds the same song from several providers into one row. */
    const val MERGE_SAME_SONGS = "mergeSameSongs"
    const val MERGE_SAME_SONGS_DEFAULT = true

    /** The device-local account's display name; blank means the default. */
    const val LOCAL_ACCOUNT_NAME = "localAccountName"
    const val UID = "uid"
    const val UID_COOKIE_FINGERPRINT = "uidCookieFingerprint"
    const val ONLINE_PLAY_QUALITY = "onlinePlayQuality"
    const val DOWNLOAD_QUALITY = "downloadQuality"
    const val REPLACE_PLAYLIST = "replacePlaylist"
    // Every read of these three uses the constant, so a screen cannot disagree with its switch.
    // Tapping a song plays the list from it; with this off, the song alone replaces the queue.
    const val REPLACE_PLAYLIST_DEFAULT = true
    const val CHECK_UPDATE = "checkUpdate"
    const val SHOW_DOWNLOAD_STATUS = "showDownloadStatus"
    const val SHOW_DOWNLOAD_STATUS_DEFAULT = true
    const val SEARCH_PREDICTION = "searchPrediction"
    /** Light, dark or the system's; see ThemeMode. */
    const val THEME_MODE = "themeMode"
    /** The app's language, or "system" to follow the system's; see LanguageSetting. */
    const val APP_LANGUAGE = "appLanguage"
    /** Android 12+ wallpaper colours instead of the app's own scheme. */
    const val DYNAMIC_COLOR = "dynamicColor"
    /** Recent searches, newest first, one per line. Kept on the device, not in the backup. */
    const val SEARCH_HISTORY = "searchHistory"
    // Keep the legacy persisted key so existing preferences and exported backups remain valid.
    const val ENABLE_EXTRA_LYRIC_SURFACE = "adaptOriginalAndroidLyric"
    // The desktop lyric window's own layout: whether it ignores the pointer, and how its two
    // lines sit inside it. Only the desktop reads them; they are backed up with the rest.
    const val DESKTOP_LYRIC_LOCKED = "desktopLyricLocked"
    const val DESKTOP_LYRIC_ALIGNMENT = "desktopLyricAlignment"
    /** Text size of the desktop lyric window, as a multiple of its default. */
    const val DESKTOP_LYRIC_TEXT_SCALE = "desktopLyricTextScale"
    /** Closing the desktop main window leaves the app in the tray instead of quitting it. */
    const val DESKTOP_CLOSE_TO_TRAY = "desktopCloseToTray"
    const val DESKTOP_CLOSE_TO_TRAY_DEFAULT = true
    // Where the desktop windows were left, so a launch opens them there again. They describe one
    // machine's screens, so like AUDIO_OUTPUT_DEVICE they are not part of the settings backup.
    const val DESKTOP_WINDOW_BOUNDS = "desktopWindowBounds"
    const val DESKTOP_LYRIC_WINDOW_BOUNDS = "desktopLyricWindowBounds"
    const val SHOW_TRANSLATED_LYRIC = "showTranslatedLyric"
    const val SHOW_TRANSLATED_LYRIC_DEFAULT = true
    const val SHOW_ROMAN_LYRIC = "showRomanLyric"
    const val LYRIC_SOURCE_MODE = "lyricSourceMode"
    const val USE_DYNAMIC_COVER = "useDynamicCover"
    const val PLAYER_VOLUME = "playerVolume"
    /** Off, crossfade or smart; see SongTransitionMode. Backed up with the rest. */
    const val SONG_TRANSITION_MODE = "songTransitionMode"
    /** The fixed crossfade's length in whole seconds, 1..12. */
    const val SONG_TRANSITION_CROSSFADE_SECONDS = "songTransitionCrossfadeSeconds"
    // A per-session override, not a preference: desktop startup resets it so playback follows
    // the current system output device. Left out of the settings backup for the same reason it
    // is not carried across restarts: it names one machine's hardware at one moment in time.
    const val AUDIO_OUTPUT_DEVICE = "audioOutputDevice"
}

enum class SoundQuality {
    STANDARD,
    HIGHER,
    EXHIGH,
    LOSSLESS,
    HIRES,
    JYEFFECT,
    SKY,
    DOLBY,
    JYMASTER;

    val displayName: String
        get() = when (this) {
            STANDARD -> strings.qualityStandard
            HIGHER -> strings.qualityHigh
            EXHIGH -> strings.qualityVeryHigh
            LOSSLESS -> strings.lossless
            HIRES -> "Hi-Res"
            JYEFFECT -> strings.qualityHdSurround
            SKY -> strings.qualityImmersiveSurround
            DOLBY -> strings.qualityDolbyAtmos
            JYMASTER -> strings.qualityMaster
        }

    override fun toString(): String = displayName
}

/**
 * A platform's persisted key-value store.
 *
 * Each target keeps its own storage format (DataStore stores typed preferences, NSUserDefaults
 * stores native booleans, `java.util.prefs` and `localStorage` store strings), so the typed
 * accessors stay in the expect surface instead of being derived from one raw string getter.
 * Moving them onto a common raw format would strand every existing install's saved values.
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
 * are snapshot-only and would return the default before the first load completes. The
 * await-then-read is written once here for every platform instead of once per value type in
 * each actual.
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
