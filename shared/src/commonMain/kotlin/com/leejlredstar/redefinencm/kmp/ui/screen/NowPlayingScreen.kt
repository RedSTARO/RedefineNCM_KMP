package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.themeColorFromCoilImage
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
    val defaultAccentColor = MaterialTheme.colorScheme.primaryContainer
    var rawAccentColor by remember(metadata?.artworkUri, defaultAccentColor) {
        mutableStateOf(defaultAccentColor)
    }
    val animatedAccentColor by animateColorAsState(
        targetValue = rawAccentColor,
        animationSpec = spring(),
        label = "nowPlayingAccent",
    )
    val accentPalette = contentAccentPalette(animatedAccentColor)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentPalette.pageStart,
                        accentPalette.pageMiddle,
                        accentPalette.pageEnd,
                    ),
                ),
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            SongHeroSection(
                metadata = metadata,
                accentPalette = accentPalette,
                onAccentColor = { rawAccentColor = it },
                onBack = onBack,
                modifier = Modifier.weight(0.38f),
            )

            LyricSection(
                lyricMap = lyricMap,
                lyricIndex = lyricIndex,
                accentPalette = accentPalette,
                onSeekClick = { timeMs -> viewModel.onPositionSeekClick(timeMs) },
                modifier = Modifier
                    .weight(0.32f)
                    .padding(horizontal = 16.dp),
            )

            ProgressSection(
                currentPosition = position,
                songLength = songLength,
                accentPalette = accentPalette,
                onSeekChanged = { viewModel.onPositionSeekClick(it) },
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 2.dp),
            )

            PlaybackControlSection(
                isPlaying = isPlaying,
                accentPalette = accentPalette,
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
                onOpenFullLyric = onOpenFullLyric,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }

    LaunchedEffect(showPlaylist) {
        if (showPlaylist) viewModel.onPlaylistClick()
    }

    if (showPlaylist) {
        QueueBottomSheet(
            playlist = playList,
            currentIndex = currentIndex?.toIntOrNull() ?: 0,
            accentPalette = accentPalette,
            onDismiss = { showPlaylist = false },
            onSeekClick = { viewModel.onSeekClick(it) },
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

@Composable
private fun SongHeroSection(
    metadata: com.leejlredstar.redefinencm.kmp.player.MediaInfo?,
    accentPalette: ContentAccentPalette,
    onAccentColor: (Color) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fallbackAccentColor = MaterialTheme.colorScheme.primaryContainer
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentPalette.pageStart,
                            accentPalette.pageMiddle,
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val coverSize = maxWidth.coerceAtMost(maxHeight).coerceAtMost(252.dp)
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentPalette.quietContainer.copy(alpha = 0.72f),
                    contentColor = accentPalette.onQuietContainer,
                ) {
                    Icon(
                        AppIcons.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = accentPalette.container.copy(alpha = 0.30f),
                modifier = Modifier
                    .size(coverSize)
                    .padding(4.dp),
            ) {
                AsyncImage(
                    model = metadata?.artworkUri,
                    contentDescription = "Album Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                    onSuccess = { state ->
                        // 原版 SongDetails：封面 Palette 取色驱动 hero 渐变
                        themeColorFromCoilImage(state.result.image)?.let { onAccentColor(Color(it)) }
                    },
                    onError = { onAccentColor(fallbackAccentColor) },
                )
            }
        }

        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
            Text(
                text = metadata?.title?.ifBlank { "Unknown Title" } ?: "Unknown Title",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentPalette.onQuietContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = metadata?.artist?.ifBlank { "Unknown Artist" } ?: "Unknown Artist",
                style = MaterialTheme.typography.titleMedium,
                color = accentPalette.onQuietContainer.copy(alpha = 0.72f),
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
    accentPalette: ContentAccentPalette,
    onSeekClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val lyricEntries = remember(lyricMap) { lyricMap.entries.toList() }

    var isUserScrolling by remember { mutableStateOf(false) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-center current line when not being manually scrolled
    LaunchedEffect(lyricIndex, isUserScrolling) {
        if (lyricIndex >= 0 && lyricIndex < lyricEntries.size && !isUserScrolling) {
            programmaticScroll = true
            listState.animateScrollToItem(
                index = lyricIndex,
                scrollOffset = (listState.layoutInfo.viewportSize.height / 2).coerceAtLeast(0),
            )
            programmaticScroll = false
        }
    }

    // Detect manual scroll by the user to pause auto-centering
    val currentFirst = listState.firstVisibleItemIndex
    var lastKnownFirst by remember { mutableStateOf(currentFirst) }
    LaunchedEffect(currentFirst) {
        if (currentFirst != lastKnownFirst && !programmaticScroll && lyricEntries.isNotEmpty()) {
            isUserScrolling = true
            resumeJob?.cancel()
            resumeJob = scope.launch {
                kotlinx.coroutines.delay(3_000)
                isUserScrolling = false
            }
        }
        lastKnownFirst = currentFirst
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(lyricEntries) { index, entry ->
                val isCurrent = index == lyricIndex

                val dist = kotlin.math.abs(index - lyricIndex)
                val itemAlpha = when {
                    dist == 0 -> 1f
                    dist <= 2 -> 1f - dist * 0.18f
                    else -> 0.45f
                }

                Surface(
                    onClick = {
                        entry.key?.let { time ->
                            onSeekClick(time)
                            // After seeking, wait briefly for position to propagate
                            // through player -> computeLyricIndex -> lyricIndex,
                            // then resume auto-centering.
                            resumeJob?.cancel()
                            resumeJob = scope.launch {
                                kotlinx.coroutines.delay(200)
                                isUserScrolling = false
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    color = if (isCurrent) accentPalette.container
                    else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = entry.value?.ifBlank { " " } ?: " ",
                        style = if (isCurrent) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isCurrent) accentPalette.onContainer
                        else accentPalette.onQuietContainer.copy(alpha = 0.72f),
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .alpha(itemAlpha),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
    accentPalette: ContentAccentPalette,
    onSeekChanged: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (songLength > 0) {
        currentPosition.toFloat() / songLength.toFloat()
    } else 0f

    // 拖动期间只跟踪本地值，松手时才真正 seek 一次：
    // 部分平台（桌面 JvmMediaPlayer）的 seek 会重开流+重取直链，逐帧 seek 会造成请求/卡音风暴。
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val sliderValue = if (isDragging) dragValue else progress.coerceIn(0f, 1f)
    val displayPosition = if (isDragging) (dragValue * songLength).toLong() else currentPosition

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { percent ->
                isDragging = true
                dragValue = percent
            },
            onValueChangeFinished = {
                onSeekChanged((dragValue * songLength).toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = accentPalette.accent,
                activeTrackColor = accentPalette.accent,
                inactiveTrackColor = accentPalette.quietContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(displayPosition),
                style = MaterialTheme.typography.labelMedium,
                color = accentPalette.onQuietContainer.copy(alpha = 0.74f),
            )
            Text(
                text = formatDuration(songLength),
                style = MaterialTheme.typography.labelMedium,
                color = accentPalette.onQuietContainer.copy(alpha = 0.74f),
            )
        }
    }
}

@Composable
fun PlaybackControlSection(
    isPlaying: Boolean,
    accentPalette: ContentAccentPalette,
    onPervClick: () -> Unit,
    onPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onShowPlaylistClick: () -> Unit,
    shuffleEnabled: Boolean,
    onShuffleClick: () -> Unit,
    onFavClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onOpenFullLyric: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
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
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = accentPalette.quietContainer,
                        contentColor = accentPalette.onQuietContainer,
                        checkedContainerColor = accentPalette.container,
                        checkedContentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(
                        imageVector = if (shuffleEnabled) AppIcons.ShuffleOn
                        else AppIcons.Shuffle,
                        contentDescription = "Shuffle",
                    )
                }
                FilledTonalIconButton(
                    onClick = onPervClick,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container.copy(alpha = 0.78f),
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(
                        imageVector = AppIcons.KeyboardArrowLeft,
                        contentDescription = "Previous",
                        modifier = Modifier.size(34.dp),
                    )
                }
                FilledIconButton(
                    onClick = onPauseClick,
                    modifier = Modifier.size(80.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accentPalette.accent,
                        contentColor = accentPalette.onAccent,
                    ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) AppIcons.Pause
                        else AppIcons.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(42.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container.copy(alpha = 0.78f),
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(
                        imageVector = AppIcons.KeyboardArrowRight,
                        contentDescription = "Next",
                        modifier = Modifier.size(34.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = onShowPlaylistClick,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container.copy(alpha = 0.78f),
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(
                        imageVector = AppIcons.QueueMusic,
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
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container.copy(alpha = 0.58f),
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(
                        imageVector = AppIcons.FavoriteBorder,
                        contentDescription = "Favorite",
                    )
                }
                FilledTonalIconButton(
                    onClick = onOpenFullLyric,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container.copy(alpha = 0.58f),
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(
                        imageVector = AppIcons.GraphicEq,
                        contentDescription = "Full-screen lyrics",
                    )
                }
                FilledTonalIconButton(
                    onClick = onCommentsClick,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container.copy(alpha = 0.58f),
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(
                        imageVector = AppIcons.Comment,
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
    accentPalette: ContentAccentPalette,
    onDismiss: () -> Unit,
    onSeekClick: (Int) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, playlist.size) {
        if (currentIndex >= 0 && currentIndex < playlist.size) {
            listState.scrollToItem(currentIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = "Play Queue",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = accentPalette.accent,
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
                    color = if (isCurrent) accentPalette.container
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
                            color = if (isCurrent) accentPalette.onContainer
                            else accentPalette.accent,
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
                                color = if (isCurrent) accentPalette.onContainer
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.artist.ifBlank { "Unknown" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) accentPalette.onContainer.copy(alpha = 0.72f)
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
    accentPalette: ContentAccentPalette,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = "Comments",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = accentPalette.accent,
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
                        color = accentPalette.quietContainer,
                        contentColor = accentPalette.onQuietContainer,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(
                            text = "No comments",
                            style = MaterialTheme.typography.bodyLarge,
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
                                        color = accentPalette.accent,
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
