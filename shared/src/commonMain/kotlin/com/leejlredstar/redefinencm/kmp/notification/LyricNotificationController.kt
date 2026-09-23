package com.leejlredstar.redefinencm.kmp.notification

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.flow.StateFlow

/**
 * Where a target shows the current lyric line outside the app's own player screen.
 *
 * - Android: a live-update notification
 * - iOS: a Live Activity (灵动岛 + Lock Screen)
 * - Desktop: a floating always-on-top window
 * - Web: a fixed element in the page
 *
 * Each capability is an interface, so a target declares what it can do and writes nothing for
 * what it cannot. Do not fold them into one `expect object` carrying every capability any target
 * has: an expect declaration must be implemented in full everywhere, so a feature only the
 * desktop window offers would cost a stub member on each of the other three targets, and with the
 * lock and alignment controls the stub count would reach fifteen.
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
     * Whether the window is turned on, and whether it is locked, as they are now. Its own
     * toolbar and the tray menu change both, so Settings follows these rather than a copy.
     */
    val isEnabled: StateFlow<Boolean>
    val isWindowLocked: StateFlow<Boolean>

    /**
     * A locked window stays where it is: it cannot be dragged or resized, and where the host
     * allows it the pointer falls through to whatever is underneath. Settings and the tray menu
     * are the way back.
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
enum class LyricSurfaceAlignment(val wireValue: String) {
    START("start"),
    CENTER("center"),
    END("end"),
    ;

    val displayName: String
        get() = when (this) {
            START -> strings.alignLeft
            CENTER -> strings.alignCenter
            END -> strings.alignRight
        }

    companion object {
        val DEFAULT = CENTER

        fun fromWireValueOrNull(value: String): LyricSurfaceAlignment? =
            entries.firstOrNull { it.wireValue == value.trim().lowercase() }

        fun fromWireValueOrDefault(value: String): LyricSurfaceAlignment =
            fromWireValueOrNull(value) ?: DEFAULT
    }
}
