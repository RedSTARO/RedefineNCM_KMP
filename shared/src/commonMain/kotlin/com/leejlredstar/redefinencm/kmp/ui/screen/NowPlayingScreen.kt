package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import org.koin.compose.koinInject

/**
 * Now Playing screen — the main music player view.
 * Ported from the original Android NowPlayingActivity with M3 Expressive styling.
 */
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit = {},
    onOpenFullLyric: () -> Unit = {},
    viewModel: NowPlayingViewModel = koinInject(),
) {
    val metadata by viewModel.currentMedia.collectAsState()
    val currentIndex by viewModel.currentMediaIndexInList.collectAsState()
    val lyricMap by viewModel.lyricMap.collectAsState()
    val lyricIndex by viewModel.lyricIndex.collectAsState()
    val shuffleStatus by viewModel.shuffleStatus.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val songLength by viewModel.songLength.collectAsState()
    val playList by viewModel.playList.collectAsState()
    val comments by viewModel.comments.collectAsState()

    var showPlaylist by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SongHeroSection(
            metadata = metadata,
            onBack = onBack,
            modifier = Modifier.weight(0.38f),
        )

        LyricSection(
            lyricMap = lyricMap,
            lyricIndex = lyricIndex,
            onClick = onOpenFullLyric,
            modifier = Modifier
                .weight(0.32f)
                .padding(horizontal = 16.dp),
        )

        ProgressSection(
            currentPosition = position,
            songLength = songLength,
            onSeekChanged = { viewModel.onPositionSeekClick(it) },
            modifier = Modifier.padding(horizontal = 24.dp, top = 4.dp, bottom = 2.dp),
        )

        PlaybackControlSection(
            isPlaying = isPlaying,
            onPervClick = { viewModel.onPervClick() },
            onPauseClick = { viewModel.onPauseClick() },
            onNextClick = { viewModel.onNextClick() },
            onShowPlaylistClick = { showPlaylist = !showPlaylist },
            shuffleEnabled = shuffleStatus,
            onShuffleClick = { viewModel.onShuffleClick(!shuffleStatus) },
            onFavClick = { viewModel.onFavClick() },
            onCommentsClick = {
                if (!showComments) viewModel.getComments()
                showComments = !showComments
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }

    LaunchedEffect(showPlaylist) {
        if (showPlaylist) viewModel.onPlaylistClick()
    }

    if (showPlaylist) {
        QueueBottomSheet(
            playlist = playList,
            currentIndex = currentIndex?.toIntOrNull() ?: 0,
            onDismiss = { showPlaylist = false },
            onSeekClick = { viewModel.onSeekClick(it) },
        )
    }

    if (showComments) {
        CommentBottomSheet(
            comments = comments?.hotComments?.ifEmpty { comments?.comments } ?: emptyList(),
            onDismiss = { showComments = false },
        )
    }
}

@Composable
private fun SongHeroSection(
    metadata: com.leejlredstar.redefinencm.kmp.player.MediaInfo?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultHeroColor = MaterialTheme.colorScheme.primaryContainer
    var themeColor by remember { mutableStateOf(defaultHeroColor) }
    val heroColor by animateColorAsState(
        targetValue = themeColor,
        animationSpec = spring(),
        label = "heroColor",
    )

    Column(modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            heroColor,
                            heroColor.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            AsyncImage(
                model = metadata?.artworkUri,
                contentDescription = "Album Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(220.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, top = 12.dp)) {
            Text(
                text = metadata?.title?.ifBlank { "Unknown Title" } ?: "Unknown Title",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = metadata?.artist?.ifBlank { "Unknown Artist" } ?: "Unknown Artist",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}

@Composable
fun LyricSection(
    lyricMap: LinkedHashMap<Long?, String?>,
    lyricIndex: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val lyrics = lyricMap.values.toList()

    LaunchedEffect(lyricIndex) {
        if (lyricIndex >= 0 && lyricIndex < lyrics.size) {
            listState.animateScrollToItem(lyricIndex)
        }
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isCurrent = index == lyricIndex
                Surface(
                    shape = CircleShape,
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = line?.ifBlank { " " } ?: " ",
                        style = if (isCurrent) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .alpha(if (isCurrent) 1f else 0.68f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressSection(
    currentPosition: Long,
    songLength: Long,
    onSeekChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (songLength > 0) {
        currentPosition.toFloat() / songLength.toFloat()
    } else 0f

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { percent ->
                onSeekChanged((percent * songLength).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(songLength),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PlaybackControlSection(
    isPlaying: Boolean,
    onPervClick: () -> Unit,
    onPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onShowPlaylistClick: () -> Unit,
    shuffleEnabled: Boolean,
    onShuffleClick: () -> Unit,
    onFavClick: () -> Unit,
    onCommentsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconToggleButton(
                    checked = shuffleEnabled,
                    onCheckedChange = { onShuffleClick() },
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        imageVector = if (shuffleEnabled) Icons.Default.ShuffleOn
                        else Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                    )
                }
                FilledTonalIconButton(
                    onClick = onPervClick,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous",
                        modifier = Modifier.size(34.dp),
                    )
                }
                FilledIconButton(
                    onClick = onPauseClick,
                    modifier = Modifier.size(80.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(42.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next",
                        modifier = Modifier.size(34.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = onShowPlaylistClick,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onFavClick,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                    )
                }
                FilledTonalIconButton(
                    onClick = onCommentsClick,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = "Comments",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    playlist: List<com.leejlredstar.redefinencm.kmp.player.MediaInfo>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSeekClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, playlist.size) {
        if (currentIndex >= 0 && currentIndex < playlist.size) {
            listState.scrollToItem(currentIndex)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Play Queue",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = "${playlist.size} songs",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(playlist) { index, item ->
                val isCurrent = index == currentIndex
                Surface(
                    onClick = { onSeekClick(index); onDismiss() },
                    shape = connectedListItemShape(index, playlist.size),
                    color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 1.5.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(36.dp),
                        )
                        AsyncImage(
                            model = item.artworkUri,
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title.ifBlank { "Unknown" },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                            Text(
                                text = item.artist.ifBlank { "Unknown" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    comments: List<com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusicComments>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Comments",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (comments.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(
                            text = "No comments",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
            itemsIndexed(comments) { index, comment ->
                Surface(
                    shape = connectedListItemShape(index, comments.size),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 1.5.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        AsyncImage(
                            model = comment.user.avatarUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = comment.user.nickname,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (comment.likedCount > 0) {
                                    Text(
                                        text = comment.likedCount.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                text = comment.content,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                text = comment.timeStr,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
