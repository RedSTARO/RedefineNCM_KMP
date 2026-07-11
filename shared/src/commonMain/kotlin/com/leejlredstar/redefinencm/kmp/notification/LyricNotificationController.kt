package com.leejlredstar.redefinencm.kmp.notification

/**
 * Platform-specific lyric notification / live activity controller.
 *
 * - Android: Shows a live-update notification with current lyric
 * - iOS: Updates Live Activities (灵动岛 + Lock Screen)
 * - Desktop: Updates floating window / desktop lyrics
 * - Web: No-op (stub)
 */
expect object LyricNotificationController {
    /** Whether this target exposes an optional, extra lyric surface in Settings. */
    val supportsOptionalSurfaceControl: Boolean

    /** Platform-specific Settings label for the optional lyric surface. */
    val optionalSurfaceSettingLabel: String

    /**
     * Enable or disable the optional lyric surface.
     *
     * Android maps this to the extra Live Update notification and Desktop maps it to the
     * floating desktop-lyrics window. iOS Live Activity and Web lyrics are intentionally not
     * controlled by this setting.
     */
    fun setOptionalSurfaceEnabled(enabled: Boolean)

    /**
     * Update the displayed lyric content.
     * @param title Song title
     * @param artist Song artist
     * @param currentLyric Current lyric line
     * @param nextLyric Next lyric line (for preview)
     * @param artworkUri Album art URI
     */
    fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String? = null,
        isPlaying: Boolean = true,
        positionMs: Long = 0L,
        durationMs: Long = -1L,
    )

    /** Remove the lyric display. */
    fun clearFocus()

    /** Reset internal state. */
    fun reset()
}
