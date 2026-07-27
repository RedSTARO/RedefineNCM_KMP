package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.lyric.LyricStateOverlay
import com.leejlredstar.redefinencm.kmp.player.PlayerState
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.ui.component.AutoHideMiniPlayerController
import com.leejlredstar.redefinencm.kmp.ui.component.NativeDynamicCoverLayer
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsButton
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsSheet
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.SongWikiUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private data class AmllLyricPreferences(
    val showTranslated: Boolean = false,
    val showRoman: Boolean = false,
)

private val WikiBackdropCssEase = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.00f)
private const val NativeAmllControllerAutoHideMillis = 3_600L
private const val DesktopAmllControllerAutoHideMillis = 30_000L

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
    settings: PlatformSettings = koinInject(),
) {
    val lyricMap by viewModel.lyricMap.collectAsState()
    val lyricUiState by viewModel.lyricUiState.collectAsState()
    val lyricMediaId by viewModel.lyricMediaId.collectAsState()
    val rawLyric by viewModel.rawLyric.collectAsState()
    val rawWordLyric by viewModel.rawWordLyric.collectAsState()
    val wordLyricLines by viewModel.wordLyricLines.collectAsState()
    val rawTranslatedLyric by viewModel.rawTranslatedLyric.collectAsState()
    val rawRomanLyric by viewModel.rawRomanLyric.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.songLength.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()
    val dynamicCoverState by viewModel.dynamicCoverUiState.collectAsState()
    val songWikiState by viewModel.songWikiUiState.collectAsState()

    var lyricPreferences by remember(settings) { mutableStateOf(AmllLyricPreferences()) }
    var showSongWikiDetails by remember { mutableStateOf(false) }
    var wikiDynamicCoverVisible by remember { mutableStateOf(false) }
    var keepSongWikiBackdropBlur by remember { mutableStateOf(false) }
    var songWikiBackdropOpen by remember { mutableStateOf(false) }
    var controllerRevealRequest by remember { mutableIntStateOf(0) }
    val songWikiButtonFocusRequester = remember { FocusRequester() }
    val reducedMotion = rememberReducedMotionEnabled()
    val platform = remember { getPlatform() }

    LaunchedEffect(settings) {
        try {
            settings.awaitLoaded()
            lyricPreferences = AmllLyricPreferences(
                showTranslated = settings.getBooleanAsync(
                    SettingKeys.SHOW_TRANSLATED_LYRIC,
                    false,
                ),
                showRoman = settings.getBooleanAsync(
                    SettingKeys.SHOW_ROMAN_LYRIC,
                    false,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            lyricPreferences = AmllLyricPreferences()
        }
    }

    LaunchedEffect(metadata?.id) {
        showSongWikiDetails = false
        wikiDynamicCoverVisible = false
    }
    LaunchedEffect(showSongWikiDetails, reducedMotion) {
        if (showSongWikiDetails) {
            keepSongWikiBackdropBlur = true
            songWikiBackdropOpen = false
            if (!reducedMotion) withFrameNanos { }
            songWikiBackdropOpen = true
        } else if (keepSongWikiBackdropBlur) {
            // `#wiki-overlay` stays in the DOM for its 180 ms close fade, so its
            // backdrop-filter remains active until the delayed `hidden=true`.
            songWikiBackdropOpen = false
            if (!reducedMotion) delay(180L)
            keepSongWikiBackdropBlur = false
        }
    }
    val songWikiBackdropBlurRadius by animateDpAsState(
        targetValue = if (songWikiBackdropOpen) 16.dp else 0.dp,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = 180, easing = WikiBackdropCssEase)
        },
        label = "wiki-backdrop-blur",
    )

    val lyricsBelongToCurrentMedia = metadata != null &&
        metadata?.id == lyricMediaId &&
        lyricUiState is LyricUiState.Content
    val document = remember(
        lyricsBelongToCurrentMedia,
        lyricMap,
        rawLyric,
        rawWordLyric,
        wordLyricLines,
        rawTranslatedLyric,
        rawRomanLyric,
        lyricPreferences,
    ) {
        if (lyricsBelongToCurrentMedia) {
            buildAmllLyricDocument(
                lyricMap = lyricMap,
                rawLrc = rawLyric,
                rawYrc = rawWordLyric,
                wordLines = wordLyricLines,
                translatedLrc = rawTranslatedLyric,
                romanLrc = rawRomanLyric,
                showTranslated = lyricPreferences.showTranslated,
                showRoman = lyricPreferences.showRoman,
            )
        } else {
            AmllLyricDocument(emptyList())
        }
    }
    val presentationPosition = rememberPresentationPosition(
        sampledPositionMs = currentPosition,
        durationMs = duration,
        advancing = isPlaying && playerState == PlayerState.PLAYING && !reducedMotion,
    )

    // player.html keeps the full-screen dynamic cover available under reduced motion. The
    // preference disables the song-wiki video and pauses the background immediately; in normal
    // motion the background pauses only after the wiki video has presented a frame.
    val dynamicCoverUrl = dynamicCoverState.urlFor(metadata?.id)
    val scopedSongWikiState = songWikiState.scopedTo(metadata?.id)
    LaunchedEffect(dynamicCoverUrl) {
        wikiDynamicCoverVisible = false
    }
    val playDynamicBackground = shouldPlayAmllDynamicBackground(
        songWikiVisible = showSongWikiDetails,
        reducedMotion = reducedMotion,
        wikiDynamicCoverVisible = wikiDynamicCoverVisible,
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .then(
                if (keepSongWikiBackdropBlur) {
                    Modifier.blur(
                        radius = songWikiBackdropBlurRadius,
                        edgeTreatment = BlurredEdgeTreatment.Rectangle,
                    )
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = "正在播放歌词" },
    ) {
        val mobileWikiPosition = maxWidth <= 600.dp
        val visualParameters = remember(maxWidth, maxHeight, reducedMotion) {
            calculateAmllLyricVisualParameters(
                viewportWidthDp = maxWidth.value,
                viewportHeightDp = maxHeight.value,
                reducedMotion = reducedMotion,
            )
        }

        AmllBackground(
            artworkUri = metadata?.artworkUri,
            dynamicCoverUrl = dynamicCoverUrl,
            playDynamicCover = playDynamicBackground,
            androidPresentation = platform.isAndroid,
            reducedMotion = reducedMotion,
            onArtworkLoaded = {},
            modifier = Modifier.fillMaxSize(),
        )

        // Tapping unused background space should reveal the same shared controller without
        // stealing line-click or sheet gestures from children composed above it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                controllerRevealRequest += 1
                            }
                        }
                    }
                },
        )

        if (document.lines.isNotEmpty() && metadata != null) {
            AmllLyricViewport(
                document = document,
                mediaId = metadata!!.id,
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
                onRetry = viewModel::retryLyrics,
            )
        }

        AmllTopActions(
            mobileWikiPosition = mobileWikiPosition,
            hasMedia = metadata != null,
            reducedMotion = reducedMotion,
            songWikiButtonFocusRequester = songWikiButtonFocusRequester,
            onBack = onBack,
            onOpenDetails = {
                showSongWikiDetails = true
                if (shouldRequestSongWikiOnOpen(songWikiState, metadata?.id)) {
                    viewModel.getSongWikiSummary()
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )

        AutoHideMiniPlayerController(
            modifier = Modifier.fillMaxSize(),
            autoHideDelayMillis = amllControllerAutoHideDelayMillis(platform.isDesktop),
            reducedMotion = reducedMotion,
            externalRevealRequest = controllerRevealRequest,
        )
    }

    SongWikiDetailsSheet(
        visible = showSongWikiDetails,
        songTitle = metadata?.title,
        songArtist = metadata?.artist,
        albumTitle = metadata?.albumTitle,
        artworkUri = metadata?.artworkUri,
        durationMs = metadata?.duration,
        artworkOverlay = dynamicCoverUrl
            ?.takeUnless { reducedMotion }
            ?.let { videoUrl ->
            {
                NativeDynamicCoverLayer(
                    url = videoUrl,
                    play = showSongWikiDetails,
                    showBadge = true,
                    reducedMotion = reducedMotion,
                    onVisibilityChanged = { visible ->
                        wikiDynamicCoverVisible = visible
                    },
                    modifier = Modifier.matchParentSize(),
                )
            }
        },
        state = scopedSongWikiState,
        onDismiss = { showSongWikiDetails = false },
        onRetry = viewModel::getSongWikiSummary,
        reducedMotion = reducedMotion,
        returnFocusRequester = songWikiButtonFocusRequester,
    )
}

/**
 * Literal port of `playWikiDynamicCover()` in player.html.
 *
 * In normal motion the full-screen video keeps advancing until the detail video has
 * successfully presented its first frame. That avoids freezing the background when the
 * detail decoder fails. Reduced motion is the one source path that pauses it immediately.
 */
internal fun shouldPlayAmllDynamicBackground(
    songWikiVisible: Boolean,
    reducedMotion: Boolean,
    wikiDynamicCoverVisible: Boolean,
): Boolean =
    !songWikiVisible || (!reducedMotion && !wikiDynamicCoverVisible)

/**
 * The former Desktop host kept its expanded controller available for 30 seconds. Other targets
 * used the shared controller's 3.6-second default, so preserve that distinction without forking
 * the common screen tree.
 */
internal fun amllControllerAutoHideDelayMillis(isDesktop: Boolean): Long =
    if (isDesktop) DesktopAmllControllerAutoHideMillis else NativeAmllControllerAutoHideMillis

/**
 * `player.html::openSongWiki()` fetches only from its idle state. Closing and reopening an error
 * keeps that error visible; the explicit retry button is the retry path for the same track.
 */
internal fun shouldRequestSongWikiOnOpen(
    state: SongWikiUiState,
    mediaId: String?,
): Boolean = mediaId != null && state.scopedTo(mediaId) is SongWikiUiState.Idle

@Composable
private fun AmllTopActions(
    mobileWikiPosition: Boolean,
    hasMedia: Boolean,
    reducedMotion: Boolean,
    songWikiButtonFocusRequester: FocusRequester,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeEnd = WindowInsets.safeDrawing.asPaddingValues().calculateEndPadding(layoutDirection)
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

        SongWikiDetailsButton(
            enabled = hasMedia,
            onClick = onOpenDetails,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = safeEnd + if (mobileWikiPosition) 12.dp else 18.dp),
            tint = Color.White.copy(alpha = 0.94f),
            reducedMotion = reducedMotion,
            focusRequester = songWikiButtonFocusRequester,
        )
    }
}

@Composable
private fun rememberPresentationPosition(
    sampledPositionMs: Long,
    durationMs: Long,
    advancing: Boolean,
): State<Long> = produceState(
    initialValue = sampledPositionMs.coerceAtLeast(0L),
    sampledPositionMs,
    durationMs,
    advancing,
) {
    val safeSample = sampledPositionMs.coerceAtLeast(0L)
    val anchoredSample = if (durationMs > 0L) {
        safeSample.coerceAtMost(durationMs)
    } else {
        safeSample
    }
    value = anchoredSample
    if (!advancing) {
        return@produceState
    }

    val frameOrigin = withFrameNanos { it }
    while (true) {
        withFrameNanos { frameTime ->
            val elapsedMs = ((frameTime - frameOrigin) / 1_000_000L).coerceAtLeast(0L)
            val extrapolated = anchoredSample + elapsedMs
            value = if (durationMs > 0L) {
                extrapolated.coerceAtMost(durationMs)
            } else {
                extrapolated
            }
        }
    }
}

private fun SongWikiUiState.scopedTo(mediaId: String?): SongWikiUiState {
    if (mediaId == null) return SongWikiUiState.Idle
    val stateMediaId = when (this) {
        is SongWikiUiState.Idle -> return this
        is SongWikiUiState.Loading -> this.mediaId
        is SongWikiUiState.Content -> this.mediaId
        is SongWikiUiState.Empty -> this.mediaId
        is SongWikiUiState.Error -> this.mediaId
    }
    return if (stateMediaId == mediaId) this else SongWikiUiState.Idle
}
