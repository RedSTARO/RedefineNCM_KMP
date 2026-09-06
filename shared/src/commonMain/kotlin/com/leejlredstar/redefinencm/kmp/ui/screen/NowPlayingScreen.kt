package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarColors
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.CommentBottomSheet
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.NowPlayingUiState
import com.leejlredstar.redefinencm.kmp.ui.component.QueueBottomSheet
import com.leejlredstar.redefinencm.kmp.ui.component.rememberNowPlayingUiState
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import org.koin.compose.koinInject

/**
 * The Now Playing entry page — what the mini player, the desktop rail and every OS
 * "now playing" request open first.
 *
 * It is the Apple Music layout in Material 3 Expressive clothing: one large artwork whose
 * frame morphs while held and settles smaller while paused, a Black headline, a wavy progress
 * track that only ripples while audio is actually moving, a wide play/pause toggle that
 * morphs between round and square, and a floating toolbar whose action button is the pair of
 * quotation marks that opens the AMLL lyric page. Nothing here renders lyrics; that stays the
 * job of `AmllPlayerScreen`, which this page pushes on top of itself.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onOpenLyrics: () -> Unit,
    viewModel: NowPlayingViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
) {
    val nowPlaying = rememberNowPlayingUiState(player, viewModel)
    val media = nowPlaying.media
    val reducedMotion = rememberReducedMotionEnabled()
    val motionScheme = MaterialTheme.motionScheme

    val defaultAccent = MaterialTheme.colorScheme.primaryContainer
    var rawAccent by remember(media?.artworkUri, defaultAccent) { mutableStateOf(defaultAccent) }
    val accent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = if (reducedMotion) snap() else motionScheme.slowEffectsSpec(),
        label = "nowPlayingAccent",
    )
    val palette = contentAccentPalette(accent)
    val extractAccent = rememberThemeColorExtractor(media?.artworkUri) { rawAccent = it }

    var showQueue by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    LaunchedEffect(showComments, media?.id) {
        if (showComments) viewModel.getComments()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.pageStart, palette.pageMiddle, palette.pageEnd),
                ),
            )
            .semantics { contentDescription = "正在播放" },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = ExpressiveLayout.PageHorizontalPadding),
        ) {
            // Landscape splits into artwork | controls; short windows (landscape phones, small
            // desktop windows) also drop one size on the transport so nothing scrolls.
            val viewportWidth = maxWidth
            val viewportHeight = maxHeight
            val twoColumns = viewportWidth > viewportHeight && viewportWidth >= 600.dp
            val shortHeight = viewportHeight < 600.dp
            val transportHeight = if (shortHeight) 96.dp else 136.dp
            val artwork: @Composable (Modifier) -> Unit = { modifier ->
                NowPlayingArtwork(
                    media = media,
                    isPlaying = nowPlaying.isPlaying,
                    reducedMotion = reducedMotion,
                    onArtworkLoaded = extractAccent,
                    onToggle = { if (nowPlaying.hasMedia) player.togglePlayPause() },
                    modifier = modifier,
                )
            }
            val controls: @Composable () -> Unit = {
                NowPlayingTitle(
                    media = media,
                    isFavorite = nowPlaying.isFavorite,
                    palette = palette,
                    onFavorite = viewModel::onFavClick,
                )
                Spacer(Modifier.height(20.dp))
                NowPlayingProgress(
                    nowPlaying = nowPlaying,
                    palette = palette,
                    reducedMotion = reducedMotion,
                    onSeek = player::seekTo,
                )
                Spacer(Modifier.height(20.dp))
                NowPlayingTransport(
                    isPlaying = nowPlaying.isPlaying,
                    hasMedia = nowPlaying.hasMedia,
                    compact = shortHeight,
                    palette = palette,
                    onPrevious = player::seekToPrevious,
                    onPlayPause = player::togglePlayPause,
                    onNext = player::seekToNext,
                )
            }
            val toolbar: @Composable () -> Unit = {
                NowPlayingToolbar(
                    shuffleEnabled = nowPlaying.shuffleEnabled,
                    hasMedia = nowPlaying.hasMedia,
                    palette = palette,
                    onShuffle = { enabled -> viewModel.onShuffleClick(enabled) },
                    onQueue = {
                        viewModel.onPlaylistClick()
                        showQueue = true
                    },
                    onComments = { showComments = true },
                    onLyrics = onOpenLyrics,
                )
            }

            if (twoColumns) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        artwork(Modifier.widthIn(max = 520.dp).fillMaxWidth())
                    }
                    Spacer(Modifier.width(32.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(end = ExpressiveLayout.PageHorizontalPadding),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        controls()
                        Spacer(Modifier.height(28.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            toolbar()
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Everything below the artwork has a fixed height; the artwork takes what is
                    // left, so a short window shrinks the cover instead of pushing controls off.
                    val fixedHeight = 64.dp + 80.dp + 20.dp + 60.dp + 20.dp +
                        transportHeight + 28.dp + 64.dp + ExpressiveLayout.PageVerticalPadding
                    val artworkSize =
                        minOf(420.dp, viewportWidth - 24.dp, viewportHeight - fixedHeight)
                            .coerceAtLeast(96.dp)
                    Spacer(Modifier.height(64.dp))
                    Spacer(Modifier.weight(1f))
                    artwork(Modifier.size(artworkSize))
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                        controls()
                    }
                    Spacer(Modifier.height(28.dp))
                    toolbar()
                    Spacer(Modifier.height(ExpressiveLayout.PageVerticalPadding))
                }
            }

            NowPlayingTopBar(
                palette = palette,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }

    if (showQueue) {
        QueueBottomSheet(
            playlist = nowPlaying.playList,
            currentIndex = nowPlaying.currentIndex,
            accentPalette = palette,
            onDismiss = { showQueue = false },
            onSeekClick = viewModel::onSeekClick,
        )
    }
    if (showComments) {
        CommentBottomSheet(
            comments = nowPlaying.comments?.hotComments?.ifEmpty { nowPlaying.comments?.comments }
                ?: emptyList(),
            hasLoadedData = nowPlaying.comments != null,
            accentPalette = palette,
            onDismiss = { showComments = false },
            isLoading = nowPlaying.commentsLoading,
            isFromCache = nowPlaying.commentsFromCache,
            errorMessage = nowPlaying.commentsLoadError,
            onRetry = viewModel::getComments,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingTopBar(
    palette: ContentAccentPalette,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier
                .size(IconButtonDefaults.mediumContainerSize())
                .semantics { contentDescription = "收起播放页" },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = palette.container.copy(alpha = 0.72f),
                contentColor = palette.onContainer,
            ),
        ) {
            Icon(
                imageVector = AppIcons.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "正在播放",
            style = MaterialTheme.typography.labelLarge,
            color = palette.secondaryOnPageStart,
        )
        Spacer(Modifier.weight(1f))
        // Keeps the label centred against the leading button.
        Spacer(Modifier.size(IconButtonDefaults.mediumContainerSize()))
    }
}

/**
 * The artwork. Held: the frame blooms into a cookie (`ExpressiveArtwork`'s press morph).
 * Paused: it settles to 86%, Apple Music's cue that nothing is moving.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingArtwork(
    media: MediaInfo?,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    onArtworkLoaded: (coil3.Image) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val restingScale = if (isPlaying || media == null) 1f else 0.86f
    val scale by animateFloatAsState(
        targetValue = restingScale,
        animationSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "nowPlayingArtworkScale",
    )
    val frameShape = MaterialTheme.shapes.extraLarge
    ExpressiveArtwork(
        model = media?.artworkUri?.takeIf(String::isNotBlank),
        contentDescription = media?.title?.let { "$it 的封面" },
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .dropShadow(
                shape = frameShape,
                shadow = Shadow(
                    radius = 36.dp,
                    color = Color.Black,
                    spread = 0.dp,
                    offset = DpOffset(x = 0.dp, y = 18.dp),
                    alpha = 0.32f,
                ),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = media != null,
                onClickLabel = if (isPlaying) "暂停" else "播放",
                onClick = onToggle,
            ),
        shape = frameShape,
        pressInteractionSource = interactionSource,
        onImageLoaded = onArtworkLoaded,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingTitle(
    media: MediaInfo?,
    isFavorite: Boolean,
    palette: ContentAccentPalette,
    onFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = media?.title ?: "未在播放",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = palette.onPageMiddle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = media?.artist?.takeIf(String::isNotBlank) ?: "从任意列表选一首歌开始",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.secondaryOnPageMiddle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(),
            )
        }
        Spacer(Modifier.width(12.dp))
        FilledIconToggleButton(
            checked = isFavorite,
            onCheckedChange = { onFavorite() },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            enabled = media != null,
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = palette.container.copy(alpha = 0.72f),
                contentColor = palette.onContainer,
                checkedContainerColor = palette.accent,
                checkedContentColor = palette.onAccent,
            ),
        ) {
            Icon(
                imageVector = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                contentDescription = if (isFavorite) "已收藏" else "收藏",
                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
            )
        }
    }
}

/**
 * A seekable slider whose track is the expressive wavy indicator: it ripples while audio is
 * moving and lies flat while paused or being dragged, so the wave itself reads as playback.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingProgress(
    nowPlaying: NowPlayingUiState,
    palette: ContentAccentPalette,
    reducedMotion: Boolean,
    onSeek: (Long) -> Unit,
) {
    val mediaId = nowPlaying.media?.id
    var dragFraction by remember(mediaId) { mutableStateOf<Float?>(null) }
    val totalDuration = nowPlaying.totalDuration
    val seekable = nowPlaying.hasMedia && totalDuration > 0L
    val sliderValue = dragFraction ?: nowPlaying.progress
    val displayPosition = dragFraction?.let { (it * totalDuration).toLong() }
        ?: nowPlaying.safePosition
    val amplitude by animateFloatAsState(
        targetValue = if (nowPlaying.isPlaying && dragFraction == null) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "nowPlayingWaveAmplitude",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val thumbColors = SliderDefaults.colors(
        thumbColor = palette.onPageMiddle,
        disabledThumbColor = palette.secondaryOnPageMiddle,
    )

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { fraction -> onSeek((fraction * totalDuration).toLong()) }
                dragFraction = null
            },
            enabled = seekable,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = "播放进度" },
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = thumbColors,
                    enabled = seekable,
                )
            },
            track = { state ->
                LinearWavyProgressIndicator(
                    progress = { state.value },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.accent,
                    trackColor = palette.onPageMiddle.copy(alpha = 0.16f),
                    amplitude = { amplitude },
                )
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            val clockStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
            Text(
                text = formatNowPlayingClock(displayPosition),
                style = clockStyle,
                color = palette.secondaryOnPageMiddle,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatNowPlayingClock(totalDuration),
                style = clockStyle,
                color = palette.secondaryOnPageMiddle,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingTransport(
    isPlaying: Boolean,
    hasMedia: Boolean,
    compact: Boolean,
    palette: ContentAccentPalette,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val tonalColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = palette.container.copy(alpha = 0.72f),
        contentColor = palette.onContainer,
        disabledContainerColor = palette.container.copy(alpha = 0.32f),
        disabledContentColor = palette.onContainer.copy(alpha = 0.38f),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val skipSize = if (compact) {
            IconButtonDefaults.mediumContainerSize()
        } else {
            IconButtonDefaults.largeContainerSize()
        }
        val skipIconSize = if (compact) {
            IconButtonDefaults.mediumIconSize
        } else {
            IconButtonDefaults.largeIconSize
        }
        val playSize = if (compact) {
            IconButtonDefaults.largeContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)
        } else {
            IconButtonDefaults.extraLargeContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)
        }
        val playIconSize = if (compact) {
            IconButtonDefaults.largeIconSize
        } else {
            IconButtonDefaults.extraLargeIconSize
        }
        FilledTonalIconButton(
            onClick = onPrevious,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(skipSize),
            enabled = hasMedia,
            colors = tonalColors,
        ) {
            Icon(
                imageVector = AppIcons.SkipPrevious,
                contentDescription = "上一首",
                modifier = Modifier.size(skipIconSize),
            )
        }
        // The wide extra-large toggle: round while paused, squarer while playing, and it morphs
        // through the press. This is the M3 Expressive media play/pause affordance.
        FilledIconToggleButton(
            checked = isPlaying,
            onCheckedChange = { onPlayPause() },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(playSize),
            enabled = hasMedia,
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                checkedContainerColor = palette.accent,
                checkedContentColor = palette.onAccent,
                disabledContainerColor = palette.accent.copy(alpha = 0.32f),
                disabledContentColor = palette.onAccent.copy(alpha = 0.38f),
            ),
        ) {
            Icon(
                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(playIconSize),
            )
        }
        FilledTonalIconButton(
            onClick = onNext,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(skipSize),
            enabled = hasMedia,
            colors = tonalColors,
        ) {
            Icon(
                imageVector = AppIcons.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(skipIconSize),
            )
        }
    }
}

/**
 * The floating toolbar. Its action button is the quotation-mark lyrics button, the same
 * place Apple Music keeps its lyrics control; the toolbar body carries the queue-level
 * controls that do not belong next to the transport.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingToolbar(
    shuffleEnabled: Boolean,
    hasMedia: Boolean,
    palette: ContentAccentPalette,
    onShuffle: (Boolean) -> Unit,
    onQueue: () -> Unit,
    onComments: () -> Unit,
    onLyrics: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onLyrics,
                modifier = Modifier.semantics { contentDescription = "歌词" },
            ) {
                Icon(imageVector = AppIcons.FormatQuote, contentDescription = null)
            }
        },
        colors = FloatingToolbarColors(
            toolbarContainerColor = palette.quietContainer,
            toolbarContentColor = palette.onQuietContainer,
            fabContainerColor = palette.accent,
            fabContentColor = palette.onAccent,
        ),
        content = {
            FilledIconToggleButton(
                checked = shuffleEnabled,
                onCheckedChange = onShuffle,
                shapes = IconButtonDefaults.toggleableShapes(),
                enabled = hasMedia,
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = palette.onQuietContainer,
                    checkedContainerColor = palette.accent,
                    checkedContentColor = palette.onAccent,
                ),
            ) {
                Icon(
                    imageVector = if (shuffleEnabled) AppIcons.ShuffleOn else AppIcons.Shuffle,
                    contentDescription = "随机播放",
                )
            }
            IconButton(
                onClick = onQueue,
                shapes = IconButtonDefaults.shapes(),
                enabled = hasMedia,
            ) {
                Icon(imageVector = AppIcons.QueueMusic, contentDescription = "播放队列")
            }
            IconButton(
                onClick = onComments,
                shapes = IconButtonDefaults.shapes(),
                enabled = hasMedia,
            ) {
                Icon(imageVector = AppIcons.Comment, contentDescription = "评论")
            }
        },
    )
}

/** `m:ss`, or `h:mm:ss` past an hour — the clock format the other transport surfaces use. */
internal fun formatNowPlayingClock(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val paddedSeconds = seconds.toString().padStart(2, '0')
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:$paddedSeconds"
    } else {
        "$minutes:$paddedSeconds"
    }
}
