package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.HorizontalRuler
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.leejlredstar.redefinencm.kmp.data.api.dto.RecommendResourceRecommend
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalCompactAppBar
import com.leejlredstar.redefinencm.kmp.ui.adaptive.browseHeader
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveCacheHint
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveFeatureIcon
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.rememberMorphShape
import com.leejlredstar.redefinencm.kmp.ui.component.rememberPressMorphProgress
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ArtworkAccent
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

internal fun replaceQueueWithDailyRecommendations(
    player: PlatformPlayer,
    songs: List<SongDetailSongs>,
) {
    if (songs.isEmpty()) return
    PlaybackSource.set(DailySource)
    player.setQueue(songs.map { it.toMediaInfo() }, 0)
}

@Composable
fun HomeScreen(
    scaffoldPadding: PaddingValues,
    onOpenPlaylist: (Long) -> Unit,
    onOpenMy: () -> Unit,
    onOpenRecognition: () -> Unit,
    onOpenDailySongs: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
    searchEntryModifier: Modifier = Modifier,
) {
    val recommend by viewModel.recommendSongs.collectAsState()
    val recommendResource by viewModel.recommendResource.collectAsState()
    val accountLoading by viewModel.accountLoading.collectAsState()
    val resourceLoadError by viewModel.recommendResourceLoadError.collectAsState()
    val songsLoadError by viewModel.recommendSongsLoadError.collectAsState()
    val resourceFromCache by viewModel.recommendResourceFromCache.collectAsState()
    val songsFromCache by viewModel.recommendSongsFromCache.collectAsState()
    val userDetail by viewModel.userDetail.collectAsState()
    val uid by viewModel.uid.collectAsState()
    val accountLoadError by viewModel.accountLoadError.collectAsState()
    val sessionExpired by viewModel.sessionExpired.collectAsState()
    val signedOut = uid == 0L && !accountLoading && accountLoadError == null
    val dailySongs = recommend?.data?.dailySongs.orEmpty()
    val dailyQueue = remember(dailySongs) { dailySongs.map { it.toMediaInfo() } }
    val playWholeList = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    val resources = recommendResource?.recommend.orEmpty()
    val listState = rememberLazyListState()

    // Only the feed's resting position and its controls avoid the top insets. The viewport
    // itself reaches the window edge, so scrolling art doesn't stop at a second, artificial edge.
    ExpressivePage(headerOwnsTopInsets = true) {
        val gutter = LocalExpressivePageGutter.current
        val sectionInset = Modifier.padding(horizontal = gutter)
        val compactHeight = LocalCompactAppBar.current
        val topInsets = WindowInsets.browseHeader
        val statusBars = WindowInsets.statusBars
        var consumedInsets by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
        val safeTop = topInsets.exclude(consumedInsets).asPaddingValues().calculateTopPadding()
        val statusTop = statusBars.exclude(consumedInsets).asPaddingValues().calculateTopPadding()
        val surface = MaterialTheme.colorScheme.surface
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                // A lower tabletop pane already starts below the window's top insets.
                .onConsumedWindowInsetsChanged { consumedInsets = it }
                .homeControlBoundary(safeTop)
                .homeStatusFade(statusTop, surface),
            contentPadding = PaddingValues(
                top = safeTop + 8.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "search") {
                Column(
                    modifier = sectionInset.fillMaxWidth().padding(bottom = 20.dp).homeControl(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExpressivePageHeading(
                            title = strings.forYou,
                            supportingText = strings.discoveryHeadingSubtitle.takeUnless { compactHeight },
                            modifier = Modifier.weight(1f),
                        )
                        HomeAccountAvatar(
                            avatarUrl = userDetail?.profile?.avatarUrl,
                            nickname = userDetail?.profile?.nickname ?: strings.me,
                            onOpenMy = onOpenMy,
                        )
                    }
                    HomeSearchEntry(
                        onClick = onOpenSearch,
                        modifier = Modifier.widthIn(max = SearchEntryMaxWidth).fillMaxWidth()
                            .then(searchEntryModifier),
                    )
                }
            }

            if (signedOut) {
                // No account is required to recognize or search for music. Lead with a usable
                // discovery feature, rather than presenting sign-in as the only path forward.
                item(key = "music-tools") {
                    RecognitionFeature(onOpenRecognition, featured = true, modifier = sectionInset.homeControl())
                }
                item(key = "sign-in") {
                    ExpressiveStatePanel(
                        title = if (sessionExpired) strings.signInExpiredTitle else strings.signInToSeeRecommendationsTitle,
                        message = if (sessionExpired) strings.signInExpiredMessage else strings.signInToSeeRecommendationsMessage,
                        icon = AppIcons.Person,
                        actionLabel = strings.signIn,
                        onAction = onOpenLogin,
                        modifier = sectionInset.padding(top = 16.dp).homeControl(),
                    )
                }
            } else {
                item(key = "daily-feature") {
                    val error = songsLoadError ?: accountLoadError
                    when {
                        dailySongs.isNotEmpty() -> Column(
                            modifier = sectionInset.padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DailyPicksFeature(
                                queue = dailyQueue,
                                player = player,
                                onTrack = { index -> playFromList(player, dailyQueue, index, playWholeList, source = DailySource) },
                                onPlay = { replaceQueueWithDailyRecommendations(player, dailySongs) },
                                onBrowse = onOpenDailySongs,
                            )
                            if (songsFromCache) {
                                ExpressiveCacheHint(isRefreshing = accountLoading, modifier = Modifier.homeControl())
                            }
                        }
                        error != null -> ExpressiveStatePanel(
                            title = strings.dailyPicksLoadFailed,
                            message = error.text,
                            icon = AppIcons.Refresh,
                            tone = ExpressiveStateTone.Error,
                            actionLabel = strings.retry,
                            onAction = viewModel::retryAccountData,
                            modifier = sectionInset.homeControl(),
                        )
                        accountLoading && recommend == null -> ExpressiveLoadingState(
                            label = strings.dailyPicksLoading,
                            modifier = sectionInset.homeControl(),
                        )
                        else -> ExpressiveStatePanel(
                            title = strings.dailyPicksEmpty,
                            message = strings.sectionEmptyTapReload,
                            icon = AppIcons.MusicNote,
                            actionLabel = strings.reload,
                            onAction = viewModel::retryAccountData,
                            modifier = sectionInset.homeControl(),
                        )
                    }
                }
                item(key = "recommended-playlists") {
                    HomePlaylists(
                        items = resources,
                        isLoading = accountLoading && recommendResource == null,
                        isFromCache = resourceFromCache,
                        isRefreshing = accountLoading,
                        errorMessage = (resourceLoadError ?: accountLoadError)?.text?.takeIf { resources.isEmpty() },
                        onRetry = viewModel::retryAccountData,
                        onOpenPlaylist = onOpenPlaylist,
                        gutter = gutter,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
                item(key = "music-tools") {
                    // When personal recommendations can be shown, music tools follow them.
                    RecognitionFeature(
                        onOpenRecognition,
                        featured = false,
                        modifier = sectionInset.padding(top = 20.dp).homeControl(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DailyPicksFeature(
    queue: List<MediaInfo>,
    player: PlatformPlayer,
    onTrack: (Int) -> Unit,
    onPlay: () -> Unit,
    onBrowse: () -> Unit,
) {
    val covers = remember(queue) { queue.asSequence().map { it.artworkUri }.filter(String::isNotBlank).distinct().take(3).toList() }
    val artworkAccent = rememberArtworkAccent(requestKey = covers.firstOrNull(), label = "dailyPicksAccent")
    val palette = artworkAccent.palette
    val playInteraction = remember { MutableInteractionSource() }
    val currentMedia by player.currentMedia.collectAsState()
    val playing by player.isPlaying.collectAsState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ExpressiveLayout.FeatureCorner),
        color = palette.container,
        contentColor = palette.onContainer,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= DailyFeatureWideWidth
            val inset = if (wide) 24.dp else 16.dp
            val innerWidth = (maxWidth - inset * 2).coerceAtLeast(0.dp)
            val fontScale = LocalDensity.current.fontScale
            val sideArt = innerWidth >= 248.dp * fontScale.coerceAtLeast(1f)
            // The artwork grows, not the text measure. Small panes and large type put the art on
            // its own line rather than leaving the title a few characters of space.
            val artworkWidth = when {
                wide && sideArt -> (innerWidth * 0.42f).coerceAtMost(272.dp)
                sideArt -> (innerWidth * 0.46f).coerceAtMost(208.dp)
                else -> innerWidth.coerceAtMost(176.dp)
            }
            HomeGallery(
                count = queue.size,
                style = HomeGalleryStyle.Track,
                gutter = inset,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                header = { pager ->
                    Column(Modifier.fillMaxWidth().padding(start = inset, top = inset, end = inset)) {
                        if (sideArt) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    DailyPicksTitle(queue.size, palette, wide, Modifier.homeControl())
                                    if (wide) DailyPicksActions(palette, onPlay, onBrowse, playInteraction, Modifier.homeControl())
                                }
                                DailyPicksArtwork(covers, artworkWidth, artworkAccent, playInteraction)
                            }
                        } else {
                            DailyPicksTitle(queue.size, palette, wide, Modifier.homeControl())
                            if (artworkWidth >= 48.dp) {
                                Box(Modifier.align(Alignment.End).padding(top = 8.dp)) {
                                    DailyPicksArtwork(covers, artworkWidth, artworkAccent, playInteraction)
                                }
                            }
                        }
                        if (!wide || !sideArt) {
                            DailyPicksActions(palette, onPlay, onBrowse, playInteraction, Modifier.padding(top = 8.dp).homeControl())
                        }
                        if (pager != null) Box(Modifier.align(Alignment.End).homeControl()) { pager() }
                    }
                },
            ) { index, mask, height ->
                val track = queue[index]
                HomeGalleryCard(
                    index = index,
                    itemKey = track.id,
                    mask = mask,
                    style = HomeGalleryStyle.Track,
                    height = height,
                    cover = track.artworkUri,
                    title = track.title.ifBlank { strings.unknownSong },
                    subtitle = track.artist.ifBlank { strings.unknownArtist },
                    isCurrent = currentMedia?.id == track.id,
                    isPlaying = playing,
                    onClick = { onTrack(index) },
                )
            }
        }
    }
}

/** The principal cover leads; two optional, offset shapes give the artwork cadence, not a grid. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DailyPicksArtwork(
    covers: List<String>,
    width: Dp,
    accent: ArtworkAccent,
    playInteraction: MutableInteractionSource,
) {
    val press by rememberPressMorphProgress(playInteraction)
    val leadShape = rememberMorphShape(MaterialShapes.Slanted, MaterialShapes.Square, press)
    val leadSize = if (covers.size <= 1) width.coerceAtMost(196.dp) else width * 0.76f
    val palette = accent.palette
    Box(Modifier.size(width, leadSize + if (covers.size > 2) width * 0.06f else 0.dp)) {
        ExpressiveArtwork(
            model = covers.firstOrNull(),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterStart).size(leadSize),
            shape = leadShape,
            containerColor = palette.quietContainer,
            contentColor = palette.onQuietContainer,
            onImageLoaded = accent.extract,
        )
        covers.getOrNull(1)?.let { cover ->
            ExpressiveArtwork(
                model = cover,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).size(width * 0.31f, leadSize * 0.55f),
                shape = MaterialShapes.Arch.toShape(),
                containerColor = palette.quietContainer,
                contentColor = palette.onQuietContainer,
            )
        }
        covers.getOrNull(2)?.let { cover ->
            ExpressiveArtwork(
                model = cover,
                contentDescription = null,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = width * 0.05f).size(width * 0.36f),
                shape = CircleShape,
                containerColor = palette.quietContainer,
                contentColor = palette.onQuietContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DailyPicksTitle(
    count: Int,
    palette: ContentAccentPalette,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = strings.dailyPicks,
            style = if (wide) MaterialTheme.typography.displaySmallEmphasized else MaterialTheme.typography.headlineMediumEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        Text(strings.songCount(count), style = MaterialTheme.typography.bodyMedium, color = palette.secondaryOnContainer)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DailyPicksActions(
    palette: ContentAccentPalette,
    onPlay: () -> Unit,
    onBrowse: () -> Unit,
    playInteraction: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlay,
            interactionSource = playInteraction,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
        ) {
            Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(strings.playAll)
        }
        TextButton(
            onClick = onBrowse,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = palette.onContainer),
        ) {
            Text(strings.browseDailyPicks)
            Icon(AppIcons.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun HomePlaylists(
    items: List<RecommendResourceRecommend>,
    isLoading: Boolean,
    isFromCache: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    gutter: Dp,
    modifier: Modifier = Modifier,
) {
    val title = strings.recommendedPlaylists
    val showItems = errorMessage == null && !isLoading && items.isNotEmpty()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val foreground = Modifier.padding(horizontal = gutter).homeControl()
    HomeGallery(
        count = if (showItems) items.size else 0,
        style = HomeGalleryStyle.Playlist,
        gutter = gutter,
        modifier = modifier,
        header = { pager ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveSectionTitle(text = title, modifier = foreground, action = pager)
                if (isFromCache) {
                    ExpressiveCacheHint(isRefreshing = isRefreshing, modifier = foreground)
                }
                when {
                    errorMessage != null -> ExpressiveStatePanel(
                        title = strings.sectionLoadFailed(title),
                        message = errorMessage,
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        actionLabel = strings.retry,
                        onAction = onRetry,
                        modifier = foreground,
                    )
                    isLoading -> ExpressiveLoadingState(label = strings.sectionLoading(title), modifier = foreground)
                    items.isEmpty() -> ExpressiveStatePanel(
                        title = strings.sectionEmpty(title),
                        message = strings.sectionEmptyTapReload,
                        icon = AppIcons.QueueMusic,
                        actionLabel = strings.reload,
                        onAction = onRetry,
                        modifier = foreground,
                    )
                }
            }
        },
    ) { index, mask, height ->
        val item = items[index]
        HomeGalleryCard(
            index = index,
            itemKey = item.id.toString(),
            mask = mask,
            style = HomeGalleryStyle.Playlist,
            height = height,
            cover = item.picUrl,
            title = item.name.ifBlank { strings.untitledPlaylist },
            onClick = { onOpenPlaylist(item.id) },
        )
    }
    if (showItems) {
        TextButton(onClick = { showAll = true }, modifier = foreground) {
            Text(strings.viewAllRecommendedPlaylists)
            Icon(AppIcons.KeyboardArrowRight, contentDescription = null)
        }
    }
    if (showItems && showAll) {
        RecommendedPlaylistsDialog(
            items = items,
            onDismiss = { showAll = false },
            onSelect = { id ->
                showAll = false
                onOpenPlaylist(id)
            },
        )
    }
}

/** A vertical equivalent of the gallery without a new navigation destination or duplicate data. */
@Composable
private fun RecommendedPlaylistsDialog(
    items: List<RecommendResourceRecommend>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                ExpressiveSectionTitle(
                    text = strings.recommendedPlaylists,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items) { item ->
                        Surface(
                            onClick = { onSelect(item.id) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
                        ) {
                            Row(
                                Modifier.heightIn(min = 56.dp).padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ExpressiveArtwork(
                                    model = item.picUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    placeholderIcon = AppIcons.QueueMusic,
                                )
                                Text(item.name.ifBlank { strings.untitledPlaylist }, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp)) {
                    Text(strings.close)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecognitionFeature(onClick: () -> Unit, featured: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val openRecognitionLabel = strings.openSongRecognition
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (featured) ExpressiveLayout.FeatureCorner else ExpressiveLayout.ConnectedOuterCorner),
        color = if (featured) scheme.secondaryContainer else scheme.surfaceContainerLow,
        contentColor = if (featured) scheme.onSecondaryContainer else scheme.onSurface,
        modifier = modifier.fillMaxWidth().semantics { role = Role.Button; contentDescription = openRecognitionLabel },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = if (featured) 24.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveFeatureIcon(
                icon = AppIcons.Mic,
                containerColor = if (featured) scheme.secondary else scheme.surfaceContainerHigh,
                contentColor = if (featured) scheme.onSecondary else scheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = strings.songRecognition,
                    style = if (featured) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text = strings.songRecognitionCardHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (featured) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                )
            }
            Icon(AppIcons.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun HomeAccountAvatar(avatarUrl: String?, nickname: String, onOpenMy: () -> Unit) {
    val label = strings.openUserProfile(nickname.ifBlank { strings.me })
    Surface(
        onClick = onOpenMy,
        shape = CircleShape,
        modifier = Modifier.size(48.dp).semantics { role = Role.Button; contentDescription = label },
    ) {
        ExpressiveArtwork(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            placeholderIcon = AppIcons.Person,
        )
    }
}

@Composable
private fun HomeSearchEntry(onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurfaceVariant,
        modifier = modifier.semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Search, contentDescription = null)
            Text(SearchPlaceholder, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// A ruler lets each foreground group find the inset in its own coordinates at placement time,
// including when a lazy item moves. The shape clips both drawing and hit testing; an opaque
// overlay on the list would hide art and leave invisible controls behind it.
private val HomeControlTop = HorizontalRuler()

private fun Modifier.homeControlBoundary(top: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height, rulers = { HomeControlTop provides top.toPx() }) {
        placeable.placeRelative(0, 0)
    }
}

internal fun Modifier.homeControl(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        // Reading the ruler tracks the provider; also track this item's position so a LazyColumn
        // move (including moving a cached item back on screen) recomputes its local clip.
        val origin = coordinates?.localToRoot(Offset.Zero)
        val top = if (origin?.y?.isFinite() == true) {
            HomeControlTop.current(0f).coerceIn(0f, placeable.height.toFloat())
        } else 0f
        // Keep the child placed even when completely under the bar. Unplacing it can stop
        // Compose notifying it when the lazy item next moves, leaving a blank heading on return.
        placeable.placeRelativeWithLayer(0, 0) {
            clip = top > 0f
            shape = if (clip) HomeControlClip(top) else RectangleShape
            // An empty outline alone does not suppress drawing on every graphics backend.
            alpha = if (top >= size.height) 0f else 1f
        }
    }
}

private data class HomeControlClip(val top: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(0f, top.coerceAtMost(size.height), size.width, size.height))
}

/** Retain the root's icon contrast without painting a new, hard app-bar strip. */
private fun Modifier.homeStatusFade(statusTop: Dp, surface: Color): Modifier = drawWithCache {
    val barHeight = statusTop.toPx().coerceAtMost(size.height)
    val fadeEnd = (barHeight + 16.dp.toPx()).coerceAtMost(size.height)
    val fade = if (barHeight > 0f) {
        Brush.verticalGradient(
            0f to surface.copy(alpha = 0.92f),
            (barHeight / fadeEnd) to surface.copy(alpha = 0.82f),
            1f to surface.copy(alpha = 0f),
            endY = fadeEnd,
        )
    } else null
    onDrawWithContent {
        drawContent()
        if (fade != null) drawRect(fade, size = Size(size.width, fadeEnd))
    }
}

internal val DailySource: UiText = UiText { it.dailyPicks }
internal val SearchEntryMaxWidth = 720.dp
private val DailyFeatureWideWidth = 600.dp
