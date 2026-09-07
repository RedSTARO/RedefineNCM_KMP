package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette

/**
 * The position a transport surface shows while its seek control is being dragged.
 *
 * A seek control cannot simply display the player's position: between the finger moving and the
 * backend reporting the new position the bar would snap back. Every surface therefore holds a
 * pending fraction, shows that instead while a drag is live, and commits on release.
 *
 * That protocol had been written four times — inside [PlaybackSeekBar] and again at each of its
 * call sites, plus a fourth copy on the now-playing screen — and the copies disagreed. Two of
 * them multiplied the fraction by the duration without clamping, so a drag to the very end could
 * ask the backend to seek past the end of the track. Clamping lives in [seekPositionOf] now.
 */
@Stable
internal class SeekDragState {
    /** Whether a drag is in progress, so the surface shows the pending position. */
    var isDragging by mutableStateOf(false)
        private set

    private var dragProgress by mutableFloatStateOf(0f)

    /** Records a new pending fraction, starting the drag if it is not already live. */
    fun preview(fraction: Float) {
        isDragging = true
        dragProgress = fraction.coerceIn(0f, 1f)
    }

    /**
     * Ends the drag.
     *
     * @return the position to seek to, or null when the track has no known duration and there is
     *   nothing to seek to. The pending fraction is not cleared: nothing reads it once the drag
     *   is over, and keeping it avoids a frame of the old position before the backend catches up.
     */
    fun commit(totalDuration: Long): Long? {
        isDragging = false
        if (totalDuration <= 0L) return null
        return seekPositionOf(dragProgress, totalDuration)
    }

    /** Abandons the drag without seeking. The surface falls back to the player's own position. */
    fun cancel() {
        isDragging = false
    }

    /** The fraction to draw: the pending one while dragging, the player's otherwise. */
    fun progressFor(progress: Float): Float =
        (if (isDragging) dragProgress else progress).coerceIn(0f, 1f)

    /**
     * The position to label: the pending one while dragging, the player's otherwise.
     *
     * A reported position never draws past the end of the track. Backends overshoot by a frame
     * around a track change, and one surface already clamped for it while the others did not.
     */
    fun positionFor(position: Long, totalDuration: Long): Long = when {
        isDragging -> seekPositionOf(dragProgress, totalDuration)
        totalDuration > 0L -> position.coerceIn(0L, totalDuration)
        else -> position.coerceAtLeast(0L)
    }
}

/**
 * A seek state that resets when [resetKey] changes.
 *
 * Pass the current media id: a drag left live across a track change would otherwise commit the
 * old fraction against the new track's duration.
 */
@Composable
internal fun rememberSeekDragState(resetKey: Any?): SeekDragState =
    remember(resetKey) { SeekDragState() }

/** The position [fraction] of the way through [totalDuration], never past either end. */
internal fun seekPositionOf(fraction: Float, totalDuration: Long): Long {
    if (totalDuration <= 0L) return 0L
    return (fraction.coerceIn(0f, 1f) * totalDuration)
        .toLong()
        .coerceIn(0L, totalDuration)
}

/**
 * The compact seek slider shared by the desktop strip and the control island.
 *
 * The now-playing screen draws its own slider because its track is the expressive wavy indicator
 * rather than a plain one, but it drives the same [SeekDragState] through the same calls.
 *
 * @param onInteractionStart runs once when a drag begins, for surfaces that have to bring
 *   themselves back into view first.
 */
@Composable
internal fun PlaybackSeekBar(
    state: SeekDragState,
    progress: Float,
    totalDuration: Long,
    enabled: Boolean,
    accentPalette: ContentAccentPalette,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onInteractionStart: (() -> Unit)? = null,
) {
    // A surface torn down mid-drag must not leave the state believing a drag is still live.
    DisposableEffect(state) {
        onDispose { if (state.isDragging) state.cancel() }
    }

    Slider(
        value = state.progressFor(progress),
        onValueChange = { updated ->
            if (!state.isDragging) onInteractionStart?.invoke()
            state.preview(updated)
        },
        onValueChangeFinished = {
            state.commit(totalDuration)?.let(onSeek)
        },
        enabled = enabled,
        valueRange = 0f..1f,
        modifier = Modifier
            .heightIn(min = ExpressiveLayout.MinimumTouchTarget)
            .then(modifier),
        colors = SliderDefaults.colors(
            thumbColor = accentPalette.onContainer,
            activeTrackColor = accentPalette.onContainer,
            inactiveTrackColor = accentPalette.onContainer.copy(alpha = 0.22f),
            disabledThumbColor = accentPalette.onContainer.copy(alpha = 0.38f),
            disabledActiveTrackColor = accentPalette.onContainer.copy(alpha = 0.28f),
            disabledInactiveTrackColor = accentPalette.onContainer.copy(alpha = 0.12f),
        ),
    )
}
