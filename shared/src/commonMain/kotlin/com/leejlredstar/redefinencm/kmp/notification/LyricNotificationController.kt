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
     * Whether the optional surface is a window of its own that can be locked in place and have
     * its lines aligned. Only the desktop's floating window is; a notification has no such
     * layout, so Settings shows the lock and alignment rows only where this is true.
     */
    val supportsOptionalSurfaceLayout: Boolean

    /**
     * A locked surface stays where it is: it cannot be dragged or resized, and where the host
     * allows it the pointer falls through to whatever is underneath. Settings is the way back.
     */
    fun setOptionalSurfaceLocked(locked: Boolean)

    /** How the surface's lyric lines sit inside it. */
    fun setOptionalSurfaceAlignment(alignment: LyricSurfaceAlignment)

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

/** Horizontal placement of the optional lyric surface's lines, persisted by its wire value. */
enum class LyricSurfaceAlignment(val wireValue: String, val displayName: String) {
    START("start", "左对齐"),
    CENTER("center", "居中"),
    END("end", "右对齐"),
    ;

    companion object {
        val DEFAULT = CENTER

        fun fromWireValueOrNull(value: String): LyricSurfaceAlignment? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }

        fun fromWireValueOrDefault(value: String): LyricSurfaceAlignment =
            fromWireValueOrNull(value) ?: DEFAULT
    }
}
