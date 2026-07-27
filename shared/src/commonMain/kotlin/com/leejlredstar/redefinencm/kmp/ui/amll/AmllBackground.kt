/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Native Compose translation/adaptation of Apple Music-like Lyrics and the former
 * RedefineNCM AMLL host.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.ui.component.NativeDynamicCoverLayer

private const val BackgroundCrossfadeMillis = 400
private val BackgroundCssEase = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.00f)

internal data class AmllBackgroundVisualSpec(
    val blurRadiusDp: Float,
    val combinedScale: Float,
    val saturation: Float,
    val brightness: Float,
    val scrimStartAlpha: Float = 0.62f,
    val scrimEndAlpha: Float = 0.56f,
)

internal fun amllBackgroundVisualSpec(
    androidPresentation: Boolean,
): AmllBackgroundVisualSpec =
    if (androidPresentation) {
        AmllBackgroundVisualSpec(
            blurRadiusDp = 24f,
            combinedScale = 1.20f * 1.06f,
            saturation = 1.15f,
            brightness = 0.46f,
        )
    } else {
        AmllBackgroundVisualSpec(
            blurRadiusDp = 48f,
            combinedScale = 1.20f * 1.10f,
            saturation = 1.30f,
            brightness = 0.55f,
        )
    }

/**
 * Native equivalent of the shared AMLL page's `#bg`, `#dynamic-bg`, and `#bg-scrim`
 * layers. The static artwork and every visual effect stay in common Compose; only video
 * frame production is delegated to the platform leaf composable.
 */
@Composable
internal fun AmllBackground(
    artworkUri: String?,
    fallbackArtworkUri: String?,
    dynamicCoverUrl: String?,
    playDynamicCover: Boolean,
    androidPresentation: Boolean,
    reducedMotion: Boolean,
    onArtworkLoaded: (Image) -> Unit,
    modifier: Modifier = Modifier,
) {
    // player.html selects these values by `data-platform="android"`, not by viewport width.
    // Keeping that distinction matters for Android tablets and narrow desktop windows.
    val visualSpec = amllBackgroundVisualSpec(androidPresentation)
    val blurRadius = visualSpec.blurRadiusDp.dp
    // applyViewportSize() first made both background elements 120% of the viewport and centered
    // them at -10%, then CSS applied the platform scale. Equal aspect ratios make the combined
    // centered scale exactly 1.20 * 1.06/1.10.
    val scale = visualSpec.combinedScale
    val saturation = visualSpec.saturation
    val brightness = visualSpec.brightness
    var displayedArtworkUri by remember(artworkUri, fallbackArtworkUri) {
        mutableStateOf(artworkUri)
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF0A0A0A)),
    ) {
        AnimatedContent(
            targetState = displayedArtworkUri,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { artworkCrossfade(reducedMotion) },
            label = "AmllArtworkCrossfade",
        ) { currentArtwork ->
            AsyncImage(
                model = currentArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(saturation) },
                ),
                onSuccess = { state ->
                    if (currentArtwork == displayedArtworkUri) {
                        onArtworkLoaded(state.result.image)
                    }
                },
                onError = {
                    nextAmllArtworkUriAfterFailure(
                        failedUri = currentArtwork,
                        primaryUri = artworkUri,
                        fallbackUri = fallbackArtworkUri,
                    )?.let { fallback ->
                        displayedArtworkUri = fallback
                    }
                },
                modifier = Modifier.amllBackgroundEffect(
                    blurRadius = blurRadius,
                    scale = scale,
                ),
            )
        }

        dynamicCoverUrl
            ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            ?.let { url ->
                NativeDynamicCoverLayer(
                    url = url,
                    play = playDynamicCover,
                    showBadge = false,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.amllBackgroundEffect(
                        blurRadius = blurRadius,
                        scale = scale,
                    ),
                )
            }

        // A black source-over layer is equivalent to CSS brightness() for both Compose images
        // and the Android TextureView leaf: outputRgb = inputRgb * brightness.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - brightness)),
        )

        // AMLL uses a horizontal 0.62 -> 0.56 black scrim. Keeping this separate from
        // image saturation also makes the fallback deterministic when artwork is absent.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = visualSpec.scrimStartAlpha),
                            Color.Black.copy(alpha = visualSpec.scrimEndAlpha),
                        ),
                    ),
                ),
        )
    }
}

internal fun nextAmllArtworkUriAfterFailure(
    failedUri: String?,
    primaryUri: String?,
    fallbackUri: String?,
): String? = fallbackUri
    ?.takeIf(String::isNotBlank)
    ?.takeIf { failedUri == primaryUri && it != failedUri }

private fun Modifier.amllBackgroundEffect(
    blurRadius: Dp,
    scale: Float,
): Modifier = fillMaxSize()
    .graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    .blur(
        radius = blurRadius,
        edgeTreatment = BlurredEdgeTreatment.Unbounded,
    )

private fun artworkCrossfade(reducedMotion: Boolean): ContentTransform =
    if (reducedMotion) {
        fadeIn(snap()) togetherWith fadeOut(snap())
    } else {
        fadeIn(tween(BackgroundCrossfadeMillis, easing = BackgroundCssEase)) togetherWith
            fadeOut(tween(BackgroundCrossfadeMillis, easing = BackgroundCssEase))
    }
