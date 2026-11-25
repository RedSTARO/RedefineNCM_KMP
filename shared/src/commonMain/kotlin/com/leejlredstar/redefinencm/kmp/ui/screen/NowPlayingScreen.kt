package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.ui.theme.DeclareSystemBarBackground
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveMorphPair
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackPlayButton
import com.leejlredstar.redefinencm.kmp.ui.component.rememberMorphShape
import com.leejlredstar.redefinencm.kmp.ui.component.NativeDynamicCoverLayer
import com.leejlredstar.redefinencm.kmp.ui.component.NowPlayingUiState
import com.leejlredstar.redefinencm.kmp.ui.component.TransportSheets
import com.leejlredstar.redefinencm.kmp.ui.component.foreignProviderName
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
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsSheet
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderCapability
import com.leejlredstar.redefinencm.kmp.data.provider.toProviderItemIdOrNull
import com.leejlredstar.redefinencm.kmp.ui.component.ProviderBadge
import com.leejlredstar.redefinencm.kmp.AppNavigationRequests
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.produceState
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.graphics.lerp
import com.leejlredstar.redefinencm.kmp.ui.component.rememberTransitionVisual
import com.leejlredstar.redefinencm.kmp.ui.component.TransitionVisual
import com.leejlredstar.redefinencm.kmp.ui.component.TransitionArtworkStack
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import com.leejlredstar.redefinencm.kmp.transition.TransitionKind
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilledIconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntRect
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackFold
import com.leejlredstar.redefinencm.kmp.ui.component.ReflowEntry
import com.leejlredstar.redefinencm.kmp.ui.component.ReflowProgress
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
import com.leejlredstar.redefinencm.kmp.ui.component.reflowScale

/**
 * The Now Playing entry page: what the mini player, the desktop rail and every OS
 * "now playing" request open first.
 *
 * The artwork, track title and transport are the focal points. The cover opens lyrics, and
 * the quotation-mark action makes that same route discoverable without guessing the gesture.
 * `AmllPlayerScreen` owns the lyric renderer.
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
    /** Each colour read from the current track's cover, for the caller that themes the app from it. */
    onAccentColor: (Color) -> Unit = {},
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
        onAccentColor = onAccentColor,
    )
    val accent = artworkAccent.color
    val palette = artworkAccent.palette
    val extractAccent = artworkAccent.extract

    // Through a song transition the page travels with the music: the incoming cover dissolves
    // in over the outgoing one and the page's colours follow it, from the outgoing cover's to the
    // incoming one's, instead of jumping at the swap.
    val visual = rememberTransitionVisual(player)
    val blendKey = visual?.incoming?.id
    val startPalette = remember(blendKey) { palette }
    val startAccent = remember(blendKey) { accent }
    val incomingAccent = rememberArtworkAccent(
        requestKey = visual?.incoming?.artworkUri,
        fallback = startAccent,
        animationSpec = motionScheme.slowEffectsSpec(),
        label = "nowPlayingIncomingAccent",
    )
    val incomingPalette = incomingAccent.palette

    val sheets = rememberTransportSheetsState()
    val playbackSource by PlaybackSource.label.collectAsState()
    val songWikiState by viewModel.songWikiUiState.collectAsState()
    // The dynamic cover plays over the artwork in the song details, which open from this page.
    val dynamicCoverUiState by viewModel.dynamicCoverUiState.collectAsState()
    val localArtworkActive by viewModel.localArtworkActive.collectAsState()
    val remoteArtworkUri by viewModel.remoteArtworkUri.collectAsState()
    val outputVolume by player.volume.collectAsState()
    val android = remember { getPlatform().isAndroid }
    val controlsScroll = rememberScrollState()
    val restoreState by viewModel.playerStatusRestoreState.collectAsState()
    var wikiMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    val showSongWiki = wikiMediaId != null && wikiMediaId == media?.id
    LaunchedEffect(wikiMediaId, media?.id, restoreState is PlayerStatusRestoreState.Loading) {
        if (wikiMediaId != null && !showSongWiki &&
            (media != null || restoreState !is PlayerStatusRestoreState.Loading)
        ) {
            wikiMediaId = null
        } else if (showSongWiki && shouldRequestSongWikiOnOpen(songWikiState, media?.id)) {
            viewModel.getSongWikiSummary()
        }
    }

    // The bars sit on the top of the cover-tinted gradient, which can be light in a dark theme.
    DeclareSystemBarBackground(dark = palette.pageStart.luminance() < 0.5f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Read at draw time, so a blend repaints the page without recomposing it.
                val weight = visual?.weight
                val from = if (weight == null) palette else startPalette
                val to = if (weight == null) palette else incomingPalette
                val t = weight ?: 0f
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            lerp(from.pageStart, to.pageStart, t),
                            lerp(from.pageMiddle, to.pageMiddle, t),
                            lerp(from.pageEnd, to.pageEnd, t),
                        ),
                    ),
                )
            }
            .semantics { contentDescription = strings.nowPlaying },
    ) {
        val artwork: @Composable (Modifier) -> Unit = { modifier ->
            NowPlayingArtwork(
                media = media,
                isPlaying = nowPlaying.isPlaying,
                visual = visual,
                palette = palette,
                reducedMotion = reducedMotion,
                onArtworkLoaded = extractAccent,
                onIncomingArtworkLoaded = incomingAccent.extract,
                onOpenLyrics = { if (nowPlaying.hasMedia) onOpenLyrics() },
                modifier = modifier,
            )
        }
        val header: @Composable (Modifier, Boolean) -> Unit = { modifier, tight ->
            NowPlayingTopBar(
                palette = palette,
                source = playbackSource?.text,
                wikiEnabled = nowPlaying.hasMedia,
                onBack = onBack,
                onOpenDetails = { wikiMediaId = media?.id },
                tight = tight,
                modifier = modifier,
            )
        }
        val controls: @Composable (Boolean, Boolean, ReflowProgress?) -> Unit = { short, tight, reflow ->
            NowPlayingTitle(
                media = media,
                isFavorite = nowPlaying.isFavorite,
                canFavorite = nowPlaying.canFavorite,
                palette = palette,
                onFavorite = viewModel::onFavClick,
                reducedMotion = reducedMotion,
                tight = tight,
            )
            Spacer(Modifier.height(if (tight) 4.dp else 16.dp))
            NowPlayingProgress(
                nowPlaying = nowPlaying,
                palette = palette,
                reducedMotion = reducedMotion,
                transitionKind = visual?.kind,
                onSeek = player::seekTo,
            )
            if (outputVolume <= 0.001f && nowPlaying.hasMedia) {
                MutedChip(palette = palette, onUnmute = { player.setVolume(0.5f) })
            }
            Spacer(Modifier.height(if (tight) 4.dp else 16.dp))
            NowPlayingTransport(
                isPlaying = nowPlaying.isPlaying,
                hasMedia = nowPlaying.hasMedia,
                compact = short,
                tight = tight,
                palette = palette,
                onPrevious = player::seekToPrevious,
                onPlayPause = player::togglePlayPause,
                onNext = player::seekToNext,
                reflow = reflow,
            )
        }
        val toolbar: @Composable (Boolean) -> Unit = { tight ->
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
                commentsEnabled = nowPlaying.canComment,
                onLyrics = onOpenLyrics,
                tight = tight,
            )
        }
        val viewport = Modifier.fillMaxSize()
            .windowInsetsPadding(if (android) WindowInsets.playbackSafeContent else WindowInsets.safeDrawing)
        if (android) {
            val wideWindow = isWidePlaybackViewport(maxWidth, maxHeight)
            PlaybackViewport(viewport) { fold, positioned ->
                AdaptiveNowPlayingLayout(
                    fold, positioned, wideWindow, reducedMotion, controlsScroll, artwork, header, controls, toolbar,
                )
            }
        } else {
            BoxWithConstraints(viewport.padding(horizontal = ExpressiveLayout.PageHorizontalPadding)) {
                val twoColumns = maxWidth > maxHeight && maxWidth >= 600.dp
                val shortHeight = maxHeight < 600.dp
                val shortWindow = maxHeight < 420.dp
                Column(Modifier.fillMaxSize()) {
                    header(Modifier, shortWindow)
                    if (twoColumns) {
                        Row(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                artwork(Modifier.widthIn(max = 520.dp))
                            }
                            Spacer(Modifier.width(24.dp))
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                val tight = shortWindow || maxWidth < 340.dp
                                Column(
                                    Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(controlsScroll)
                                        .padding(vertical = if (tight) 4.dp else 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    controls(shortHeight, tight, null)
                                    Spacer(Modifier.height(if (tight) 4.dp else 24.dp))
                                    toolbar(tight)
                                }
                            }
                        }
                    } else {
                        BoxWithConstraints(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val tight = shortWindow || maxWidth < 340.dp
                            StackedPlayerContent(
                                viewportHeight = maxHeight,
                                tight = tight,
                                scroll = controlsScroll,
                                artwork = artwork,
                            ) {
                                controls(shortHeight, tight, null)
                                Spacer(Modifier.height(if (tight) 4.dp else 24.dp))
                                toolbar(tight)
                            }
                        }
                    }
                }
            }
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
        onDismiss = { wikiMediaId = null },
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

internal data class NowPlayingReflowLayout(
    val arrangement: PlaybackPaneArrangement,
    val showInlineArtwork: Boolean,
    val tightControls: Boolean,
    val shortTransport: Boolean,
    val headerInScroll: Boolean,
    val narrowMargins: Boolean,
)

/** A short control pane scrolls instead of shrinking touch targets or clipping the action row. */
@Composable
private fun AdaptiveNowPlayingLayout(
    fold: PlaybackFold?,
    positioned: Boolean,
    wideWindow: Boolean,
    reducedMotion: Boolean,
    scroll: ScrollState,
    artwork: @Composable (Modifier) -> Unit,
    header: @Composable (Modifier, Boolean) -> Unit,
    controls: @Composable (short: Boolean, tight: Boolean, reflow: ReflowProgress?) -> Unit,
    toolbar: @Composable (tight: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val full = IntRect(0, 0, constraints.maxWidth, constraints.maxHeight)
        val (artPane, controlPane) = nowPlayingPanes(full, density, fold, wideWindow)
        val layout = nowPlayingReflowLayout(full, artPane, controlPane, density)
        val expandedWidth = hasExpandedPlaybackWidth(maxWidth)
        val hasSpace = controlPane.width > 0 && controlPane.height > 0
        val reflow = rememberPlaybackReflow(
            enabled = true,
            target = if (hasSpace && positioned) PlaybackReflowTarget(
                full, fold?.placementIn(full), layout, expandedWidth,
            ) else null,
            reducedMotion = reducedMotion,
        )
        if (!hasSpace) return@BoxWithConstraints
        if (artPane != null) {
            Box(
                Modifier.inPlaybackPane(artPane).padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                artwork(Modifier.widthIn(max = 520.dp).reflowScale(reflow, start = 0.92f))
            }
        }
        val tight = layout.tightControls
        val headerInScroll = layout.headerInScroll
        val sidePadding = if (layout.narrowMargins) 8.dp else ExpressiveLayout.PageHorizontalPadding
        Column(Modifier.inPlaybackPane(controlPane).padding(horizontal = sidePadding)) {
            if (!headerInScroll) header(Modifier, tight)
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                StackedPlayerContent(
                    viewportHeight = maxHeight,
                    tight = tight,
                    scroll = scroll,
                    artwork = if (layout.showInlineArtwork) ({ modifier ->
                        artwork(modifier.reflowScale(reflow, start = 0.92f))
                    }) else null,
                ) {
                    if (headerInScroll) header(Modifier, true)
                    controls(layout.shortTransport, tight, reflow)
                    Spacer(Modifier.height(if (tight) 4.dp else 20.dp))
                    toolbar(tight)
                }
            }
        }
    }
}

/** The artwork takes what's left after the actual text, seek bar, transport and toolbar are measured. */
@Composable
private fun StackedPlayerContent(
    viewportHeight: Dp,
    tight: Boolean,
    scroll: ScrollState,
    artwork: (@Composable (Modifier) -> Unit)?,
    controls: @Composable () -> Unit,
) {
    val padding = if (tight) 4.dp else 16.dp
    val gap = if (tight) 8.dp else 12.dp
    Layout(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(scroll).padding(vertical = padding),
        content = {
            Box { artwork?.invoke(Modifier.fillMaxSize()) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { controls() }
        },
    ) { (cover, transport), constraints ->
        val width = constraints.maxWidth
        val measuredControls = transport.measure(Constraints.fixedWidth(width))
        val coverSize = if (artwork == null) 0 else stackedPlayerArtworkSize(
            height = viewportHeight,
            controlsHeight = measuredControls.height.toDp() + padding * 2 + gap,
            width = width.toDp(),
        ).roundToPx()
        val measuredCover = cover.measure(Constraints.fixed(coverSize, coverSize))
        val beforeControls = if (coverSize > 0) coverSize + gap.roundToPx() else 0
        layout(width, beforeControls + measuredControls.height) {
            if (coverSize > 0) measuredCover.placeRelative((width - coverSize) / 2, 0)
            measuredControls.placeRelative(0, beforeControls)
        }
    }
}

/** Keep a small cover and let the controls scroll when even the smallest artwork cannot fit. */
internal fun stackedPlayerArtworkSize(height: Dp, controlsHeight: Dp, width: Dp): Dp =
    minOf(width, 420.dp, (height - controlsHeight).coerceAtLeast(64.dp))

internal fun nowPlayingReflowLayout(
    full: IntRect,
    artPane: IntRect?,
    controlPane: IntRect,
    density: Density,
): NowPlayingReflowLayout = with(density) {
    val height = controlPane.height.toDp()
    val width = controlPane.width.toDp()
    NowPlayingReflowLayout(
        arrangement = playbackPaneArrangement(full, controlPane, artPane),
        showInlineArtwork = artPane == null && height >= 400.dp,
        tightControls = height < 420.dp || width < 340.dp,
        shortTransport = height < 600.dp,
        headerInScroll = height < 144.dp,
        narrowMargins = width < 320.dp,
    )
}

internal fun nowPlayingPanes(
    full: IntRect,
    density: Density,
    fold: PlaybackFold?,
    wideWindow: Boolean,
): Pair<IntRect?, IntRect> = with(density) {
    val placement = fold?.placementIn(full)
    if (placement != null) {
        val folded = placement.split
        // Give the artwork its own pane only when the control pane can show a complete player.
        if (folded != null && folded.first.width.toDp() >= 192.dp && folded.first.height.toDp() >= 192.dp &&
            folded.second.width.toDp() >= 320.dp && folded.second.height.toDp() >= 420.dp
        ) {
            return folded.first to folded.second
        }
        return null to placement.single
    }
    val short = full.height.toDp() < 420.dp
    if (wideWindow && full.height.toDp() >= 192.dp && full.width.toDp() >= (if (short) 560.dp else 840.dp)) {
        val split = (full.width * 0.42f).toInt()
        IntRect(0, 0, split, full.height) to IntRect(split, 0, full.width, full.height)
    } else {
        null to full
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingTopBar(
    palette: ContentAccentPalette,
    source: String?,
    wikiEnabled: Boolean,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    tight: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (tight) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(ExpressiveLayout.MinimumTouchTarget)
                .semantics { contentDescription = strings.collapsePlayer },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = palette.quietContainer,
                contentColor = palette.onQuietContainer,
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
                text = strings.nowPlaying,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = palette.onPageStart,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Where this queue came from, when the page that started it said.
            source?.let {
                Text(
                    text = strings.playbackSourceFrom(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.secondaryOnPageStart,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onOpenDetails,
            enabled = wikiEnabled,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(ExpressiveLayout.MinimumTouchTarget),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = palette.secondaryOnPageStart,
                disabledContentColor = palette.onPageStart.copy(alpha = 0.38f),
            ),
        ) {
            Icon(imageVector = AppIcons.Info, contentDescription = strings.songDetails)
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
            label = { Text(strings.mutedTapToRestoreVolume) },
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
 *
 * Through a song transition the next song's cover dissolves in over this one in step with the
 * music, settling into focus as it comes ([TransitionArtworkStack]); the frame, its shadow and
 * its press morph stay one.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingArtwork(
    media: MediaInfo?,
    isPlaying: Boolean,
    visual: TransitionVisual?,
    palette: ContentAccentPalette,
    reducedMotion: Boolean,
    onArtworkLoaded: (coil3.Image) -> Unit,
    onIncomingArtworkLoaded: (coil3.Image) -> Unit,
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
    val pressed by interactionSource.collectIsPressedAsState()
    val morph by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "nowPlayingArtworkMorph",
    )
    // The frame and its shadow share the same silhouette through the press.
    val frameShape = rememberMorphShape(ExpressiveMorphPair.ArtworkBloom, morph)
    TransitionArtworkStack(
        current = media,
        visual = visual,
        // A beat-matched blend brings the next cover into focus and kicks on the beat; a plain
        // crossfade only dissolves, so the two read differently at a glance.
        focusPull = visual?.movesWithBeat == true,
        onCurrentImage = onArtworkLoaded,
        onIncomingImage = onIncomingArtworkLoaded,
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                val kick = 1f + BEAT_KICK_SCALE * (visual?.beatPulse ?: 0f)
                scaleX = scale * kick
                scaleY = scale * kick
            }
            .dropShadow(
                shape = frameShape,
                shadow = Shadow(
                    radius = 24.dp,
                    color = Color.Black,
                    offset = DpOffset(x = 0.dp, y = 14.dp),
                    alpha = 0.28f,
                ),
            )
            .dropShadow(
                shape = frameShape,
                shadow = Shadow(
                    radius = 32.dp,
                    color = palette.accent,
                    offset = DpOffset(x = 0.dp, y = 8.dp),
                    alpha = 0.20f,
                ),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = media != null,
                // The cover opens the lyrics, one tap instead of the quote button; play and pause
                // have their own button right beside it.
                onClickLabel = strings.openLyrics,
                onClick = onOpenLyrics,
            ),
    ) { layerMedia, layerModifier, imageModifier, onLoaded ->
        ExpressiveArtwork(
            model = layerMedia?.artworkUri?.takeIf(String::isNotBlank),
            // Only the current track's cover is described; the other is on its way in or out.
            contentDescription = layerMedia?.takeIf { it.id == media?.id }?.title?.let { strings.artworkOf(it) },
            modifier = Modifier.matchParentSize().then(layerModifier),
            imageModifier = imageModifier,
            shape = frameShape,
            containerColor = palette.quietContainer,
            contentColor = palette.onQuietContainer,
            onImageLoaded = onLoaded,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingTitle(
    media: MediaInfo?,
    isFavorite: Boolean,
    /** False for a provider without likes: the heart stays, dimmed, so the row keeps its shape. */
    canFavorite: Boolean,
    palette: ContentAccentPalette,
    onFavorite: () -> Unit,
    reducedMotion: Boolean,
    tight: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The words change at the swap; effects motion keeps the outgoing letters from bouncing.
        Crossfade(
            targetState = media,
            animationSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.slowEffectsSpec(),
            label = "nowPlayingTitle",
            modifier = Modifier.weight(1f),
        ) { track ->
            Column {
                Text(
                    text = track?.title?.takeIf(String::isNotBlank) ?: strings.notPlaying,
                    style = if (tight) MaterialTheme.typography.titleLargeEmphasized
                    else MaterialTheme.typography.headlineLargeEmphasized,
                    color = palette.onPageMiddle,
                    maxLines = 1,
                    overflow = if (reducedMotion) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = Modifier.semantics { heading() }
                        .then(if (reducedMotion) Modifier else Modifier.basicMarquee()),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    foreignProviderName(track?.id)?.let { label ->
                        ProviderBadge(
                            label = label,
                            contentColor = palette.onQuietContainer,
                            containerColor = palette.quietContainer,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    NowPlayingCredits(media = track, palette = palette, reducedMotion = reducedMotion, tight = tight)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        FilledIconToggleButton(
            checked = isFavorite,
            onCheckedChange = { onFavorite() },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(ExpressiveLayout.MinimumTouchTarget),
            enabled = canFavorite,
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = palette.quietContainer,
                contentColor = palette.onQuietContainer,
                checkedContainerColor = palette.container,
                checkedContentColor = palette.onContainer,
            ),
        ) {
            Icon(
                imageVector = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                contentDescription = strings.like,
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
    reducedMotion: Boolean,
    tight: Boolean,
    repository: Repository = koinInject(),
    providers: MusicProviderRegistry = koinInject(),
) {
    var expanded by remember { mutableStateOf(false) }
    // Artist and album pages are NetEase's; another provider's names are plain text.
    val songId = media?.id
        ?.takeIf { ProviderCapability.CREDITS in providers.capabilitiesOf(it) }
        ?.toProviderItemIdOrNull()
        ?.neteaseIdOrNull
    val credits by produceState<SongDetailSongs?>(null, songId, expanded) {
        if (expanded && songId != null && value?.id != songId) value = repository.getSongCredits(songId)
    }
    Box {
        Text(
            text = media?.artist?.takeIf(String::isNotBlank) ?: strings.pickSongToStart,
            style = if (tight) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = palette.secondaryOnPageMiddle,
            maxLines = 1,
            overflow = if (reducedMotion) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = songId != null, onClickLabel = strings.viewArtistAndAlbum) { expanded = true }
                .then(if (reducedMotion) Modifier else Modifier.basicMarquee()),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val song = credits?.takeIf { it.id == songId }
            if (song == null) {
                DropdownMenuItem(text = { Text(strings.lookingUp) }, onClick = {}, enabled = false)
            } else {
                song.ar.filter { it.id != 0L }.forEach { artist ->
                    DropdownMenuItem(
                        text = { Text(strings.artistWithName(artist.name)) },
                        leadingIcon = { Icon(AppIcons.Person, contentDescription = null) },
                        onClick = {
                            expanded = false
                            AppNavigationRequests.openArtist(artist.id)
                        },
                    )
                }
                if (song.al.id != 0L) {
                    DropdownMenuItem(
                        text = { Text(strings.albumWithName(song.al.name)) },
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
    transitionKind: TransitionKind?,
    onSeek: (Long) -> Unit,
) {
    val seek = rememberSeekDragState(nowPlaying.media?.id)
    val totalDuration = nowPlaying.totalDuration
    val seekable = nowPlaying.hasMedia && totalDuration > 0L
    val sliderValue = seek.progressFor(nowPlaying.progress)
    val displayPosition = seek.positionFor(nowPlaying.safePosition, totalDuration)
    val amplitude by animateFloatAsState(
        targetValue = if (nowPlaying.isPlaying && !seek.isDragging && !reducedMotion) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else MaterialTheme.motionScheme.defaultSpatialSpec(),
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
                .height(ExpressiveLayout.MinimumTouchTarget)
                .semantics { contentDescription = strings.playbackPosition },
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
                    trackColor = palette.onPageMiddle.copy(alpha = 0.24f),
                    amplitude = { amplitude },
                )
            },
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                val clockStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
                Text(
                    text = formatPlaybackClock(displayPosition),
                    style = MaterialTheme.typography.labelMediumEmphasized.copy(fontFeatureSettings = "tnum"),
                    color = palette.onPageMiddle,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatPlaybackClock(totalDuration),
                    style = clockStyle,
                    color = palette.secondaryOnPageMiddle,
                )
            }
            // Between the times, where nothing else sits; it may stand taller than the row
            // without moving anything below it.
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                TransitionKindLabel(
                    kind = transitionKind,
                    palette = palette,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.wrapContentHeight(unbounded = true),
                )
            }
        }
    }
}

/**
 * Names the transition the listener is hearing while it lasts: 智能过渡 for a beat-matched blend,
 * 淡入淡出 for a plain crossfade, which is also what the smart mode falls back to when two songs'
 * tempi are too far apart to match.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransitionKindLabel(
    kind: TransitionKind?,
    palette: ContentAccentPalette,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // Kept through the fade-out, when the blend has already ended.
    var lastKind by remember { mutableStateOf(kind) }
    LaunchedEffect(kind) { if (kind != null) lastKind = kind }
    AnimatedVisibility(
        visible = kind != null,
        enter = fadeIn(if (reducedMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(if (reducedMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()),
        modifier = modifier,
    ) {
        val shown = kind ?: lastKind ?: return@AnimatedVisibility
        val beatMatched = shown == TransitionKind.BEAT_MATCHED
        Surface(
            shape = CircleShape,
            color = if (beatMatched) palette.container else palette.quietContainer,
            contentColor = if (beatMatched) palette.onContainer else palette.onQuietContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (beatMatched) {
                    Icon(
                        imageVector = AppIcons.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp).size(12.dp),
                    )
                }
                Text(
                    text = if (beatMatched) strings.transitionSmart else strings.transitionCrossfade,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowPlayingTransport(
    isPlaying: Boolean,
    hasMedia: Boolean,
    compact: Boolean,
    tight: Boolean,
    palette: ContentAccentPalette,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    reflow: ReflowProgress?,
) {
    val tonalColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = palette.quietContainer,
        contentColor = palette.onQuietContainer,
        disabledContainerColor = palette.quietContainer.copy(alpha = 0.60f),
        disabledContentColor = palette.onQuietContainer.copy(alpha = 0.38f),
    )
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val metrics = nowPlayingTransportMetrics(constraints.maxWidth, density, compact, tight)
        val fits = metrics.fits(constraints.maxWidth, density)
        val skipShape = IconButtonDefaults.shapes(
            shape = CircleShape,
            pressedShape = when (metrics.skip.height) {
                96.dp -> IconButtonDefaults.largePressedShape
                56.dp -> IconButtonDefaults.mediumPressedShape
                else -> IconButtonDefaults.smallPressedShape
            },
        )
        val buttons: @Composable () -> Unit = {
            FilledTonalIconButton(
                onClick = onPrevious,
                shapes = skipShape,
                modifier = Modifier.size(metrics.skip),
                enabled = hasMedia,
                colors = tonalColors,
            ) {
                Icon(
                    imageVector = AppIcons.SkipPrevious,
                    contentDescription = strings.previous,
                    modifier = Modifier.size(metrics.skipIcon),
                )
            }
            val heroButton = metrics.play.height >= 96.dp
            PlaybackPlayButton(
                isPlaying = isPlaying,
                enabled = hasMedia,
                onClick = onPlayPause,
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                modifier = Modifier.size(metrics.play),
                iconSize = metrics.playIcon,
                shapes = IconButtonDefaults.toggleableShapes(
                    shape = CircleShape,
                    pressedShape = if (heroButton) IconButtonDefaults.largePressedShape
                    else IconButtonDefaults.smallPressedShape,
                    checkedShape = if (heroButton) IconButtonDefaults.largeSelectedRoundShape
                    else IconButtonDefaults.smallSelectedRoundShape,
                ),
            )
            FilledTonalIconButton(
                onClick = onNext,
                shapes = skipShape,
                modifier = Modifier.size(metrics.skip),
                enabled = hasMedia,
                colors = tonalColors,
            ) {
                Icon(
                    imageVector = AppIcons.SkipNext,
                    contentDescription = strings.next,
                    modifier = Modifier.size(metrics.skipIcon),
                )
            }
        }
        val entry = Modifier.reflowPlacement(reflow, ReflowEntry.TowardEndFromCenter)
        if (fits) {
            Row(
                modifier = entry,
                horizontalArrangement = Arrangement.spacedBy(metrics.gap),
                verticalAlignment = Alignment.CenterVertically,
            ) { buttons() }
        } else {
            // Truly tiny windows stack the whole group instead of orphaning one button.
            Column(
                modifier = entry,
                verticalArrangement = Arrangement.spacedBy(metrics.gap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { buttons() }
        }
    }
}

internal data class NowPlayingTransportMetrics(
    val skip: DpSize,
    val play: DpSize,
    val skipIcon: Dp,
    val playIcon: Dp,
    val gap: Dp,
) {
    fun fits(widthPx: Int, density: Density): Boolean = with(density) {
        skip.width.roundToPx() * 2 + play.width.roundToPx() + gap.roundToPx() * 2 <= widthPx
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun nowPlayingTransportMetrics(
    widthPx: Int,
    density: Density,
    compact: Boolean,
    tight: Boolean,
): NowPlayingTransportMetrics {
    val wide = IconButtonDefaults.IconButtonWidthOption.Wide
    val roomy = NowPlayingTransportMetrics(
        IconButtonDefaults.largeContainerSize(), IconButtonDefaults.extraLargeContainerSize(wide),
        IconButtonDefaults.largeIconSize, IconButtonDefaults.extraLargeIconSize, 16.dp,
    )
    val standard = NowPlayingTransportMetrics(
        IconButtonDefaults.mediumContainerSize(), IconButtonDefaults.largeContainerSize(wide),
        IconButtonDefaults.mediumIconSize, IconButtonDefaults.largeIconSize, 16.dp,
    )
    return when {
        !compact && !tight && roomy.fits(widthPx, density) -> roomy
        !tight && standard.fits(widthPx, density) -> standard
        else -> NowPlayingTransportMetrics(DpSize(48.dp, 48.dp), DpSize(60.dp, 48.dp), 24.dp, 32.dp, 4.dp)
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
    /** False for a provider without comments. */
    commentsEnabled: Boolean,
    onLyrics: () -> Unit,
    tight: Boolean,
) {
    val actions: @Composable () -> Unit = {
        FilledIconToggleButton(
            checked = shuffleEnabled,
            onCheckedChange = onShuffle,
            shapes = IconButtonDefaults.toggleableShapes(),
            enabled = hasMedia,
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = Color.Transparent,
                contentColor = palette.onQuietContainer,
                checkedContainerColor = palette.container,
                checkedContentColor = palette.onContainer,
            ),
        ) {
            Icon(
                imageVector = if (shuffleEnabled) AppIcons.ShuffleOn else AppIcons.Shuffle,
                contentDescription = strings.shuffle,
            )
        }
        IconButton(
            onClick = onQueue,
            shapes = IconButtonDefaults.shapes(),
            enabled = hasMedia,
        ) {
            Icon(imageVector = AppIcons.QueueMusic, contentDescription = strings.queue)
        }
        IconButton(
            onClick = onComments,
            shapes = IconButtonDefaults.shapes(),
            enabled = hasMedia && commentsEnabled,
        ) {
            Icon(imageVector = AppIcons.Comment, contentDescription = strings.comments)
        }
    }
    if (tight) {
        Surface(
            shape = CircleShape,
            color = palette.quietContainer,
            contentColor = palette.onQuietContainer,
        ) {
            FlowRow(horizontalArrangement = Arrangement.Center) {
                actions()
                FilledIconButton(
                    onClick = onLyrics,
                    enabled = hasMedia,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = palette.container,
                        contentColor = palette.onContainer,
                    ),
                ) {
                    Icon(AppIcons.FormatQuote, contentDescription = strings.lyrics)
                }
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val label = strings.lyrics
        val labelStyle = MaterialTheme.typography.labelLargeEmphasized
        val textMeasurer = rememberTextMeasurer()
        // Leave room for three 48dp targets, toolbar insets and the extended action's icon/padding.
        // Measure the localized label so text scaling falls back to the same quote icon cleanly.
        val labelWidth = textMeasurer.measure(label, style = labelStyle, maxLines = 1).size.width
        val showLabel = with(LocalDensity.current) { maxWidth.roundToPx() >= 244.dp.roundToPx() + labelWidth }
        val colors = FloatingToolbarColors(
            toolbarContainerColor = palette.quietContainer,
            toolbarContentColor = palette.onQuietContainer,
            fabContainerColor = palette.container,
            fabContentColor = palette.onContainer,
        )
        if (showLabel) {
            // The toolbar's FAB slot enforces square constraints; put the extended action beside it.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalFloatingToolbar(expanded = true, colors = colors) { actions() }
                ExtendedFloatingActionButton(
                    onClick = onLyrics,
                    icon = { Icon(AppIcons.FormatQuote, contentDescription = null) },
                    text = { Text(label, style = labelStyle, maxLines = 1) },
                    modifier = Modifier.semantics { contentDescription = label },
                    shape = CircleShape,
                    containerColor = palette.container,
                    contentColor = palette.onContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                )
            }
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = colors,
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                        onClick = onLyrics,
                        containerColor = palette.container,
                        contentColor = palette.onContainer,
                    ) { Icon(AppIcons.FormatQuote, contentDescription = label) }
                },
                content = { actions() },
            )
        }
    }
}
/** How much a downbeat enlarges the cover through a beat-matched blend. */
private const val BEAT_KICK_SCALE = 0.02f
