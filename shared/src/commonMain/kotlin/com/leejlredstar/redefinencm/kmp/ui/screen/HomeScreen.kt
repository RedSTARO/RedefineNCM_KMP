package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.HorizontalRuler
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.api.dto.RecommendResourceRecommend
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
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
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import kotlinx.coroutines.launch
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
    val dailyPreview = remember(dailySongs) { dailySongs.take(DailyPreviewCount) }
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
                        modifier = Modifier.widthIn(max = SearchEntryMaxWidth).fillMaxWidth(),
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
                                songs = dailySongs,
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
                itemsIndexed(dailyPreview, key = { index, song -> "preview-$index-${song.id}" }) { index, song ->
                    Box(Modifier.homeControl()) {
                        SongRow(
                            index = index,
                            title = song.name,
                            artist = song.ar.joinToString(" / ") { it.name },
                            artworkUri = song.al.picUrl,
                            shape = connectedListItemShape(index, dailyPreview.size),
                            mediaId = dailyQueue[index].id,
                            album = song.al.name,
                            durationMs = song.dt,
                            onClick = { playFromList(player, dailyQueue, index, playWholeList, source = DailySource) },
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
                        modifier = sectionInset.padding(top = 20.dp),
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

@Composable
private fun DailyPicksFeature(
    songs: List<SongDetailSongs>,
    onPlay: () -> Unit,
    onBrowse: () -> Unit,
) {
    val leadCover = remember(songs) { songs.firstOrNull { it.al.picUrl.isNotBlank() }?.al?.picUrl }
    val artworkAccent = rememberArtworkAccent(requestKey = leadCover, label = "dailyPicksAccent")
    val palette = artworkAccent.palette

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ExpressiveLayout.FeatureCorner),
        color = palette.container,
        contentColor = palette.onContainer,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= DailyFeatureWideWidth
            val showCover = maxWidth >= DailyArtworkMinWidth
            val inset = if (wide) 24.dp else 16.dp
            // Use pane width, not the viewport left over above the keyboard. The cover serves as
            // the one visual focus; the bounded size leaves room for translated labels to wrap.
            val coverSize = ((maxWidth - inset * 2) * 0.32f).coerceIn(0.dp, if (wide) 152.dp else 96.dp)
            val cover: @Composable () -> Unit = {
                ExpressiveArtwork(
                    model = leadCover,
                    contentDescription = null,
                    modifier = Modifier.size(coverSize),
                    shape = CircleShape,
                    containerColor = palette.quietContainer,
                    contentColor = palette.onQuietContainer,
                    onImageLoaded = artworkAccent.extract,
                )
            }
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(inset),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f).homeControl(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DailyPicksTitle(songs.size, palette, wide = true)
                        DailyPicksActions(palette, onPlay, onBrowse)
                    }
                    cover()
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(inset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DailyPicksTitle(
                            songs.size, palette, wide = false, modifier = Modifier.weight(1f).homeControl(),
                        )
                        if (showCover) cover()
                    }
                    DailyPicksActions(palette, onPlay, onBrowse, modifier = Modifier.homeControl())
                }
            }
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
    modifier: Modifier = Modifier,
) {
    val title = strings.recommendedPlaylists
    val listState = rememberLazyListState()
    val pager = !getPlatform().isMobile && items.isNotEmpty()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { (RecommendedPlaylistWidth + 8.dp).toPx() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpressiveSectionTitle(
            text = title,
            modifier = Modifier.homeControl(),
            action = if (pager) {
                {
                    Row {
                        IconButton(
                            onClick = { scope.launch { listState.animateScrollBy(-step) } },
                            enabled = listState.canScrollBackward,
                        ) { Icon(AppIcons.KeyboardArrowLeft, contentDescription = strings.carouselScrollBack) }
                        IconButton(
                            onClick = { scope.launch { listState.animateScrollBy(step) } },
                            enabled = listState.canScrollForward,
                        ) { Icon(AppIcons.KeyboardArrowRight, contentDescription = strings.carouselScrollForward) }
                    }
                }
            } else null,
        )
        if (isFromCache) {
            ExpressiveCacheHint(isRefreshing = isRefreshing, modifier = Modifier.homeControl())
        }
        when {
            errorMessage != null -> ExpressiveStatePanel(
                title = strings.sectionLoadFailed(title),
                message = errorMessage,
                icon = AppIcons.Refresh,
                tone = ExpressiveStateTone.Error,
                actionLabel = strings.retry,
                onAction = onRetry,
                modifier = Modifier.homeControl(),
            )
            isLoading -> ExpressiveLoadingState(label = strings.sectionLoading(title), modifier = Modifier.homeControl())
            items.isEmpty() -> ExpressiveStatePanel(
                title = strings.sectionEmpty(title),
                message = strings.sectionEmptyTapReload,
                icon = AppIcons.QueueMusic,
                actionLabel = strings.reload,
                onAction = onRetry,
                modifier = Modifier.homeControl(),
            )
            else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                val itemWidth = minOf(maxWidth, RecommendedPlaylistWidth)
                LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(items, key = { index, item -> "$index-${item.id}" }) { _, item ->
                        HomePlaylistCard(item, Modifier.width(itemWidth)) { onOpenPlaylist(item.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePlaylistCard(item: RecommendResourceRecommend, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = rememberArtworkAccent(
        requestKey = item.picUrl,
        fallback = scheme.tertiaryContainer,
        label = "recommendCardAccent",
    )
    val palette = accent.palette
    // The cover remains decorative. Keep the linked text and its touch target in the safe area,
    // using a small cover next to a separate label rather than a title over a square tile.
    Surface(
        onClick = onClick,
        modifier = modifier.homeControl(),
        shape = MaterialTheme.shapes.large,
        color = palette.quietContainer,
        contentColor = palette.onQuietContainer,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveArtwork(
                model = item.picUrl,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                containerColor = palette.container,
                contentColor = palette.onContainer,
                placeholderIcon = AppIcons.QueueMusic,
                onImageLoaded = accent.extract,
            )
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

private fun Modifier.homeControl(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        val top = HomeControlTop.current(0f).coerceIn(0f, placeable.height.toFloat())
        if (top == 0f) {
            placeable.placeRelative(0, 0)
        } else if (top < placeable.height) {
            placeable.placeRelativeWithLayer(0, 0) {
                clip = true
                shape = GenericShape { size: Size, _ -> addRect(Rect(0f, top, size.width, size.height)) }
            }
        }
    }
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
private val DailyArtworkMinWidth = 224.dp
private val RecommendedPlaylistWidth = 240.dp
private const val DailyPreviewCount = 2
