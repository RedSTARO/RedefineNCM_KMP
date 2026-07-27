package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Source-faithful visual constants for the two `<video>` elements in AMLL's `player.html`.
 *
 * `showBadge == false` is the full-screen `#dynamic-bg` call site. It uses the platform
 * background saturation and the CSS `opacity 0.4s ease` transition. `showBadge == true`
 * is `#wiki-cover-video`; it is unfiltered and uses `opacity 220ms ease`.
 *
 * Brightness, blur, overscan/scale, and the page scrim stay in the shared background
 * composition. Applying them again inside the native texture would multiply the filters.
 */
internal data class NativeDynamicCoverVisualSpec(
    val fadeDurationMillis: Int,
    val saturation: Float,
    val easingX1: Float = 0.25f,
    val easingY1: Float = 0.10f,
    val easingX2: Float = 0.25f,
    val easingY2: Float = 1.00f,
)

internal fun nativeDynamicCoverVisualSpec(
    showBadge: Boolean,
    androidPresentation: Boolean,
): NativeDynamicCoverVisualSpec =
    if (showBadge) {
        NativeDynamicCoverVisualSpec(
            fadeDurationMillis = 220,
            saturation = 1f,
        )
    } else {
        NativeDynamicCoverVisualSpec(
            fadeDurationMillis = 400,
            saturation = if (androidPresentation) 1.15f else 1.30f,
        )
    }

/**
 * Mirrors the two source selectors: a paused `#dynamic-bg.visible` keeps its last frame,
 * while pausing `#wiki-cover-video` removes `.visible`.
 */
internal fun nativeDynamicCoverIsVisible(
    hasPresentedFrame: Boolean,
    play: Boolean,
    showBadge: Boolean,
): Boolean = hasPresentedFrame && (!showBadge || play)

/**
 * Gates decoder work without changing the source-facing [play] decision.
 *
 * A hidden/minimized host must stop the platform decoder even when audio playback is
 * allowed to continue in the background. Returning to the foreground resumes only if
 * the shared AMLL state still requests playback.
 */
internal fun nativeDynamicCoverShouldPlay(
    requestedPlay: Boolean,
    lifecycleActive: Boolean,
): Boolean = requestedPlay && lifecycleActive

/**
 * Platform video leaf used by the shared now-playing UI.
 *
 * The page layout remains in common Compose code. Each target implements only the
 * video decoder or native interop needed to feed this shared artwork slot.
 */
@Composable
internal expect fun NativeDynamicCoverLayer(
    url: String,
    modifier: Modifier = Modifier,
    play: Boolean = true,
    showBadge: Boolean = true,
    reducedMotion: Boolean = false,
    onVisibilityChanged: (Boolean) -> Unit = {},
)
