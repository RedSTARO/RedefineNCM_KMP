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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.AmllBackground
import com.leejlredstar.amll.compose.AmllLyricDocument
import com.leejlredstar.amll.compose.AmllLyricViewport
import com.leejlredstar.amll.compose.AmllLyricVisualParameters
import com.leejlredstar.amll.compose.buildAmllLyricDocument
import com.leejlredstar.amll.compose.calculateAmllLyricVisualParameters
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.DarkColors
import com.leejlredstar.redefinencm.kmp.ui.theme.DeclareSystemBarBackground
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.lyric.LyricStateOverlay
import com.leejlredstar.redefinencm.kmp.player.PlayerState
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.ui.component.AutoHideMiniPlayerController
import com.leejlredstar.redefinencm.kmp.ui.component.NativeDynamicCoverLayer
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackFold
import com.leejlredstar.redefinencm.kmp.ui.component.ReflowEntry
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackPaneArrangement
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackReflowTarget
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackViewport
import com.leejlredstar.redefinencm.kmp.ui.component.hasExpandedPlaybackWidth
import com.leejlredstar.redefinencm.kmp.ui.component.inPlaybackPane
import com.leejlredstar.redefinencm.kmp.ui.component.isWidePlaybackViewport
import com.leejlredstar.redefinencm.kmp.ui.component.playbackPaneArrangement
import com.leejlredstar.redefinencm.kmp.ui.component.playbackSafeContent
import com.leejlredstar.redefinencm.kmp.ui.component.rememberPlaybackReflow
import com.leejlredstar.redefinencm.kmp.ui.component.placementIn
import com.leejlredstar.redefinencm.kmp.ui.component.reflowPlacement
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import kotlinx.coroutines.flow.collect
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.material3.MaterialTheme
import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.viewmodel.lyricCapabilityLevel

/**
 * The Android control island's own timeout, used on every target.
 *
 * Desktop shows the same island as Android and follows the same timing.
 */
private const val AmllControllerAutoHideMillis = 3_600L
private const val AndroidPortraitLyricFontSizeLimitDp = 36f

/**
 * One native AMLL-style full-screen player shared by Android, iOS, Desktop, and Web.
 *
 * The common tree owns the artwork treatment, lyric document/layout, controls, song details,
 * accessibility, and input. A platform leaf is used only when native video frames are available.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    // Keep the island's dark artwork palette, even when the compact controls hide their cover.
    val artworkAccent = rememberArtworkAccent(
        requestKey = primaryArtworkUri to fallbackArtworkUri,
        fallback = DarkColors.primaryContainer,
        scheme = DarkColors,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "fullLyricArtworkAccent",
    )
    // Near-black under the bars whatever the theme: light icons.
    DeclareSystemBarBackground(dark = true)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .semantics { contentDescription = strings.nowPlayingLyrics },
    ) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        AmllBackground(
            artworkUri = primaryArtworkUri,
            fallbackArtworkUri = fallbackArtworkUri,
            dynamicCoverUrl = dynamicCoverUrl,
            // Nothing opens over this page (the song's details are on Now Playing), so
            // nothing pauses it.
            playDynamicCover = true,
            androidPresentation = platform.isAndroid,
            reducedMotion = reducedMotion,
            onArtworkLoaded = artworkAccent.extract,
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

        // Only the artwork is edge to edge. The interactive viewport is clear of cutouts,
        // system gesture areas (including landscape side bars), and a resized window's IME.
        val viewport = Modifier.fillMaxSize().then(
            if (platform.isAndroid) Modifier.windowInsetsPadding(WindowInsets.playbackSafeContent).clipToBounds()
            else Modifier,
        )
        PlaybackViewport(viewport, trackFold = platform.isAndroid) { fold, positioned ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val full = IntRect(0, 0, constraints.maxWidth, constraints.maxHeight)
                val untimedLyrics = lyricsBelongToCurrentMedia &&
                    lyricUiState.lyricCapabilityLevel == LyricCapabilityLevel.UNSYNCED &&
                    untimedLyricLines.isNotEmpty()
                // Use lyric groups, not translations/background vocals, to judge the amount of text.
                val lyricCount = if (untimedLyrics) untimedLyricLines.size else document.groups.size
                val (lyricPane, controlsPane) = if (platform.isAndroid) {
                    nativeAmllPanes(full, density, fold, isWidePlaybackViewport(windowWidth, windowHeight), lyricCount)
                } else {
                    full to full
                }
                val sharedPane = lyricPane == controlsPane
                val lyricWidth = with(density) { lyricPane.width.toDp() }
                val lyricHeight = with(density) { lyricPane.height.toDp() }
                val controlHeight = with(density) { controlsPane.height.toDp() }
                val expandedWidth = hasExpandedPlaybackWidth(maxWidth)
                val layout = nativeAmllReflowLayout(full, lyricPane, controlsPane, density)
                val shortLyrics = platform.isAndroid && layout.shortLyrics
                val limitIslandHeight = platform.isAndroid && sharedPane && controlHeight < 420.dp
                val hasSpace = controlsPane.width > 0 && controlsPane.height > 0
                val reflow = rememberPlaybackReflow(
                    enabled = platform.isAndroid,
                    target = if (hasSpace && positioned) PlaybackReflowTarget(
                        full, fold?.placementIn(full), layout, expandedWidth,
                    ) else null,
                    reducedMotion = reducedMotion,
                )
                if (!hasSpace) return@BoxWithConstraints
                Box(Modifier.inPlaybackPane(lyricPane)) {
                    // Only a completed tap on unused background space reveals the controller.
                    // A press may become a lyric drag or trackpad gesture and must not reveal it.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures(onTap = { controllerRevealRequest += 1 })
                    })
                    if (untimedLyrics) {
                        // The list's media key preserves its saveable scroll but cannot reuse the
                        // old song's offset for another song.
                        key(metadata?.id) {
                            UntimedLyrics(
                                lines = untimedLyricLines,
                                palette = artworkAccent.palette,
                                compact = shortLyrics,
                                controllerOverlaysLyrics = sharedPane,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (document.lines.isNotEmpty() && metadata != null) {
                        val visualParameters = remember(lyricWidth, lyricHeight, windowWidth, windowHeight, platform.isAndroid, reducedMotion) {
                            calculateNativeAmllVisualParameters(
                                viewportWidth = lyricWidth,
                                viewportHeight = lyricHeight,
                                windowWidth = windowWidth,
                                windowHeight = windowHeight,
                                androidPresentation = platform.isAndroid,
                                reducedMotion = reducedMotion,
                            )
                        }
                        // Keep this call in place during resizing; AMLL remeasures the active
                        // document without starting a new timeline or clearing its live scroll.
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
                        // Error/empty panels can be taller than a letterboxed or split viewport.
                        // Keep their action above the island and scroll to it instead of clipping it.
                        val overlay = if (shortLyrics) {
                            Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                                .heightIn(max = (lyricHeight - sharedControllerHeight(lyricHeight) - 48.dp)
                                    .coerceAtLeast(0.dp))
                                .verticalScroll(rememberScrollState())
                        } else {
                            Modifier.align(Alignment.Center)
                        }
                        Box(overlay.widthIn(max = 560.dp).fillMaxWidth()) {
                            LyricStateOverlay(
                                state = lyricUiState,
                                hasMedia = metadata != null,
                                isPlayerRestoring = playerStatusRestoreState is PlayerStatusRestoreState.Loading,
                                onRetry = viewModel::retryLyrics,
                            )
                        }
                    }
                    AmllTopActions(
                        onBack = onBack,
                        tint = artworkAccent.palette.onQuietContainer,
                        compact = shortLyrics,
                        modifier = Modifier.align(Alignment.TopStart)
                            .reflowPlacement(reflow, ReflowEntry.TowardEndFromStart),
                    )
                }
                Box(Modifier.inPlaybackPane(controlsPane)) {
                    val controlModifier = if (limitIslandHeight) {
                        // On a narrow split, keep space for the lyrics/close action even when the
                        // expanded island needs to scroll all of its controls into view.
                        Modifier.align(Alignment.BottomCenter)
                            .heightIn(max = sharedControllerHeight(controlHeight))
                    } else {
                        Modifier
                    }
                    AutoHideMiniPlayerController(
                        modifier = controlModifier.fillMaxSize(),
                        islandModifier = Modifier.reflowPlacement(reflow, ReflowEntry.TowardTopFromBottom),
                        autoHideDelayMillis = AmllControllerAutoHideMillis,
                        reducedMotion = reducedMotion,
                        externalRevealRequest = controllerRevealRequest,
                        lyricArtworkAccent = artworkAccent,
                    )
                }
            }
        }
    }
}

internal data class AmllReflowLayout(
    val arrangement: PlaybackPaneArrangement,
    val shortLyrics: Boolean,
    val compactIsland: Boolean,
)

internal fun nativeAmllReflowLayout(
    full: IntRect,
    lyricPane: IntRect,
    controlsPane: IntRect,
    density: Density,
): AmllReflowLayout = with(density) {
    AmllReflowLayout(
        arrangement = playbackPaneArrangement(full, lyricPane, controlsPane),
        shortLyrics = lyricPane.height.toDp() < 420.dp || lyricPane != controlsPane,
        compactIsland = controlsPane.height.toDp() < 420.dp || controlsPane.width.toDp() < 360.dp,
    )
}

internal fun nativeAmllPanes(
    full: IntRect,
    density: Density,
    fold: PlaybackFold?,
    wideWindow: Boolean,
    lyricCount: Int,
): Pair<IntRect, IntRect> = with(density) {
    val placement = fold?.placementIn(full)
    // A pane should have room for a verse. A status panel or isolated line doesn't warrant one.
    val usefulLyrics = lyricCount >= 4
    if (placement != null) {
        val folded = placement.split
        if (usefulLyrics && folded != null &&
            folded.first.width.toDp() >= 320.dp && folded.first.height.toDp() >= 420.dp &&
            folded.second.width.toDp() >= 340.dp && folded.second.height.toDp() >= 420.dp
        ) {
            return folded.first to folded.second
        }
        return placement.single to placement.single
    }
    if (usefulLyrics && wideWindow && full.width.toDp() >= 720.dp && full.height.toDp() in 224.dp..<420.dp) {
        val split = (full.width * 0.53f).toInt()
        IntRect(0, 0, split, full.height) to IntRect(split, 0, full.width, full.height)
    } else {
        full to full
    }
}

private fun sharedControllerHeight(height: Dp): Dp = minOf(height, maxOf(96.dp, height * 0.58f))

internal fun calculateNativeAmllVisualParameters(
    viewportWidth: Dp,
    viewportHeight: Dp,
    windowHeight: Dp,
    androidPresentation: Boolean,
    reducedMotion: Boolean,
    windowWidth: Dp = viewportWidth,
): AmllLyricVisualParameters {
    // Insets and a separated playback pane may have a different aspect ratio than the
    // window. Classify the full window, then lay out the lyrics in the actual viewport.
    val fontSizeLimitDp = if (androidPresentation && windowWidth <= windowHeight) {
        AndroidPortraitLyricFontSizeLimitDp
    } else {
        Float.POSITIVE_INFINITY
    }
    return calculateAmllLyricVisualParameters(
        viewportWidthDp = viewportWidth.value,
        viewportHeightDp = viewportHeight.value,
        reducedMotion = reducedMotion,
        maxBaseFontSizeDp = fontSizeLimitDp,
    )
}

@Composable
private fun AmllTopActions(
    onBack: () -> Unit,
    tint: Color,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = if (compact) 0.dp else 18.dp),
    ) {
        // Down, not back: the lyrics are the player opened up, and this puts them away with the
        // same gesture and the same glyph as the Now Playing page it returns to. Nothing behind
        // it: the artwork under the lyrics is blurred and darkened, so a filled circle would be
        // one more shape on a page whose whole point is the words.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = if (compact) 8.dp else 18.dp)
                .size(48.dp)
                .semantics { contentDescription = strings.closeLyrics },
        ) {
            Icon(
                imageVector = AppIcons.KeyboardArrowDown,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(32.dp),
            )
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
 * every frame, so masks, floats and emphasis glows advance at the refresh rate, on a high-refresh
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
    palette: ContentAccentPalette,
    compact: Boolean,
    controllerOverlaysLyrics: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = if (compact) 20.dp else 32.dp,
            end = if (compact) 20.dp else 32.dp,
            top = if (compact) 52.dp else 120.dp,
            bottom = when {
                !controllerOverlaysLyrics -> 12.dp
                compact -> 76.dp
                else -> 200.dp
            },
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "untimed-note") {
            Text(
                text = strings.lyricsUntimedNote,
                color = palette.secondaryOnQuietContainer,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        itemsIndexed(lines) { _, line ->
            Text(
                text = line,
                color = palette.onPageEnd,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
