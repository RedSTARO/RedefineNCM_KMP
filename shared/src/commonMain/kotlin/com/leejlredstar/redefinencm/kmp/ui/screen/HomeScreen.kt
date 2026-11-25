package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalCompactAppBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveCacheHint
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveFeatureIcon
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
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
import org.koin.compose.koinInject

internal fun replaceQueueWithDailyRecommendations(
    player: PlatformPlayer,
    songs: List<SongDetailSongs>,
) {
    if (songs.isEmpty()) return
    PlaybackSource.set(DailySource)
    player.setQueue(songs.map { it.toMediaInfo() }, 0)
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val dailyPreview = remember(dailySongs) { dailySongs.take(DailyPreviewCount) }
    val playWholeList = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    val resources = recommendResource?.recommend.orEmpty()
    val listState = rememberLazyListState()

    ExpressivePage(contentWindowInsets = WindowInsets.statusBars) {
        val gutter = LocalExpressivePageGutter.current
        val sectionInset = Modifier.padding(horizontal = gutter)
        val compactHeight = LocalCompactAppBar.current
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Leave the floating player/navigation clearance at the end of the scrollable content.
            contentPadding = PaddingValues(top = 8.dp, bottom = scaffoldPadding.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "search") {
                Column(
                    modifier = sectionInset.fillMaxWidth().padding(bottom = 20.dp),
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
                    RecognitionFeature(onOpenRecognition, featured = true, modifier = sectionInset)
                }
                item(key = "sign-in") {
                    ExpressiveStatePanel(
                        title = if (sessionExpired) strings.signInExpiredTitle else strings.signInToSeeRecommendationsTitle,
                        message = if (sessionExpired) strings.signInExpiredMessage else strings.signInToSeeRecommendationsMessage,
                        icon = AppIcons.Person,
                        actionLabel = strings.signIn,
                        onAction = onOpenLogin,
                        modifier = sectionInset.padding(top = 16.dp),
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
                            if (songsFromCache) ExpressiveCacheHint(isRefreshing = accountLoading)
                        }
                        error != null -> ExpressiveStatePanel(
                            title = strings.dailyPicksLoadFailed,
                            message = error.text,
                            icon = AppIcons.Refresh,
                            tone = ExpressiveStateTone.Error,
                            actionLabel = strings.retry,
                            onAction = viewModel::retryAccountData,
                            modifier = sectionInset,
                        )
                        accountLoading && recommend == null -> ExpressiveLoadingState(
                            label = strings.dailyPicksLoading,
                            modifier = sectionInset,
                        )
                        else -> ExpressiveStatePanel(
                            title = strings.dailyPicksEmpty,
                            message = strings.sectionEmptyTapReload,
                            icon = AppIcons.MusicNote,
                            actionLabel = strings.reload,
                            onAction = viewModel::retryAccountData,
                            modifier = sectionInset,
                        )
                    }
                }
                itemsIndexed(dailyPreview, key = { index, song -> "preview-$index-${song.id}" }) { index, song ->
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

                item(key = "recommended-playlists") {
                    Column(sectionInset) {
                        SectionWithCarousel(
                            title = strings.recommendedPlaylists,
                            items = resources,
                            isLoading = accountLoading && recommendResource == null,
                            isFromCache = resourceFromCache,
                            isRefreshing = accountLoading,
                            errorMessage = (resourceLoadError ?: accountLoadError)?.text?.takeIf { resources.isEmpty() },
                            onRetry = viewModel::retryAccountData,
                            key = { resource -> resource.id },
                            itemContent = { resource ->
                                RecommendSquareCard(
                                    picUrl = resource.picUrl,
                                    text = resource.name,
                                    onClick = { onOpenPlaylist(resource.id) },
                                )
                            },
                        )
                    }
                }
                item(key = "music-tools") {
                    // When personal recommendations can be shown, music tools follow them.
                    RecognitionFeature(onOpenRecognition, featured = false, modifier = sectionInset.padding(top = 20.dp))
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
    val covers = remember(songs) { songs.asSequence().map { it.al.picUrl }.filter(String::isNotBlank).distinct().take(3).toList() }
    val leadCover = covers.firstOrNull()
    val artworkAccent = rememberArtworkAccent(requestKey = leadCover, label = "dailyPicksAccent")
    val palette = artworkAccent.palette

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ExpressiveLayout.FeatureCorner),
        color = palette.container,
        contentColor = palette.onContainer,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 720.dp
            val inset = if (wide) 24.dp else 16.dp
            val innerWidth = (maxWidth - inset * 2).coerceAtLeast(0.dp)
            val coverShape = RoundedCornerShape(ExpressiveLayout.FeatureCorner - inset)
            val cover: @Composable (Modifier) -> Unit = { modifier ->
                ExpressiveArtwork(
                    model = leadCover,
                    contentDescription = null,
                    modifier = modifier,
                    shape = coverShape,
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
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DailyPicksTitle(songs.size, palette, wide = true)
                        DailyPicksActions(palette, onPlay, onBrowse)
                    }
                    val leadSize = (innerWidth / 3).coerceAtMost(224.dp)
                    cover(Modifier.size(leadSize))
                    // On wider panes a small pair of covers previews the variety in the queue.
                    if (covers.size >= 3) {
                        val smallSize = (leadSize - 8.dp) / 2
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            covers.drop(1).forEach { url ->
                                ExpressiveArtwork(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier.size(smallSize),
                                    shape = coverShape,
                                )
                            }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(inset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DailyPicksTitle(songs.size, palette, wide = false, modifier = Modifier.weight(1f))
                        if (innerWidth >= 176.dp) cover(Modifier.size((innerWidth * 0.36f).coerceAtMost(144.dp)))
                    }
                    DailyPicksActions(palette, onPlay, onBrowse)
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
private fun DailyPicksActions(palette: ContentAccentPalette, onPlay: () -> Unit, onBrowse: () -> Unit) {
    FlowRow(
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

internal val DailySource: UiText = UiText { it.dailyPicks }
internal val SearchEntryMaxWidth = 720.dp
private const val DailyPreviewCount = 2
