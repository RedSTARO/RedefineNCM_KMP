package com.leejlredstar.redefinencm.kmp.ui.screen

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
import androidx.compose.runtime.getValue
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
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.NativeDynamicCoverLayer
import com.leejlredstar.redefinencm.kmp.ui.component.NowPlayingUiState
import com.leejlredstar.redefinencm.kmp.ui.component.TransportSheets
import com.leejlredstar.redefinencm.kmp.ui.component.formatPlaybackClock
import com.leejlredstar.redefinencm.kmp.ui.component.rememberNowPlayingUiState
import com.leejlredstar.redefinencm.kmp.ui.component.rememberSeekDragState
import com.leejlredstar.redefinencm.kmp.ui.component.rememberTransportSheetsState
import com.leejlredstar.redefinencm.kmp.ui.component.scopedToMedia
import com.leejlredstar.redefinencm.kmp.ui.component.shouldRequestSongWikiOnOpen
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import org.koin.compose.koinInject
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsSheet
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.AppNavigationRequests
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.produceState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu

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

    val artworkAccent = rememberArtworkAccent(
        requestKey = media?.artworkUri,
        animationSpec = motionScheme.slowEffectsSpec(),
        label = "nowPlayingAccent",
    )
    val accent = artworkAccent.color
    val palette = artworkAccent.palette
    val extractAccent = artworkAccent.extract

    val sheets = rememberTransportSheetsState()
    val playbackSource by PlaybackSource.label.collectAsState()
    val songWikiState by viewModel.songWikiUiState.collectAsState()
    // The dynamic cover came with the details, and the details came here from the lyric page.
    val dynamicCoverUiState by viewModel.dynamicCoverUiState.collectAsState()
    val localArtworkActive by viewModel.localArtworkActive.collectAsState()
    val remoteArtworkUri by viewModel.remoteArtworkUri.collectAsState()
    val outputVolume by player.volume.collectAsState()
    var showSongWiki by remember { mutableStateOf(false) }
    LaunchedEffect(media?.id) { showSongWiki = false }

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
                    onOpenLyrics = { if (nowPlaying.hasMedia) onOpenLyrics() },
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
                // Silence with no sign of why: a muted app volume showed nowhere on this page.
                if (outputVolume <= 0.001f && nowPlaying.hasMedia) {
                    MutedChip(
                        palette = palette,
                        onUnmute = { player.setVolume(0.5f) },
                    )
                }
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
                        sheets.openQueue()
                    },
                    onComments = sheets::openComments,
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
                source = playbackSource,
                wikiEnabled = nowPlaying.hasMedia,
                onBack = onBack,
                onOpenDetails = {
                    showSongWiki = true
                    if (shouldRequestSongWikiOnOpen(songWikiState, media?.id)) {
                        viewModel.getSongWikiSummary()
                    }
                },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }

    TransportSheets(
        state = sheets,
        nowPlaying = nowPlaying,
        accentPalette = palette,
        viewModel = viewModel,
    )

    val dynamicCoverUrl = dynamicCoverUiState.urlFor(media?.id)
        .takeUnless { localArtworkActive || reducedMotion }
    SongWikiDetailsSheet(
        visible = showSongWiki,
        songTitle = media?.title,
        songArtist = media?.artist,
        albumTitle = media?.albumTitle,
        artworkUri = media?.artworkUri,
        fallbackArtworkUri = remoteArtworkUri
            .takeIf { localArtworkActive && it.isNotBlank() && it != media?.artworkUri },
        durationMs = media?.duration,
        state = songWikiState.scopedToMedia(media?.id),
        accentPalette = palette,
        onDismiss = { showSongWiki = false },
        onRetry = viewModel::getSongWikiSummary,
        artworkOverlay = dynamicCoverUrl?.let { videoUrl ->
            {
                NativeDynamicCoverLayer(
                    url = videoUrl,
                    play = showSongWiki,
                    showBadge = true,
                    reducedMotion = reducedMotion,
                    onVisibilityChanged = {},
                    modifier = Modifier.matchParentSize(),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingTopBar(
    palette: ContentAccentPalette,
    source: String?,
    wikiEnabled: Boolean,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
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
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "正在播放",
                style = MaterialTheme.typography.labelLarge,
                color = palette.secondaryOnPageStart,
            )
            // Where this queue came from, when the page that started it said.
            source?.let {
                Text(
                    text = "来自$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryOnPageStart,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FilledTonalIconButton(
            onClick = onOpenDetails,
            enabled = wikiEnabled,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = palette.container.copy(alpha = 0.72f),
                contentColor = palette.onContainer,
            ),
        ) {
            Icon(imageVector = AppIcons.Info, contentDescription = "歌曲详细信息")
        }
    }
}

@Composable
private fun MutedChip(
    palette: ContentAccentPalette,
    onUnmute: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        AssistChip(
            onClick = onUnmute,
            label = { Text("已静音 · 点按恢复音量") },
            leadingIcon = {
                Icon(AppIcons.VolumeOff, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = palette.container.copy(alpha = 0.72f),
                labelColor = palette.onContainer,
                leadingIconContentColor = palette.onContainer,
            ),
            border = null,
        )
    }
}

/**
 * The artwork. Tapped: opens the lyrics. Held: the frame blooms into a cookie
 * (`ExpressiveArtwork`'s press morph). Paused: it settles to 86%, Apple Music's cue that
 * nothing is moving.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingArtwork(
    media: MediaInfo?,
    isPlaying: Boolean,
    reducedMotion: Boolean,
    onArtworkLoaded: (coil3.Image) -> Unit,
    onOpenLyrics: () -> Unit,
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
                // The cover opens the lyrics, one tap instead of the quote button; play and pause
                // have their own button right beside it.
                onClickLabel = "打开歌词",
                onClick = onOpenLyrics,
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
            NowPlayingCredits(media = media, palette = palette)
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
                contentDescription = "喜欢",
                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
            )
        }
    }
}

/**
 * The artist line, which opens a menu of the song's artists and its album. The player only
 * carries the names, so the ids are looked up from the song when the menu opens.
 */
@Composable
private fun NowPlayingCredits(
    media: MediaInfo?,
    palette: ContentAccentPalette,
    repository: Repository = koinInject(),
) {
    var expanded by remember { mutableStateOf(false) }
    val songId = media?.id?.toLongOrNull()
    val credits by produceState<SongDetailSongs?>(null, songId, expanded) {
        if (expanded && songId != null && value?.id != songId) value = repository.getSongCredits(songId)
    }
    Box {
        Text(
            text = media?.artist?.takeIf(String::isNotBlank) ?: "从任意列表选一首歌开始",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.secondaryOnPageMiddle,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = songId != null, onClickLabel = "查看歌手与专辑") { expanded = true }
                .basicMarquee(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val song = credits?.takeIf { it.id == songId }
            if (song == null) {
                DropdownMenuItem(text = { Text("正在查找…") }, onClick = {}, enabled = false)
            } else {
                song.ar.filter { it.id != 0L }.forEach { artist ->
                    DropdownMenuItem(
                        text = { Text("歌手：${artist.name}") },
                        leadingIcon = { Icon(AppIcons.Person, contentDescription = null) },
                        onClick = {
                            expanded = false
                            AppNavigationRequests.openArtist(artist.id)
                        },
                    )
                }
                if (song.al.id != 0L) {
                    DropdownMenuItem(
                        text = { Text("专辑：${song.al.name}") },
                        leadingIcon = { Icon(AppIcons.Album, contentDescription = null) },
                        onClick = {
                            expanded = false
                            AppNavigationRequests.openAlbum(song.al.id)
                        },
                    )
                }
            }
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
    val seek = rememberSeekDragState(nowPlaying.media?.id)
    val totalDuration = nowPlaying.totalDuration
    val seekable = nowPlaying.hasMedia && totalDuration > 0L
    val sliderValue = seek.progressFor(nowPlaying.progress)
    val displayPosition = seek.positionFor(nowPlaying.safePosition, totalDuration)
    val amplitude by animateFloatAsState(
        targetValue = if (nowPlaying.isPlaying && !seek.isDragging) 1f else 0f,
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
            onValueChange = seek::preview,
            onValueChangeFinished = { seek.commit(totalDuration)?.let(onSeek) },
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
                text = formatPlaybackClock(displayPosition),
                style = clockStyle,
                color = palette.secondaryOnPageMiddle,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatPlaybackClock(totalDuration),
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
            // The toolbar's fabContainerColor below does not reach this button: the FAB reads its
            // own containerColor default (tertiaryContainer), so the artwork accent is passed here.
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onLyrics,
                modifier = Modifier.semantics { contentDescription = "歌词" },
                containerColor = palette.accent,
                contentColor = palette.onAccent,
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
