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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.AmllBackground
import com.leejlredstar.amll.compose.AmllLyricDocument
import com.leejlredstar.amll.compose.AmllLyricViewport
import com.leejlredstar.amll.compose.buildAmllLyricDocument
import com.leejlredstar.amll.compose.calculateAmllLyricVisualParameters
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.lyric.LyricStateOverlay
import com.leejlredstar.redefinencm.kmp.player.PlayerState
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.ui.component.AutoHideMiniPlayerController
import com.leejlredstar.redefinencm.kmp.ui.component.NativeDynamicCoverLayer
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import kotlinx.coroutines.flow.collect
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.viewmodel.lyricCapabilityLevel

/**
 * The Android control island's own timeout, now the timeout on every target.
 *
 * Desktop used to keep the expanded console up for 30 seconds because its console lived inside
 * the AMLL WebView page and a Compose reveal could not sit above the WebView2 child HWND. That
 * page is gone; desktop shows the same island as Android and follows the same timing.
 */
private const val AmllControllerAutoHideMillis = 3_600L

/**
 * One native AMLL-style full-screen player shared by Android, iOS, Desktop, and Web.
 *
 * The common tree owns the artwork treatment, lyric document/layout, controls, song details,
 * accessibility, and input. A platform leaf is used only when native video frames are available.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NativeAmllScreen(
    onBack: () -> Unit = {},
    viewModel: NowPlayingViewModel = koinInject(),
) {
    val lyricMap by viewModel.lyricMap.collectAsState()
    val lyricUiState by viewModel.lyricUiState.collectAsState()
    val lyricMediaId by viewModel.lyricMediaId.collectAsState()
    val wordLyricLines by viewModel.wordLyricLines.collectAsState()
    val rawTranslatedLyric by viewModel.rawTranslatedLyric.collectAsState()
    val rawRomanLyric by viewModel.rawRomanLyric.collectAsState()
    val showTranslatedLyric by viewModel.showTranslatedLyric.collectAsState()
    val showRomanLyric by viewModel.showRomanLyric.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.songLength.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()
    val playerStatusRestoreState by viewModel.playerStatusRestoreState.collectAsState()
    val dynamicCoverState by viewModel.dynamicCoverUiState.collectAsState()
    val localArtworkActive by viewModel.localArtworkActive.collectAsState()
    val remoteArtworkUri by viewModel.remoteArtworkUri.collectAsState()
    val songWikiState by viewModel.songWikiUiState.collectAsState()
    val untimedLyricLines by viewModel.untimedLyricLines.collectAsState()

    var controllerRevealRequest by remember { mutableIntStateOf(0) }
    val reducedMotion = rememberReducedMotionEnabled()
    val platform = remember { getPlatform() }


    val lyricsBelongToCurrentMedia = metadata != null &&
        metadata?.id == lyricMediaId &&
        lyricUiState is LyricUiState.Content
    val document = remember(
        lyricsBelongToCurrentMedia,
        lyricMap,
        wordLyricLines,
        rawTranslatedLyric,
        rawRomanLyric,
        showTranslatedLyric,
        showRomanLyric,
    ) {
        if (lyricsBelongToCurrentMedia) {
            buildAmllLyricDocument(
                lyricMap = lyricMap,
                wordLines = wordLyricLines,
                translatedLrc = rawTranslatedLyric,
                romanLrc = rawRomanLyric,
                showTranslated = showTranslatedLyric,
                showRoman = showRomanLyric,
            )
        } else {
            AmllLyricDocument(emptyList())
        }
    }
    val presentationPosition = rememberPresentationPosition(
        sampledPositionMs = currentPosition,
        durationMs = duration,
        // Reduced motion disables spatial transitions, not the lyric playback clock.
        advancing = isPlaying && playerState == PlayerState.PLAYING,
    )

    val primaryArtworkUri = metadata?.artworkUri
    val fallbackArtworkUri = remoteArtworkUri
        .takeIf { localArtworkActive && it.isNotBlank() && it != primaryArtworkUri }
    val dynamicCoverUrl = dynamicCoverState
        .urlFor(metadata?.id)
        .takeUnless { localArtworkActive }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .semantics { contentDescription = "正在播放歌词" },
    ) {
        val visualParameters = remember(maxWidth, maxHeight, reducedMotion) {
            calculateAmllLyricVisualParameters(
                viewportWidthDp = maxWidth.value,
                viewportHeightDp = maxHeight.value,
                reducedMotion = reducedMotion,
            )
        }

        AmllBackground(
            artworkUri = primaryArtworkUri,
            fallbackArtworkUri = fallbackArtworkUri,
            dynamicCoverUrl = dynamicCoverUrl,
            // Nothing pauses it any more: the song's details left this page for Now
            // Playing, and they were the only thing that ever opened over it.
            playDynamicCover = true,
            androidPresentation = platform.isAndroid,
            reducedMotion = reducedMotion,
            onArtworkLoaded = {},
            modifier = Modifier.fillMaxSize(),
            // `#dynamic-bg`. The renderer owns every filter around it; this supplies frames only.
            dynamicCoverLayer = { url, play, layerReducedMotion, layerModifier ->
                NativeDynamicCoverLayer(
                    url = url,
                    play = play,
                    showBadge = false,
                    reducedMotion = layerReducedMotion,
                    modifier = layerModifier,
                )
            },
        )

        // Only a completed tap on unused background space reveals the controller. A raw press
        // may become a lyric drag or trackpad gesture and must not expand the island.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controllerRevealRequest += 1
                        },
                    )
                },
        )

        val untimedLyrics = lyricsBelongToCurrentMedia &&
            lyricUiState.lyricCapabilityLevel == LyricCapabilityLevel.UNSYNCED &&
            untimedLyricLines.isNotEmpty()
        if (untimedLyrics) {
            // Lyrics without timestamps used to leave the page blank: there was nothing to sync,
            // so nothing was drawn. The text itself is still worth reading.
            UntimedLyrics(
                lines = untimedLyricLines,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (document.lines.isNotEmpty() && metadata != null) {
            AmllLyricViewport(
                document = document,
                mediaId = metadata!!.id,
                sampledPositionMs = currentPosition,
                positionState = presentationPosition,
                isPlaying = isPlaying,
                parameters = visualParameters,
                androidPresentation = platform.isAndroid,
                onSeek = viewModel::onLyricLineClick,
                onInteraction = { controllerRevealRequest += 1 },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LyricStateOverlay(
                state = lyricUiState,
                hasMedia = metadata != null,
                isPlayerRestoring = playerStatusRestoreState is PlayerStatusRestoreState.Loading,
                onRetry = viewModel::retryLyrics,
            )
        }

        AmllTopActions(
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )

        AutoHideMiniPlayerController(
            modifier = Modifier.fillMaxSize(),
            autoHideDelayMillis = AmllControllerAutoHideMillis,
            reducedMotion = reducedMotion,
            externalRevealRequest = controllerRevealRequest,
        )
    }

}

@Composable
private fun AmllTopActions(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val backGlyphSize = with(density) { 32.dp.toSp() }
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 18.dp),
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp)
                .size(48.dp)
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = 28.dp,
                        color = Color.Black,
                        spread = 0.dp,
                        offset = DpOffset(x = 0.dp, y = 10.dp),
                        alpha = 0.24f,
                    ),
                )
                .semantics { contentDescription = "返回" },
            shape = CircleShape,
            color = Color(0xFF181919).copy(alpha = 0.72f),
            contentColor = Color.White.copy(alpha = 0.94f),
            shadowElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "‹",
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = backGlyphSize,
                    lineHeight = backGlyphSize,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
    }
}

/**
 * The lyric clock: the player's position sample, advanced by the platform frame clock between
 * samples.
 *
 * AMLL's word animations are Web Animations on the document timeline, so they move on every
 * display refresh whatever the host's `timeupdate` cadence. The player publishes a sample only
 * every 100 ms; this clock therefore anchors to the newest sample and publishes a position on
 * every frame, so masks, floats and emphasis glows advance at the refresh rate — on a high-refresh
 * desktop as well as on a 60 Hz phone.
 */
@Composable
private fun rememberPresentationPosition(
    sampledPositionMs: Long,
    durationMs: Long,
    advancing: Boolean,
): State<Long> {
    val latestSampledPosition = rememberUpdatedState(sampledPositionMs)
    return produceState(
        initialValue = coerceAmllPresentationPosition(sampledPositionMs, durationMs),
        durationMs,
        advancing,
    ) {
        if (!advancing) {
            snapshotFlow { latestSampledPosition.value }.collect { sample ->
                value = coerceAmllPresentationPosition(sample, durationMs)
            }
            return@produceState
        }

        var observedSample = latestSampledPosition.value
        var anchoredSample = coerceAmllPresentationPosition(observedSample, durationMs)
        var anchorFrameNanos: Long? = null
        while (true) {
            val frameTimeNanos = withFrameNanos { it }
            val currentSample = latestSampledPosition.value
            if (anchorFrameNanos == null || currentSample != observedSample) {
                observedSample = currentSample
                anchoredSample = coerceAmllPresentationPosition(currentSample, durationMs)
                anchorFrameNanos = frameTimeNanos
            }

            value = amllPresentationPositionAt(
                anchoredSampleMs = anchoredSample,
                anchorFrameNanos = checkNotNull(anchorFrameNanos),
                frameTimeNanos = frameTimeNanos,
                durationMs = durationMs,
            )
        }
    }
}

internal fun amllPresentationPositionAt(
    anchoredSampleMs: Long,
    anchorFrameNanos: Long,
    frameTimeNanos: Long,
    durationMs: Long,
): Long {
    val elapsedMs = (frameTimeNanos - anchorFrameNanos).coerceAtLeast(0L) / 1_000_000L
    return coerceAmllPresentationPosition(anchoredSampleMs + elapsedMs, durationMs)
}

private fun coerceAmllPresentationPosition(
    positionMs: Long,
    durationMs: Long,
): Long {
    val safePosition = positionMs.coerceAtLeast(0L)
    return if (durationMs > 0L) {
        safePosition.coerceAtMost(durationMs)
    } else {
        safePosition
    }
}

/** Lyrics that have text but no timing, shown as a plain scrollable column. */
@Composable
private fun UntimedLyrics(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 120.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "untimed-note") {
            Text(
                text = "这首歌的歌词没有时间轴，不能随播放滚动",
                color = Color.White.copy(alpha = 0.56f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        itemsIndexed(lines) { _, line ->
            Text(
                text = line,
                color = Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
