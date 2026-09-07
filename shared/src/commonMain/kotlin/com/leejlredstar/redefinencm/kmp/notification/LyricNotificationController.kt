package com.leejlredstar.redefinencm.kmp.notification

/**
 * Where a target shows the current lyric line outside the app's own player screen.
 *
 * - Android: a live-update notification
 * - iOS: a Live Activity (灵动岛 + Lock Screen)
 * - Desktop: a floating always-on-top window
 * - Web: a fixed element in the page
 *
 * This was one `expect object` carrying every capability any target had. Because an expect
 * declaration must be implemented in full everywhere, a feature only the desktop window could
 * offer cost three stub members on the other three, and adding the lock and alignment controls
 * took the stub count to fifteen. A capability is an interface here instead, so a target
 * declares what it can do and writes nothing for what it cannot.
 */
interface LyricSurface {
    /**
     * Show [currentLyric] for the track described by the other arguments.
     *
     * @param nextLyric the line after this one, for surfaces with room to preview it.
     * @param durationMs -1 when the length is not known yet.
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

    /** Take the lyric display down. */
    fun clearFocus()

    /** Forget everything, so the next update starts from nothing. */
    fun reset()
}

/**
 * A lyric surface the user can turn off in Settings.
 *
 * Android's extra notification and the desktop window are; iOS's Live Activity and the browser
 * surface are part of playback itself and have no setting of their own.
 */
interface OptionalLyricSurface : LyricSurface {
    /** What Settings calls this surface. Each target names its own. */
    val settingLabel: String

    fun setEnabled(enabled: Boolean)
}

/**
 * A lyric surface that is a window of its own, so it has a position and a layout.
 *
 * Only the desktop window is. A notification has neither, which is why these two are not on
 * [OptionalLyricSurface].
 */
interface WindowedLyricSurface : OptionalLyricSurface {
    /**
     * A locked window stays where it is: it cannot be dragged or resized, and where the host
     * allows it the pointer falls through to whatever is underneath. Settings is the way back.
     */
    fun setLocked(locked: Boolean)

    /** How the lyric lines sit inside the window. */
    fun setAlignment(alignment: LyricSurfaceAlignment)
}

/**
 * This target's lyric surface.
 *
 * Test it with `as?` for the capability you need rather than asking a boolean first: a target
 * that cannot lock its surface does not implement [WindowedLyricSurface] at all.
 */
expect val lyricSurface: LyricSurface

/** Horizontal placement of a windowed lyric surface's lines, persisted by its wire value. */
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
