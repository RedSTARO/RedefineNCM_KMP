/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Native Compose translation/adaptation of Apple Music-like Lyrics and the former
 * RedefineNCM AMLL host.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

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

/**
 * The source's `opacity <n>ms ease` transition, as a Compose spec.
 *
 * Every target faded its video surface with this, spelled out in full each time.
 */
@Composable
internal fun nativeDynamicCoverFadeSpec(
    visualSpec: NativeDynamicCoverVisualSpec,
    reducedMotion: Boolean,
): AnimationSpec<Float> = remember(visualSpec, reducedMotion) {
    if (reducedMotion) {
        snap()
    } else {
        tween(
            durationMillis = visualSpec.fadeDurationMillis,
            easing = CubicBezierEasing(
                visualSpec.easingX1,
                visualSpec.easingY1,
                visualSpec.easingX2,
                visualSpec.easingY2,
            ),
        )
    }
}

/**
 * Reports [visible] to the host, and reports false once this layer goes away.
 *
 * The teardown report is the part worth sharing: without it the host keeps dimming its own
 * artwork for a video that is no longer on screen.
 */
@Composable
internal fun ReportNativeDynamicCoverVisibility(
    visible: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
) {
    val latestOnVisibilityChanged by rememberUpdatedState(onVisibilityChanged)
    LaunchedEffect(visible) {
        latestOnVisibilityChanged(visible)
    }
    DisposableEffect(Unit) {
        onDispose { latestOnVisibilityChanged(false) }
    }
}

/**
 * The frame around a platform video surface: the fade, the visibility report and the badge slot.
 *
 * Android, iOS and Desktop each held their own copy of all three. Android's copy had also
 * inlined [DynamicCoverBadge] rather than calling it, so the badge existed twice despite the
 * shared one documenting itself as the single definition.
 *
 * The browser layer does not use this. Its video is a DOM node that owns its own badge and
 * reports its own visibility, so it shares [nativeDynamicCoverFadeSpec] and
 * [ReportNativeDynamicCoverVisibility] but not the Compose surface around them.
 *
 * @param videoSurface receives the animated opacity to apply however the platform view wants it.
 */
@Composable
internal fun NativeDynamicCoverScaffold(
    modifier: Modifier,
    visualSpec: NativeDynamicCoverVisualSpec,
    hasPresentedFrame: Boolean,
    play: Boolean,
    showBadge: Boolean,
    reducedMotion: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
    videoSurface: @Composable BoxScope.(videoAlpha: Float) -> Unit,
) {
    val visible = nativeDynamicCoverIsVisible(
        hasPresentedFrame = hasPresentedFrame,
        play = play,
        showBadge = showBadge,
    )
    // `setDynamicBackgroundSuppressed(true)` in player.html pauses `#dynamic-bg` without
    // removing its `.visible` class, so a paused full-screen background keeps its last frame
    // on screen — for instance while the song-wiki dialog is open.
    val videoAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = nativeDynamicCoverFadeSpec(visualSpec, reducedMotion),
        label = "native-dynamic-cover",
    )
    ReportNativeDynamicCoverVisibility(visible, onVisibilityChanged)
    Box(modifier = modifier) {
        videoSurface(videoAlpha)
        if (showBadge && hasPresentedFrame && play) {
            DynamicCoverBadge(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

/**
 * The "动态封面" pill AMLL shows over `#wiki-cover-video`.
 *
 * Pure Compose with no platform surface of its own, so it is declared once here rather than
 * copied into each target's video layer — the Desktop and iOS copies had drifted into being
 * byte-identical, and Android and Web rendered no badge at all.
 */
@Composable
internal fun DynamicCoverBadge(modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color(0xFF005144).copy(alpha = 0.92f),
        contentColor = Color(0xFF9CF2DC),
    ) {
        Text(
            text = "动态封面",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.02.em,
                lineHeight = TextUnit.Unspecified,
            ),
        )
    }
}
