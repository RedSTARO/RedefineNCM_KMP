package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.lyric.LyricSource
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ArtworkAccent
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.lyricCapabilityLevel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    islandModifier: Modifier = Modifier,
    lyricArtworkAccent: ArtworkAccent? = null,
) {
    val nowPlaying = rememberNowPlayingUiState(player, viewModel)
    val outputVolume by player.volume.collectAsState()
    val media = nowPlaying.media
    val isPlaying = nowPlaying.isPlaying
    val position = nowPlaying.position
    val shuffleEnabled = nowPlaying.shuffleEnabled
    val lyricUiState by viewModel.lyricUiState.collectAsState()
    val lyricMediaId by viewModel.lyricMediaId.collectAsState()
    val activeLyricSource by viewModel.activeLyricSource.collectAsState()
    val activeLyricEndpoint by viewModel.activeLyricEndpoint.collectAsState()

    val android = remember { getPlatform().isAndroid }
    val playerRestoreState by viewModel.playerStatusRestoreState.collectAsState()
    val controlsScroll = rememberScrollState()
    var visible by rememberSaveable { mutableStateOf(initialExpanded) }
    var revealRequest by remember { mutableIntStateOf(0) }
    val sheets = rememberTransportSheetsState()
    var lyricDetailsMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    var islandHasFocus by remember { mutableStateOf(false) }
    val islandHasKeyboardFocus = android && islandHasFocus && LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val seekInteractionSource = remember { MutableInteractionSource() }
    val seekDragged by seekInteractionSource.collectIsDraggedAsState()
    val seekPressed by seekInteractionSource.collectIsPressedAsState()
    // A held volume thumb keeps the island open; the auto-hide timer restarts on release.
    var adjustingOutputVolume by remember { mutableStateOf(false) }
    // A mouse resting on the island keeps it open too, so it does not vanish from under the
    // pointer.
    val islandHoverSource = remember { MutableInteractionSource() }
    val islandHovered by islandHoverSource.collectIsHoveredAsState()

    val hasMedia = nowPlaying.hasMedia
    val isFavorite = nowPlaying.isFavorite
    val lyricCapabilityLevel = lyricUiState.lyricCapabilityLevel.takeIf {
        hasMedia && lyricMediaId == media?.id
    }
    val showLyricDetails = lyricDetailsMediaId != null && lyricDetailsMediaId == media?.id && lyricCapabilityLevel != null
    val displayedLyricSource = activeLyricSource.takeIf { lyricCapabilityLevel != null }
    val displayedLyricEndpoint = activeLyricEndpoint.takeIf { lyricCapabilityLevel != null }.orEmpty()
    val totalDuration = nowPlaying.totalDuration
    val progress = nowPlaying.progress

    val motion = MaterialTheme.motionScheme
    val artworkAccent = lyricArtworkAccent ?: rememberArtworkAccent(
        requestKey = media?.artworkUri,
        animationSpec = motion.slowEffectsSpec(),
        label = "fullLyricControlAccent",
    )
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

    val accessibility = LocalAccessibilityManager.current
    val delayMillis = autoHideDelayMillis.coerceAtLeast(0L).let { millis ->
        if (android) accessibility?.calculateRecommendedTimeoutMillis(
            millis, containsIcons = true, containsText = true, containsControls = true,
        ) ?: millis else millis
    }
    val pauseAutoHide = sheets.anyOpen || lyricDetailsMediaId != null || adjustingOutputVolume ||
        islandHovered || islandHasKeyboardFocus || seekDragged || seekPressed || controlsScroll.isScrollInProgress
    LaunchedEffect(visible, revealRequest, pauseAutoHide, delayMillis) {
        if (visible && !pauseAutoHide) {
            delay(delayMillis)
            collapse()
        }
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

    // A new track closes the old popup; a temporarily unknown track during restore does not.
    LaunchedEffect(media?.id, playerRestoreState is PlayerStatusRestoreState.Loading, lyricUiState) {
        if (lyricDetailsMediaId != null &&
            ((media?.id != lyricDetailsMediaId && (media != null || playerRestoreState !is PlayerStatusRestoreState.Loading)) ||
                (media?.id == lyricMediaId && lyricUiState !is LyricUiState.Loading &&
                    lyricUiState !is LyricUiState.Idle && lyricCapabilityLevel == null))
        ) {
            lyricDetailsMediaId = null
        }
    }

    // Keep the host transparent. An opaque fill in this box shows around the rounded cards
    // inside NativeAmllScreen while AnimatedContent scales between states.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val adaptive = android && (maxHeight < 420.dp || maxWidth < 360.dp)
        AnimatedContent(
            targetState = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
                .then(islandModifier)
                .hoverable(islandHoverSource)
                .then(if (android) Modifier.onFocusChanged { islandHasFocus = it.hasFocus }.focusGroup() else Modifier),
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = {
                fullLyricControllerTransform(
                    expanding = targetState,
                    reducedMotion = reducedMotion,
                    motion = motion,
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
                    canFavorite = nowPlaying.canFavorite,
                    canComment = nowPlaying.canComment,
                    lyricCapabilityLevel = lyricCapabilityLevel,
                    lyricSource = displayedLyricSource,
                    lyricEndpoint = displayedLyricEndpoint,
                    lyricDetailsExpanded = showLyricDetails,
                    accentPalette = accentPalette,
                    forceOpaqueSurfaces = forceOpaqueSurfaces,
                    compact = adaptive,
                    seekInteractionSource = seekInteractionSource,
                    modifier = if (android) Modifier.verticalScroll(controlsScroll) else Modifier,
                    onArtworkLoaded = if (lyricArtworkAccent == null) extractAccent else { _ -> },
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
                        lyricDetailsMediaId = media?.id.takeIf { expanded }
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
                    fillHostWidth = collapsedHostFillsWidth || adaptive,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun fullLyricControllerTransform(
    expanding: Boolean,
    reducedMotion: Boolean,
    motion: MotionScheme,
): ContentTransform = if (reducedMotion) {
    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
} else {
    // Stay near the resting position. The lyric surface doesn't scale or move with the island.
    ContentTransform(
        targetContentEnter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(
            initialScale = if (expanding) 0.92f else 0.98f,
            transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 1f),
            animationSpec = motion.defaultSpatialSpec(),
        ),
        initialContentExit = fadeOut(motion.fastEffectsSpec()),
        sizeTransform = SizeTransform(clip = true) { _, _ -> motion.defaultSpatialSpec() },
    )
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    canFavorite: Boolean,
    canComment: Boolean,
    lyricCapabilityLevel: LyricCapabilityLevel?,
    lyricSource: LyricSource?,
    lyricEndpoint: String,
    lyricDetailsExpanded: Boolean,
    accentPalette: ContentAccentPalette,
    forceOpaqueSurfaces: Boolean,
    compact: Boolean,
    seekInteractionSource: MutableInteractionSource,
    modifier: Modifier,
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
        modifier = modifier.fillMaxWidth(),
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
            compact = compact,
            seekInteractionSource = seekInteractionSource,
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
        // A single quiet surface for auxiliary actions. Only actual binary actions toggle;
        // queue and comments remain standard buttons and don't compete with Play.
        Surface(
            modifier = Modifier
                .padding(horizontal = if (compact) 8.dp else 16.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth(),
            shape = if (compact) MaterialTheme.shapes.large else CircleShape,
            color = accentPalette.quietContainer.copy(alpha = if (forceOpaqueSurfaces) 1f else 0.94f),
            contentColor = accentPalette.onQuietContainer,
        ) {
            FlowRow(horizontalArrangement = Arrangement.SpaceEvenly) {
                FilledIconToggleButton(
                    checked = isFavorite,
                    onCheckedChange = { onFavorite() },
                    enabled = canFavorite,
                    modifier = Modifier.size(ExpressiveLayout.MinimumTouchTarget),
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = accentPalette.onQuietContainer,
                        checkedContainerColor = accentPalette.container,
                        checkedContentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(
                        if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                        contentDescription = if (isFavorite) strings.liked else strings.like,
                    )
                }
                IconButton(onClick = onQueue, modifier = Modifier.size(ExpressiveLayout.MinimumTouchTarget)) {
                    Icon(AppIcons.QueueMusic, contentDescription = strings.queue)
                }
                IconButton(onClick = onComments, enabled = canComment, modifier = Modifier.size(ExpressiveLayout.MinimumTouchTarget)) {
                    Icon(AppIcons.Comment, contentDescription = strings.comments)
                }
                FilledIconToggleButton(
                    checked = shuffleEnabled,
                    onCheckedChange = { onShuffle() },
                    modifier = Modifier.size(ExpressiveLayout.MinimumTouchTarget),
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = accentPalette.onQuietContainer,
                        checkedContainerColor = accentPalette.container,
                        checkedContentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(if (shuffleEnabled) AppIcons.ShuffleOn else AppIcons.Shuffle, strings.shuffle)
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
            alpha = if (forceOpaqueSurface) 1f else 0.94f,
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
                    .semantics { contentDescription = strings.outputVolumePercent(label) },
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    compact: Boolean,
    seekInteractionSource: MutableInteractionSource,
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
    val displayPosition = seek.positionFor(position, totalDuration)
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
    val clock: @Composable () -> Unit = {
        Text(
            text = playbackTimeLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = accentPalette.secondaryOnContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val seekBar: @Composable () -> Unit = {
        PlaybackSeekBar(
            state = seek,
            progress = progress,
            totalDuration = totalDuration,
            enabled = hasMedia && totalDuration > 0L,
            accentPalette = accentPalette,
            onSeek = onSeek,
            onInteractionStart = onReveal,
            interactionSource = seekInteractionSource,
            modifier = Modifier.fillMaxWidth().height(48.dp)
                .semantics { contentDescription = strings.playbackPosition },
        )
    }
    val transport: @Composable () -> Unit = {
        IslandTransportButtons(hasMedia, isPlaying, accentPalette, onPrevious, onPlayPause, onNext)
    }
    val titleRow: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media?.title?.takeIf { it.isNotBlank() } ?: strings.notPlaying,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    foreignProviderName(media?.id)?.let { label ->
                        ProviderBadge(
                            label = label,
                            contentColor = accentPalette.onQuietContainer,
                            containerColor = accentPalette.quietContainer,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text(
                        text = media?.artist?.takeIf { it.isNotBlank() } ?: strings.chooseSongToPlay,
                        style = MaterialTheme.typography.bodySmall,
                        color = accentPalette.secondaryOnContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            lyricCapabilityLevel?.let { level ->
                LyricCapabilityBadge(
                    level = level,
                    source = lyricSource,
                    endpoint = lyricEndpoint,
                    detailsExpanded = lyricDetailsExpanded,
                    onDetailsExpandedChange = onLyricDetailsExpandedChange,
                    modifier = Modifier.padding(start = if (compact) 0.dp else 8.dp),
                )
            }
            IconButton(onClick = onCollapse, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.KeyboardArrowDown, contentDescription = strings.collapsePlaybackControls)
            }
        }
    }
    Surface(
        modifier = Modifier
            .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = if (compact) 4.dp else 8.dp)
            .widthIn(max = 620.dp)
            .fillMaxWidth(),
        shape = if (compact) MaterialTheme.shapes.largeIncreased else RoundedCornerShape(ExpressiveLayout.FeatureCorner),
        color = accentPalette.container.copy(alpha = if (forceOpaqueSurface) 1f else 0.94f),
        contentColor = accentPalette.onContainer,
        tonalElevation = 0.dp,
    ) {
        if (compact) {
            BoxWithConstraints(Modifier.padding(horizontal = 8.dp)) {
                val inlineTransport = maxWidth >= 312.dp
                Column {
                    titleRow()
                    if (inlineTransport) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                seekBar()
                                clock()
                            }
                            Spacer(Modifier.width(8.dp))
                            transport()
                        }
                    } else {
                        seekBar()
                        clock()
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { transport() }
                    }
                }
            }
        } else {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ExpressiveArtwork(
                    model = media?.artworkUri?.takeIf(String::isNotBlank),
                    contentDescription = media?.let { strings.coverOf(it.title) },
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(ExpressiveLayout.FeatureCorner - 8.dp),
                    containerColor = accentPalette.quietContainer,
                    contentColor = accentPalette.onQuietContainer,
                    onImageLoaded = onArtworkLoaded,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    titleRow()
                    seekBar()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f, fill = false)) { clock() }
                        transport()
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandTransportButtons(
    hasMedia: Boolean,
    isPlaying: Boolean,
    accentPalette: ContentAccentPalette,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    BoxWithConstraints {
        val useWidePlay = maxWidth >= 160.dp
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
            IconButton(onClick = onPrevious, enabled = hasMedia, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.SkipPrevious, contentDescription = strings.previous)
            }
            PlaybackPlayButton(
                isPlaying = isPlaying,
                enabled = hasMedia,
                onClick = onPlayPause,
                modifier = Modifier.size(width = if (useWidePlay) 60.dp else 48.dp, height = 48.dp),
                containerColor = accentPalette.accent,
                contentColor = accentPalette.onAccent,
            )
            IconButton(onClick = onNext, enabled = hasMedia, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.SkipNext, contentDescription = strings.next)
            }
        }
    }
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val motion = MaterialTheme.motionScheme
    val dragThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    val dragFraction = (dragOffsetPx / dragThresholdPx).coerceIn(-1f, 1f)
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx.coerceIn(-dragThresholdPx, dragThresholdPx) * 0.30f,
        animationSpec = if (reducedMotion) snap() else motion.fastSpatialSpec(),
        label = "collapsedControllerDragOffset",
    )
    val animatedScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.965f
            dragOffsetPx != 0f -> 1f + abs(dragFraction) * 0.045f
            else -> 1f
        },
        animationSpec = if (reducedMotion) snap() else motion.fastSpatialSpec(),
        label = "collapsedControllerScale",
    )
    val swipeLabel = when {
        dragOffsetPx <= -dragThresholdPx * 0.38f -> strings.releaseForNextSong
        dragOffsetPx >= dragThresholdPx * 0.38f -> strings.releaseForPreviousSong
        else -> null
    }
    val swipeAlpha by animateFloatAsState(
        targetValue = abs(dragFraction).coerceIn(0f, 1f),
        animationSpec = if (reducedMotion) snap() else motion.fastEffectsSpec(),
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
                    strings.playbackControlsFor(media?.title ?: strings.currentSong)
                } else {
                    strings.noSongPlaying
                }
                onClick(label = strings.expandPlaybackControls) {
                    onReveal()
                    true
                }
                if (hasMedia) {
                    customActions = listOf(
                        CustomAccessibilityAction(if (isPlaying) strings.pause else strings.play) {
                            onTogglePlayPause()
                            true
                        },
                        CustomAccessibilityAction(strings.previous) {
                            onPrevious()
                            true
                        },
                        CustomAccessibilityAction(strings.next) {
                            onNext()
                            true
                        },
                    )
                }
            },
        shape = CircleShape,
        color = accentPalette.quietContainer.copy(
            alpha = if (forceOpaqueSurface) 1f else 0.90f + swipeAlpha * 0.10f,
        ),
        contentColor = accentPalette.onQuietContainer,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                if (swipeLabel == null) {
                    Icon(
                        imageVector = AppIcons.UnfoldMore,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp).size(16.dp),
                    )
                }
                Text(
                    text = media?.title?.takeIf { it.isNotBlank() } ?: strings.notPlaying,
                    style = MaterialTheme.typography.labelMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = playbackTimeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    overflow = TextOverflow.Ellipsis,
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
                trackColor = accentPalette.onQuietContainer.copy(alpha = 0.24f),
                amplitude = { if (isPlaying && !reducedMotion) 1f else 0f },
            )
        }
    }
}
