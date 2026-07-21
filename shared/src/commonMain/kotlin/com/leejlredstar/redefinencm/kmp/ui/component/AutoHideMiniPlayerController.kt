package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.abs

@Composable
fun AutoHideMiniPlayerController(
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = true,
    showCollapsedWhenHidden: Boolean = true,
    externalRevealRequest: Int = 0,
    onOverlayVisibilityChanged: (Boolean) -> Unit = {},
    onSheetVisibilityChanged: (Boolean) -> Unit = {},
    onExpandedChanged: (Boolean) -> Unit = {},
    viewModel: NowPlayingViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
) {
    val media by player.currentMedia.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    val queueSnapshot by viewModel.queueSnapshot.collectAsState()
    val playList = queueSnapshot.items
    val currentIndex = queueSnapshot.currentIndex
    val shuffleEnabled = queueSnapshot.shuffleEnabled
    val comments by viewModel.comments.collectAsState()
    val commentsLoading by viewModel.commentsLoading.collectAsState()
    val commentsLoadError by viewModel.commentsLoadError.collectAsState()
    val commentsFromCache by viewModel.commentsFromCache.collectAsState()
    val favoriteState by viewModel.favoriteUiState.collectAsState()

    var visible by remember { mutableStateOf(initialExpanded) }
    var revealRequest by remember { mutableIntStateOf(0) }
    var showQueue by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    val hasMedia = media != null
    val isFavorite = favoriteState.mediaId == media?.id && favoriteState.isLiked
    val totalDuration = duration
        .takeIf { it > 0L }
        ?: media?.duration?.takeIf { it > 0L }
        ?: 0L
    val progress = if (totalDuration > 0L) {
        (position.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val defaultAccentColor = MaterialTheme.colorScheme.primaryContainer
    var rawAccentColor by remember(media?.artworkUri, defaultAccentColor) {
        mutableStateOf(defaultAccentColor)
    }
    val accentColor by animateColorAsState(
        targetValue = rawAccentColor,
        animationSpec = spring(),
        label = "fullLyricControlAccent",
    )
    val accentPalette = contentAccentPalette(accentColor)
    val extractAccent = rememberThemeColorExtractor(media?.artworkUri) { rawAccentColor = it }

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

    LaunchedEffect(visible, revealRequest, showQueue, showComments) {
        if (!visible || showQueue || showComments) return@LaunchedEffect
        delay(3_600)
        if (!showQueue && !showComments) collapse()
    }

    val drawsController = visible || showCollapsedWhenHidden
    val sheetVisible = showQueue || showComments
    val overlayActive = drawsController || sheetVisible
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

    LaunchedEffect(showComments, media?.id) {
        if (showComments) viewModel.getComments()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter,
            transitionSpec = { fullLyricControllerTransform(expanding = targetState) },
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
                    accentPalette = accentPalette,
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
                        showQueue = true
                    },
                    onComments = {
                        reveal()
                        showComments = true
                    },
                    onShuffle = {
                        reveal()
                        viewModel.onShuffleClick(!shuffleEnabled)
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
                    onReveal = ::reveal,
                    onTogglePlayPause = { if (hasMedia) player.togglePlayPause() },
                    onPrevious = { if (hasMedia) player.seekToPrevious() },
                    onNext = { if (hasMedia) player.seekToNext() },
                )
            }
        }

        if (showQueue) {
            QueueBottomSheet(
                playlist = playList,
                currentIndex = currentIndex,
                accentPalette = accentPalette,
                onDismiss = { showQueue = false },
                onSeekClick = { index ->
                    viewModel.onSeekClick(index)
                    reveal()
                },
            )
        }

        if (showComments) {
            CommentBottomSheet(
                comments = comments?.hotComments?.ifEmpty { comments?.comments } ?: emptyList(),
                hasLoadedData = comments != null,
                accentPalette = accentPalette,
                onDismiss = { showComments = false },
                isLoading = commentsLoading,
                isFromCache = commentsFromCache,
                errorMessage = commentsLoadError,
                onRetry = viewModel::getComments,
            )
        }
    }
}

private fun fullLyricControllerTransform(expanding: Boolean): ContentTransform {
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
    accentPalette: ContentAccentPalette,
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
            accentPalette = accentPalette,
            onArtworkLoaded = onArtworkLoaded,
            onReveal = onReveal,
            onCollapse = onCollapse,
            onSeek = onSeek,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
        )
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .height(64.dp),
            shape = CircleShape,
            color = accentPalette.quietContainer.copy(alpha = 0.88f),
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

@Composable
private fun ExpandedPlaybackCard(
    media: MediaInfo?,
    hasMedia: Boolean,
    isPlaying: Boolean,
    position: Long,
    totalDuration: Long,
    progress: Float,
    accentPalette: ContentAccentPalette,
    onArtworkLoaded: (coil3.Image) -> Unit,
    onReveal: () -> Unit,
    onCollapse: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    var isDragging by remember(media?.id) { mutableStateOf(false) }
    var dragValue by remember(media?.id) { mutableStateOf(progress) }
    val sliderValue = if (isDragging) dragValue else progress
    val displayPosition = if (isDragging) {
        (dragValue * totalDuration).toLong()
    } else {
        position
    }

    Surface(
                        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 620.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = accentPalette.container.copy(alpha = 0.88f),
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                PlaybackSeekBar(
                    value = sliderValue.coerceIn(0f, 1f),
                    enabled = hasMedia && totalDuration > 0L,
                    accentPalette = accentPalette,
                    onInteractionStart = onReveal,
                    onPreview = { percent ->
                        isDragging = true
                        dragValue = percent.coerceIn(0f, 1f)
                    },
                    onCommit = { percent ->
                        dragValue = percent.coerceIn(0f, 1f)
                        if (totalDuration > 0L) {
                            onSeek((dragValue * totalDuration).toLong().coerceIn(0L, totalDuration))
                        }
                        isDragging = false
                    },
                    onCancel = {
                        dragValue = if (totalDuration > 0L) {
                            (position.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        isDragging = false
                    },
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
internal fun PlaybackSeekBar(
    value: Float,
    enabled: Boolean,
    accentPalette: ContentAccentPalette,
    onInteractionStart: () -> Unit,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingValue by remember { mutableStateOf(value.coerceIn(0f, 1f)) }
    var interactionActive by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!interactionActive) pendingValue = value.coerceIn(0f, 1f)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (interactionActive) onCancel()
        }
    }

    Slider(
        value = pendingValue.coerceIn(0f, 1f),
        onValueChange = { updated ->
            if (!interactionActive) {
                interactionActive = true
                onInteractionStart()
            }
            pendingValue = updated.coerceIn(0f, 1f)
            onPreview(pendingValue)
        },
        onValueChangeFinished = {
            if (interactionActive) {
                onCommit(pendingValue)
                interactionActive = false
            }
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

@Composable
private fun CollapsedProgressController(
    media: MediaInfo?,
    hasMedia: Boolean,
    isPlaying: Boolean,
    position: Long,
    totalDuration: Long,
    progress: Float,
    accentPalette: ContentAccentPalette,
    onReveal: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val dragThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    val dragFraction = (dragOffsetPx / dragThresholdPx).coerceIn(-1f, 1f)
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx.coerceIn(-dragThresholdPx, dragThresholdPx) * 0.30f,
        animationSpec = spring(),
        label = "collapsedControllerDragOffset",
    )
    val animatedScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.965f
            dragOffsetPx != 0f -> 1f + abs(dragFraction) * 0.045f
            else -> 1f
        },
        animationSpec = spring(),
        label = "collapsedControllerScale",
    )
    val swipeLabel = when {
        dragOffsetPx <= -dragThresholdPx * 0.38f -> "释放下一首"
        dragOffsetPx >= dragThresholdPx * 0.38f -> "释放上一首"
        else -> null
    }
    val swipeAlpha by animateFloatAsState(
        targetValue = abs(dragFraction).coerceIn(0f, 1f),
        animationSpec = tween(ExpressiveMotion.FastMillis, easing = LinearOutSlowInEasing),
        label = "collapsedControllerSwipeAlpha",
    )

    Surface(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(min = 220.dp, max = 420.dp)
            .fillMaxWidth(0.72f)
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
        color = accentPalette.quietContainer.copy(alpha = 0.78f + swipeAlpha * 0.12f),
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
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = accentPalette.accent,
                trackColor = accentPalette.onQuietContainer.copy(alpha = 0.20f),
            )
        }
    }
}

internal fun formatPlaybackDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
