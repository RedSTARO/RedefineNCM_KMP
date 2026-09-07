package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.lyric.LyricSource
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.lyricCapabilityLevel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AutoHideMiniPlayerController(
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = true,
    showCollapsedWhenHidden: Boolean = true,
    collapsedHostFillsWidth: Boolean = false,
    autoHideDelayMillis: Long = 3_600L,
    reducedMotion: Boolean = false,
    forceOpaqueSurfaces: Boolean = false,
    externalRevealRequest: Int = 0,
    onOverlayVisibilityChanged: (Boolean) -> Unit = {},
    onSheetVisibilityChanged: (Boolean) -> Unit = {},
    onExpandedChanged: (Boolean) -> Unit = {},
    /**
     * Whether the expanded island carries the output volume row. Phones hand volume to the
     * hardware keys and the system panel; the desktop's only other control is the main
     * window's strip, which the full-screen lyric page covers.
     */
    showOutputVolume: Boolean = remember { getPlatform().isDesktop },
    viewModel: NowPlayingViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
) {
    val nowPlaying = rememberNowPlayingUiState(player, viewModel)
    val outputVolume by player.volume.collectAsState()
    val media = nowPlaying.media
    val isPlaying = nowPlaying.isPlaying
    val position = nowPlaying.position
    val playList = nowPlaying.playList
    val currentIndex = nowPlaying.currentIndex
    val shuffleEnabled = nowPlaying.shuffleEnabled
    val comments = nowPlaying.comments
    val commentsLoading = nowPlaying.commentsLoading
    val commentsLoadError = nowPlaying.commentsLoadError
    val commentsFromCache = nowPlaying.commentsFromCache
    val lyricUiState by viewModel.lyricUiState.collectAsState()
    val lyricMediaId by viewModel.lyricMediaId.collectAsState()
    val activeLyricSource by viewModel.activeLyricSource.collectAsState()
    val activeLyricEndpoint by viewModel.activeLyricEndpoint.collectAsState()

    var visible by remember { mutableStateOf(initialExpanded) }
    var revealRequest by remember { mutableIntStateOf(0) }
    val sheets = rememberTransportSheetsState()
    var showLyricDetails by remember { mutableStateOf(false) }
    // A held volume thumb keeps the island open; the auto-hide timer restarts on release.
    var adjustingOutputVolume by remember { mutableStateOf(false) }

    val hasMedia = nowPlaying.hasMedia
    val isFavorite = nowPlaying.isFavorite
    val lyricCapabilityLevel = lyricUiState.lyricCapabilityLevel.takeIf {
        hasMedia && lyricMediaId == media?.id
    }
    val displayedLyricSource = activeLyricSource.takeIf { lyricCapabilityLevel != null }
    val displayedLyricEndpoint = activeLyricEndpoint.takeIf { lyricCapabilityLevel != null }.orEmpty()
    val totalDuration = nowPlaying.totalDuration
    val progress = nowPlaying.progress

    val artworkAccent = rememberArtworkAccent(
        requestKey = media?.artworkUri,
        label = "fullLyricControlAccent",
    )
    val accentColor = artworkAccent.color
    val accentPalette = artworkAccent.palette
    val extractAccent = artworkAccent.extract

    fun setExpanded(expanded: Boolean) {
        onExpandedChanged(expanded)
        visible = expanded
    }

    fun reveal() {
        setExpanded(true)
        revealRequest += 1
    }

    fun collapse() = setExpanded(false)

    LaunchedEffect(Unit) {
        onExpandedChanged(visible)
    }

    LaunchedEffect(externalRevealRequest) {
        if (externalRevealRequest > 0) reveal()
    }

    LaunchedEffect(
        visible,
        revealRequest,
        sheets.showQueue,
        sheets.showComments,
        showLyricDetails,
        adjustingOutputVolume,
        autoHideDelayMillis,
    ) {
        if (!visible || sheets.anyOpen || showLyricDetails || adjustingOutputVolume) {
            return@LaunchedEffect
        }
        delay(autoHideDelayMillis.coerceAtLeast(0L))
        if (!sheets.anyOpen && !showLyricDetails) collapse()
    }

    val drawsController = visible || showCollapsedWhenHidden
    val sheetVisible = sheets.anyOpen
    val interactionSurfaceVisible = sheetVisible || showLyricDetails
    val overlayActive = drawsController || interactionSurfaceVisible
    DisposableEffect(overlayActive) {
        onOverlayVisibilityChanged(overlayActive)
        onDispose {
            if (overlayActive) onOverlayVisibilityChanged(false)
        }
    }
    DisposableEffect(sheetVisible) {
        onSheetVisibilityChanged(sheetVisible)
        onDispose {
            if (sheetVisible) onSheetVisibilityChanged(false)
        }
    }

    LaunchedEffect(media?.id) {
        showLyricDetails = false
    }
    LaunchedEffect(lyricCapabilityLevel) {
        if (lyricCapabilityLevel == null) showLyricDetails = false
    }

    // Keep the host transparent. The legacy Desktop overlay filled this box with an opaque
    // rectangle to back a second native window; inside NativeAmllScreen that rectangle became
    // visible around the rounded cards while AnimatedContent scaled between states.
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                fullLyricControllerTransform(
                    expanding = targetState,
                    reducedMotion = reducedMotion,
                )
            },
            label = "FullLyricControllerTransform",
        ) { expanded ->
            if (expanded) {
                FullLyricControlConsole(
                    media = media,
                    hasMedia = hasMedia,
                    isPlaying = isPlaying,
                    position = position,
                    totalDuration = totalDuration,
                    progress = progress,
                    shuffleEnabled = shuffleEnabled,
                    isFavorite = isFavorite,
                    lyricCapabilityLevel = lyricCapabilityLevel,
                    lyricSource = displayedLyricSource,
                    lyricEndpoint = displayedLyricEndpoint,
                    lyricDetailsExpanded = showLyricDetails,
                    accentPalette = accentPalette,
                    forceOpaqueSurfaces = forceOpaqueSurfaces,
                    onArtworkLoaded = extractAccent,
                    onReveal = ::reveal,
                    onCollapse = ::collapse,
                    onSeek = { targetPosition ->
                        reveal()
                        player.seekTo(targetPosition)
                    },
                    onPrevious = {
                        reveal()
                        player.seekToPrevious()
                    },
                    onPlayPause = {
                        reveal()
                        player.togglePlayPause()
                    },
                    onNext = {
                        reveal()
                        player.seekToNext()
                    },
                    onFavorite = {
                        reveal()
                        viewModel.onFavClick()
                    },
                    onQueue = {
                        reveal()
                        viewModel.onPlaylistClick()
                        sheets.openQueue()
                    },
                    onComments = {
                        reveal()
                        sheets.openComments()
                    },
                    onShuffle = {
                        reveal()
                        viewModel.onShuffleClick(!shuffleEnabled)
                    },
                    onLyricDetailsExpandedChange = { expanded ->
                        if (expanded) reveal()
                        showLyricDetails = expanded
                    },
                    showOutputVolume = showOutputVolume,
                    outputVolume = outputVolume,
                    onOutputVolumeChange = player::setVolume,
                    onOutputVolumeAdjustingChange = { adjusting ->
                        adjustingOutputVolume = adjusting
                    },
                )
            } else if (showCollapsedWhenHidden) {
                CollapsedProgressController(
                    media = media,
                    hasMedia = hasMedia,
                    isPlaying = isPlaying,
                    position = position,
                    totalDuration = totalDuration,
                    progress = progress,
                    accentPalette = accentPalette,
                    forceOpaqueSurface = forceOpaqueSurfaces,
                    onReveal = ::reveal,
                    onTogglePlayPause = { if (hasMedia) player.togglePlayPause() },
                    onPrevious = { if (hasMedia) player.seekToPrevious() },
                    onNext = { if (hasMedia) player.seekToNext() },
                    fillHostWidth = collapsedHostFillsWidth,
                    reducedMotion = reducedMotion,
                )
            }
        }

        TransportSheets(
            state = sheets,
            nowPlaying = nowPlaying,
            accentPalette = accentPalette,
            viewModel = viewModel,
            onSeekClick = { index ->
                viewModel.onSeekClick(index)
                reveal()
            },
        )
    }
}

private fun fullLyricControllerTransform(
    expanding: Boolean,
    reducedMotion: Boolean,
): ContentTransform {
    if (reducedMotion) {
        return ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = ExitTransition.None,
            sizeTransform = null,
        )
    }

    val bottomCenter = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f)
    val enterScale = if (expanding) 0.58f else 1.04f
    val exitScale = if (expanding) 1.04f else 0.58f

    return (
        fadeIn(
            animationSpec = tween(
                durationMillis = 140,
                delayMillis = if (expanding) 36 else 72,
                easing = LinearOutSlowInEasing,
            ),
        ) + scaleIn(
            initialScale = enterScale,
            transformOrigin = bottomCenter,
            animationSpec = tween(ExpressiveMotion.LongMillis, easing = FastOutSlowInEasing),
        )
        ) togetherWith (
        fadeOut(
            animationSpec = tween(
                durationMillis = if (expanding) 110 else 150,
                easing = LinearOutSlowInEasing,
            ),
        ) + scaleOut(
            targetScale = exitScale,
            transformOrigin = bottomCenter,
            animationSpec = tween(ExpressiveMotion.EmphasizedMillis, easing = FastOutSlowInEasing),
        )
        )
}

@Composable
private fun FullLyricControlConsole(
    media: MediaInfo?,
    hasMedia: Boolean,
    isPlaying: Boolean,
    position: Long,
    totalDuration: Long,
    progress: Float,
    shuffleEnabled: Boolean,
    isFavorite: Boolean,
    lyricCapabilityLevel: LyricCapabilityLevel?,
    lyricSource: LyricSource?,
    lyricEndpoint: String,
    lyricDetailsExpanded: Boolean,
    accentPalette: ContentAccentPalette,
    forceOpaqueSurfaces: Boolean,
    onArtworkLoaded: (coil3.Image) -> Unit,
    onReveal: () -> Unit,
    onCollapse: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onQueue: () -> Unit,
    onComments: () -> Unit,
    onShuffle: () -> Unit,
    onLyricDetailsExpandedChange: (Boolean) -> Unit,
    showOutputVolume: Boolean,
    outputVolume: Float,
    onOutputVolumeChange: (Float) -> Unit,
    onOutputVolumeAdjustingChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExpandedPlaybackCard(
            media = media,
            hasMedia = hasMedia,
            isPlaying = isPlaying,
            position = position,
            totalDuration = totalDuration,
            progress = progress,
            lyricCapabilityLevel = lyricCapabilityLevel,
            lyricSource = lyricSource,
            lyricEndpoint = lyricEndpoint,
            lyricDetailsExpanded = lyricDetailsExpanded,
            accentPalette = accentPalette,
            forceOpaqueSurface = forceOpaqueSurfaces,
            onArtworkLoaded = onArtworkLoaded,
            onReveal = onReveal,
            onCollapse = onCollapse,
            onSeek = onSeek,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onLyricDetailsExpandedChange = onLyricDetailsExpandedChange,
        )
        if (showOutputVolume) {
            OutputVolumeControl(
                volume = outputVolume,
                accentPalette = accentPalette,
                forceOpaqueSurface = forceOpaqueSurfaces,
                onVolumeChange = onOutputVolumeChange,
                onAdjustingChange = onOutputVolumeAdjustingChange,
            )
        }
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .height(64.dp),
            shape = CircleShape,
            color = accentPalette.quietContainer.copy(
                alpha = if (forceOpaqueSurfaces) 1f else 0.88f,
            ),
            contentColor = accentPalette.onQuietContainer,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = onFavorite,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isFavorite) {
                            accentPalette.accent
                        } else {
                            accentPalette.container.copy(alpha = 0.72f)
                        },
                        contentColor = if (isFavorite) {
                            accentPalette.onAccent
                        } else {
                            accentPalette.onContainer
                        },
                    ),
                ) {
                    Icon(
                        imageVector = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                        contentDescription = if (isFavorite) "已收藏" else "收藏",
                    )
                }
                FilledTonalIconButton(
                    onClick = onQueue,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container.copy(alpha = 0.72f),
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(AppIcons.QueueMusic, contentDescription = "播放队列")
                }
                FilledTonalIconButton(
                    onClick = onComments,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container.copy(alpha = 0.72f),
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(AppIcons.Comment, contentDescription = "评论")
                }
                FilledIconToggleButton(
                    checked = shuffleEnabled,
                    onCheckedChange = { onShuffle() },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = accentPalette.container.copy(alpha = 0.72f),
                        contentColor = accentPalette.onContainer,
                        checkedContainerColor = accentPalette.accent,
                        checkedContentColor = accentPalette.onAccent,
                    ),
                ) {
                    Icon(
                        imageVector = if (shuffleEnabled) AppIcons.ShuffleOn else AppIcons.Shuffle,
                        contentDescription = "随机播放",
                    )
                }
            }
        }
    }
}

/**
 * The output volume row of the expanded island: one slider that does nothing but volume.
 *
 * It sits between the playback card and the action pill, in the pill's quiet colours, and
 * writes straight to the player so the level is audible while the thumb moves. The icon
 * follows the level; the label states it.
 */
@Composable
private fun OutputVolumeControl(
    volume: Float,
    accentPalette: ContentAccentPalette,
    forceOpaqueSurface: Boolean,
    onVolumeChange: (Float) -> Unit,
    onAdjustingChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val adjusting = dragged || pressed
    LaunchedEffect(adjusting) {
        onAdjustingChange(adjusting)
    }
    DisposableEffect(Unit) {
        onDispose { onAdjustingChange(false) }
    }
    val level = outputVolumeLevel(volume)
    val label = formatOutputVolumePercent(volume)

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .height(56.dp),
        shape = CircleShape,
        color = accentPalette.quietContainer.copy(
            alpha = if (forceOpaqueSurface) 1f else 0.88f,
        ),
        contentColor = accentPalette.onQuietContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (level) {
                    OutputVolumeLevel.MUTED -> AppIcons.VolumeOff
                    OutputVolumeLevel.LOW -> AppIcons.VolumeDown
                    OutputVolumeLevel.HIGH -> AppIcons.VolumeUp
                },
                contentDescription = null,
                tint = accentPalette.secondaryOnQuietContainer,
                modifier = Modifier.size(20.dp),
            )
            Slider(
                value = volume.coerceIn(0f, 1f),
                onValueChange = { updated -> onVolumeChange(updated.coerceIn(0f, 1f)) },
                valueRange = 0f..1f,
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ExpressiveLayout.MinimumTouchTarget)
                    .semantics { contentDescription = "输出音量 $label" },
                colors = SliderDefaults.colors(
                    thumbColor = accentPalette.onQuietContainer,
                    activeTrackColor = accentPalette.onQuietContainer,
                    inactiveTrackColor = accentPalette.onQuietContainer.copy(alpha = 0.22f),
                ),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = accentPalette.secondaryOnQuietContainer,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 40.dp),
            )
        }
    }
}

internal enum class OutputVolumeLevel {
    MUTED,
    LOW,
    HIGH,
}

/**
 * Which of the three speaker glyphs represents [volume]. It follows the whole percentage the
 * label shows and the player persists, so a level that reads `0%` is drawn muted rather than
 * as a faint sound, and the outer wave appears from `50%`.
 */
internal fun outputVolumeLevel(volume: Float): OutputVolumeLevel {
    val percent = outputVolumePercent(volume)
    return when {
        percent == 0 -> OutputVolumeLevel.MUTED
        percent < 50 -> OutputVolumeLevel.LOW
        else -> OutputVolumeLevel.HIGH
    }
}

/** The level as the whole percentage the player persists, e.g. `72%`. */
internal fun formatOutputVolumePercent(volume: Float): String = "${outputVolumePercent(volume)}%"

private fun outputVolumePercent(volume: Float): Int =
    if (volume.isNaN()) 0 else (volume.coerceIn(0f, 1f) * 100f).roundToInt()

@Composable
private fun ExpandedPlaybackCard(
    media: MediaInfo?,
    hasMedia: Boolean,
    isPlaying: Boolean,
    position: Long,
    totalDuration: Long,
    progress: Float,
    lyricCapabilityLevel: LyricCapabilityLevel?,
    lyricSource: LyricSource?,
    lyricEndpoint: String,
    lyricDetailsExpanded: Boolean,
    accentPalette: ContentAccentPalette,
    forceOpaqueSurface: Boolean,
    onArtworkLoaded: (coil3.Image) -> Unit,
    onReveal: () -> Unit,
    onCollapse: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onLyricDetailsExpandedChange: (Boolean) -> Unit,
) {
    val seek = rememberSeekDragState(media?.id)
    val sliderValue = seek.progressFor(progress)
    val displayPosition = seek.positionFor(position, totalDuration)

    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 620.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = accentPalette.container.copy(
            alpha = if (forceOpaqueSurface) 1f else 0.88f,
        ),
        contentColor = accentPalette.onContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasMedia) {
                AsyncImage(
                    model = media?.artworkUri,
                    contentDescription = "${media?.title ?: "当前歌曲"}封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(
                            onClickLabel = "收起播放控制",
                            onClick = onCollapse,
                        ),
                    onSuccess = { state -> onArtworkLoaded(state.result.image) },
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(
                            onClickLabel = "收起播放控制",
                            onClick = onCollapse,
                        ),
                    shape = MaterialTheme.shapes.large,
                    color = accentPalette.onContainer.copy(alpha = 0.16f),
                    contentColor = accentPalette.onContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                onClickLabel = "收起播放控制",
                                onClick = onCollapse,
                            ),
                    ) {
                        Text(
                            text = media?.title?.takeIf { it.isNotBlank() } ?: "未播放",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = media?.artist?.takeIf { it.isNotBlank() } ?: "选择歌曲开始播放",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentPalette.secondaryOnContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    lyricCapabilityLevel?.let { level ->
                        LyricCapabilityBadge(
                            level = level,
                            source = lyricSource,
                            endpoint = lyricEndpoint,
                            detailsExpanded = lyricDetailsExpanded,
                            onDetailsExpandedChange = onLyricDetailsExpandedChange,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                PlaybackSeekBar(
                    state = seek,
                    progress = progress,
                    totalDuration = totalDuration,
                    enabled = hasMedia && totalDuration > 0L,
                    accentPalette = accentPalette,
                    onSeek = onSeek,
                    onInteractionStart = onReveal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val playbackTimeLabel = remember(
                        hasMedia,
                        displayPosition.coerceAtLeast(0L) / 1_000L,
                        totalDuration.coerceAtLeast(0L) / 1_000L,
                    ) {
                        if (hasMedia) {
                            "${formatPlaybackDuration(displayPosition)} / ${formatPlaybackDuration(totalDuration)}"
                        } else {
                            "0:00 / 0:00"
                        }
                    }
                    Text(
                        text = playbackTimeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentPalette.secondaryOnContainer,
                        maxLines = 1,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onPrevious,
                            enabled = hasMedia,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(AppIcons.KeyboardArrowLeft, contentDescription = "上一首")
                        }
                        FilledIconButton(
                            onClick = onPlayPause,
                            enabled = hasMedia,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accentPalette.onContainer.copy(alpha = 0.18f),
                                contentColor = accentPalette.onContainer,
                                disabledContainerColor = accentPalette.onContainer.copy(alpha = 0.08f),
                                disabledContentColor = accentPalette.onContainer.copy(alpha = 0.42f),
                            ),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                            )
                        }
                        IconButton(
                            onClick = onNext,
                            enabled = hasMedia,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(AppIcons.KeyboardArrowRight, contentDescription = "下一首")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedProgressController(
    media: MediaInfo?,
    hasMedia: Boolean,
    isPlaying: Boolean,
    position: Long,
    totalDuration: Long,
    progress: Float,
    accentPalette: ContentAccentPalette,
    forceOpaqueSurface: Boolean,
    onReveal: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    fillHostWidth: Boolean,
    reducedMotion: Boolean,
) {
    val dragThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    val dragFraction = (dragOffsetPx / dragThresholdPx).coerceIn(-1f, 1f)
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx.coerceIn(-dragThresholdPx, dragThresholdPx) * 0.30f,
        animationSpec = if (reducedMotion) snap() else spring(),
        label = "collapsedControllerDragOffset",
    )
    val animatedScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.965f
            dragOffsetPx != 0f -> 1f + abs(dragFraction) * 0.045f
            else -> 1f
        },
        animationSpec = if (reducedMotion) snap() else spring(),
        label = "collapsedControllerScale",
    )
    val swipeLabel = when {
        dragOffsetPx <= -dragThresholdPx * 0.38f -> "释放下一首"
        dragOffsetPx >= dragThresholdPx * 0.38f -> "释放上一首"
        else -> null
    }
    val swipeAlpha by animateFloatAsState(
        targetValue = abs(dragFraction).coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(ExpressiveMotion.FastMillis, easing = LinearOutSlowInEasing)
        },
        label = "collapsedControllerSwipeAlpha",
    )
    val widthModifier = if (fillHostWidth) {
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
    } else {
        Modifier
            .padding(horizontal = 24.dp)
            .widthIn(min = 220.dp, max = 420.dp)
            .fillMaxWidth(0.72f)
    }

    Surface(
        modifier = widthModifier
            .heightIn(min = ExpressiveLayout.MinimumTouchTarget)
            .graphicsLayer {
                translationX = animatedOffset
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .pointerInput(hasMedia, dragThresholdPx) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDrag = 0f
                        dragOffsetPx = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        dragOffsetPx = totalDrag.coerceIn(-dragThresholdPx * 1.25f, dragThresholdPx * 1.25f)
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            totalDrag <= -dragThresholdPx -> onNext()
                            totalDrag >= dragThresholdPx -> onPrevious()
                        }
                        dragOffsetPx = 0f
                    },
                    onDragCancel = {
                        totalDrag = 0f
                        dragOffsetPx = 0f
                    },
                )
            }
            .pointerInput(hasMedia) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onReveal() },
                    onDoubleTap = { onTogglePlayPause() },
                )
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) {
                    false
                } else if (event.key == Key.Enter || event.key == Key.Spacebar) {
                    onReveal()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (hasMedia) {
                    "${media?.title ?: "当前歌曲"}，播放控制"
                } else {
                    "当前没有播放歌曲"
                }
                onClick(label = "展开播放控制") {
                    onReveal()
                    true
                }
                if (hasMedia) {
                    customActions = listOf(
                        CustomAccessibilityAction(if (isPlaying) "暂停" else "播放") {
                            onTogglePlayPause()
                            true
                        },
                        CustomAccessibilityAction("上一首") {
                            onPrevious()
                            true
                        },
                        CustomAccessibilityAction("下一首") {
                            onNext()
                            true
                        },
                    )
                }
            },
        shape = CircleShape,
        color = accentPalette.quietContainer.copy(
            alpha = if (forceOpaqueSurface) 1f else 0.78f + swipeAlpha * 0.12f,
        ),
        contentColor = accentPalette.onQuietContainer,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val playbackTimeLabel = remember(
                    swipeLabel,
                    hasMedia,
                    position.coerceAtLeast(0L) / 1_000L,
                    totalDuration.coerceAtLeast(0L) / 1_000L,
                ) {
                    swipeLabel ?: if (hasMedia) {
                        "${formatPlaybackDuration(position)} / ${formatPlaybackDuration(totalDuration)}"
                    } else {
                        "0:00 / 0:00"
                    }
                }
                Text(
                    text = media?.title?.takeIf { it.isNotBlank() } ?: "未播放",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = playbackTimeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (swipeLabel != null) {
                        accentPalette.onQuietContainer
                    } else {
                        accentPalette.secondaryOnQuietContainer
                    },
                    modifier = Modifier.padding(start = 10.dp),
                    maxLines = 1,
                )
            }
            ExpressiveWavyProgress(
                progress = { progress },
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(),
                color = accentPalette.accent,
                trackColor = accentPalette.onQuietContainer.copy(alpha = 0.20f),
            )
        }
    }
}
