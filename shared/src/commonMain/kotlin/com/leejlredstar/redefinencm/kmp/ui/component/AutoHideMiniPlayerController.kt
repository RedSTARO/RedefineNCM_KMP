package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.screen.CommentBottomSheet
import com.leejlredstar.redefinencm.kmp.ui.screen.QueueBottomSheet
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.themeColorFromCoilImage
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun AutoHideMiniPlayerController(
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
) {
    val media by player.currentMedia.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    val playList by viewModel.playList.collectAsState()
    val currentIndex by viewModel.currentMediaIndexInList.collectAsState()
    val shuffleEnabled by viewModel.shuffleStatus.collectAsState()
    val comments by viewModel.comments.collectAsState()

    var visible by remember { mutableStateOf(true) }
    var revealRequest by remember { mutableIntStateOf(0) }
    var showQueue by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    val hasMedia = media != null
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

    fun reveal() {
        revealRequest += 1
    }

    LaunchedEffect(revealRequest) {
        visible = true
        delay(3_600)
        visible = false
    }

    LaunchedEffect(showComments, media?.id) {
        if (showComments) viewModel.getComments()
    }

    Box(modifier = modifier.fillMaxSize()) {
        ControllerAccentSourceImage(
            sourceUrl = media?.artworkUri,
            onAccentColor = { rawAccentColor = it },
        )

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            enter = slideInVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 2 },
            ) + fadeIn(animationSpec = tween(160, easing = LinearOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ),
            exit = slideOutVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 2 },
            ) + fadeOut(animationSpec = tween(140, easing = LinearOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                ),
        ) {
            FullLyricControlConsole(
                media = media,
                hasMedia = hasMedia,
                isPlaying = isPlaying,
                position = position,
                totalDuration = totalDuration,
                progress = progress,
                shuffleEnabled = shuffleEnabled,
                accentPalette = accentPalette,
                onReveal = ::reveal,
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
        }

        AnimatedVisibility(
            visible = !visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
            enter = fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                ),
            exit = fadeOut(animationSpec = tween(120, easing = LinearOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.94f,
                    animationSpec = tween(140, easing = FastOutSlowInEasing),
                ),
        ) {
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

        if (showQueue) {
            QueueBottomSheet(
                playlist = playList,
                currentIndex = currentIndex?.toIntOrNull() ?: 0,
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
                accentPalette = accentPalette,
                onDismiss = { showComments = false },
            )
        }
    }
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
    accentPalette: ContentAccentPalette,
    onReveal: () -> Unit,
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
            onReveal = onReveal,
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
                .height(58.dp),
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
                        containerColor = accentPalette.container.copy(alpha = 0.72f),
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(AppIcons.FavoriteBorder, contentDescription = "收藏")
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
    onReveal: () -> Unit,
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
        onClick = onReveal,
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
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.large),
                )
            } else {
                Surface(
                    modifier = Modifier.size(72.dp),
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
                Text(
                    text = media?.title?.takeIf { it.isNotBlank() } ?: "Not playing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = media?.artist?.takeIf { it.isNotBlank() } ?: "No playback yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentPalette.onContainer.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Slider(
                    value = sliderValue.coerceIn(0f, 1f),
                    onValueChange = { percent ->
                        isDragging = true
                        dragValue = percent.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        if (totalDuration > 0L) {
                            onSeek((dragValue * totalDuration).toLong().coerceIn(0L, totalDuration))
                        }
                        isDragging = false
                    },
                    enabled = hasMedia && totalDuration > 0L,
                    colors = SliderDefaults.colors(
                        thumbColor = accentPalette.onContainer,
                        activeTrackColor = accentPalette.onContainer,
                        inactiveTrackColor = accentPalette.onContainer.copy(alpha = 0.22f),
                        disabledThumbColor = accentPalette.onContainer.copy(alpha = 0.38f),
                        disabledActiveTrackColor = accentPalette.onContainer.copy(alpha = 0.22f),
                        disabledInactiveTrackColor = accentPalette.onContainer.copy(alpha = 0.12f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (hasMedia) {
                            "${formatControllerDuration(displayPosition)} / ${formatControllerDuration(totalDuration)}"
                        } else {
                            "0:00 / 0:00"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accentPalette.onContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onPrevious,
                            enabled = hasMedia,
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(AppIcons.KeyboardArrowLeft, contentDescription = "上一首")
                        }
                        FilledIconButton(
                            onClick = onPlayPause,
                            enabled = hasMedia,
                            modifier = Modifier.size(34.dp),
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
                            modifier = Modifier.size(30.dp),
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
    onReveal: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val dragThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }

    Surface(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .widthIn(min = 220.dp, max = 420.dp)
            .fillMaxWidth(0.72f)
            .pointerInput(hasMedia, dragThresholdPx) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            totalDrag <= -dragThresholdPx -> onNext()
                            totalDrag >= dragThresholdPx -> onPrevious()
                        }
                    },
                    onDragCancel = { totalDrag = 0f },
                )
            }
            .pointerInput(hasMedia) {
                detectTapGestures(
                    onTap = { onReveal() },
                    onDoubleTap = { onTogglePlayPause() },
                )
            },
        shape = CircleShape,
        color = accentPalette.quietContainer.copy(alpha = 0.78f),
        contentColor = accentPalette.onQuietContainer,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = media?.title?.takeIf { it.isNotBlank() } ?: "Not playing",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (hasMedia) {
                        "${formatControllerDuration(position)} / ${formatControllerDuration(totalDuration)}"
                    } else {
                        "0:00 / 0:00"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accentPalette.onQuietContainer.copy(alpha = if (isPlaying) 0.82f else 0.58f),
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

@Composable
private fun ControllerAccentSourceImage(
    sourceUrl: String?,
    onAccentColor: (Color) -> Unit,
) {
    if (sourceUrl.isNullOrBlank()) return
    AsyncImage(
        model = sourceUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(1.dp).alpha(0f),
        onSuccess = { state ->
            themeColorFromCoilImage(state.result.image)?.let { onAccentColor(Color(it)) }
        },
    )
}

private fun formatControllerDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
