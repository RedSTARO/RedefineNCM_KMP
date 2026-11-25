package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.Image
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveCacheHint
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.downloadQuality
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    scaffoldPadding: PaddingValues = PaddingValues(),
    onOpenDownloads: () -> Unit = {},
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
    localLibrary: LocalLibraryViewModel = koinInject(),
) {
    val detail by viewModel.playlistDetail.collectAsState()
    val tracks by viewModel.playlistSongs.collectAsState()
    val loading by viewModel.playlistLoading.collectAsState()
    val loadError by viewModel.playlistLoadError.collectAsState()
    val detailLoadError by viewModel.playlistDetailLoadError.collectAsState()
    val detailFromCache by viewModel.playlistDetailFromCache.collectAsState()
    val songsFromCache by viewModel.playlistSongsFromCache.collectAsState()
    val localLibraryEnabled by localLibrary.enabled.collectAsState()
    val playlist = detail?.playlist?.takeIf { it.id == playlistId }
    val songs = tracks?.songs.orEmpty()
    val hasCachedContent = detailFromCache || songsFromCache
    val replacePlaylist = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDownloadAll by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        viewModel.openPlaylist(playlistId)
    }

    val queueItems = remember(songs, playlistId) {
        val sourceId = playlistId.toString()
        songs.map { it.toMediaInfo(sourceId = sourceId) }
    }

    // Playback stays on the playlist; the mini player is the way into the player page.
    fun playAll() {
        if (queueItems.isEmpty()) return
        PlaybackSource.set(playlistSourceLabel(playlist?.name))
        player.setQueue(queueItems, 0)
        viewModel.updatePlaylistPlaycount(playlistId)
    }

    fun playSong(index: Int) {
        playFromList(
            player,
            queueItems,
            index,
            wholeList = replacePlaylist,
            source = playlistSourceLabel(playlist?.name),
        )
        viewModel.updatePlaylistPlaycount(playlistId)
    }

    // While the songs load, their count is not known yet; it is not 0.
    val trackCountText = playlist?.trackCount?.takeIf { it > 0L }?.let { strings.playlistSongCount(it) }
        ?: tracks?.let { strings.playlistSongCount(songs.size) }
    val title = playlist?.name ?: strings.playlist
    val cover = playlist?.coverImgUrl?.takeIf { it.isNotBlank() }
    val accent = rememberArtworkAccent(requestKey = cover, label = "playlistAccent")

    CatalogPage(
        title = title,
        onBack = onBack,
        scaffoldPadding = scaffoldPadding,
        scrollToTopOnTitleClick = true,
        overlay = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = scaffoldPadding.calculateBottomPadding()),
            )
        },
    ) {
        // With no metadata or tracks, let the loading/error state be the focus, not a placeholder hero.
        if (playlist != null || songs.isNotEmpty()) {
            item(key = "playlist-header") {
                PlaylistHeader(
                    coverUrl = cover,
                    title = title,
                    creator = playlist?.creator?.nickname.orEmpty(),
                    trackCountText = trackCountText,
                    actionsEnabled = songs.isNotEmpty(),
                    accentPalette = accent.palette,
                    onImageLoaded = accent.extract,
                    onPlayAll = ::playAll,
                    onDownloadAll = { confirmDownloadAll = true },
                    onSaveLocal = if (localLibraryEnabled) {
                        { localLibrary.requestAddition(queueItems, suggestedName = title) }
                    } else null,
                )
            }
        }
        if (hasCachedContent) {
            item(key = "playlist-cache-hint") {
                ExpressiveCacheHint(
                    isRefreshing = loading,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current, vertical = 8.dp),
                )
            }
        }
        if (!loading && loadError == null && detailLoadError != null) {
            item(key = "playlist-detail-error") {
                ExpressiveStatePanel(
                    title = strings.playlistDetailsUnavailable,
                    message = detailLoadError?.text.orEmpty(),
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    actionLabel = strings.retry,
                    onAction = { viewModel.fetchPlaylistDetail(playlistId) },
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current, vertical = 8.dp),
                )
            }
        }
        when {
            loading && tracks == null -> item(key = "playlist-loading") {
                ExpressiveLoadingState(
                    label = strings.playlistLoading,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            loadError != null && tracks == null -> item(key = "playlist-error") {
                ExpressiveStatePanel(
                    title = strings.playlistLoadFailed,
                    message = loadError?.text.orEmpty(),
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    actionLabel = strings.retry,
                    onAction = { viewModel.fetchPlaylistDetail(playlistId) },
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            tracks != null && songs.isEmpty() -> item(key = "playlist-empty") {
                ExpressiveStatePanel(
                    title = strings.playlistNoSongsYet,
                    message = strings.playlistEmptyHint,
                    icon = AppIcons.QueueMusic,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            else -> itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
            ) { index, song ->
                SongRow(
                    index = index,
                    title = song.name,
                    artist = song.ar.joinToString(" / ") { it.name },
                    artworkUri = song.al.picUrl,
                    shape = connectedListItemShape(index, songs.size),
                    onClick = { playSong(index) },
                    songId = song.id,
                    accentColor = accent.color,
                    durationMs = song.dt,
                    album = song.al.name,
                    badge = songFeeBadge(song.fee),
                    actions = rememberSongRowActions(
                        media = queueItems.getOrNull(index) ?: song.toMediaInfo(playlistId.toString()),
                        neteaseSong = song,
                        playlistId = playlistId,
                    ),
                )
            }
        }
    }

    if (confirmDownloadAll) {
        val quality = remember { settings.downloadQuality() }
        AlertDialog(
            onDismissRequest = { confirmDownloadAll = false },
            icon = { Icon(AppIcons.Download, contentDescription = null) },
            title = { Text(strings.downloadPlaylistTitle(title)) },
            text = { Text(strings.downloadPlaylistMessage(songs.size, quality.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDownloadAll = false
                        viewModel.onDownloadPlaylistClick(playlistId)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = strings.addedToDownloadQueue(songs.size),
                                actionLabel = strings.view,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) onOpenDownloads()
                        }
                    },
                ) { Text(strings.download) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownloadAll = false }) { Text(strings.cancel) }
            },
        )
    }
}

internal fun playlistSourceLabel(name: String?): UiText? =
    name?.takeIf { it.isNotBlank() }?.let { playlistName -> UiText { it.playbackSourcePlaylist(playlistName) } }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistHeader(
    coverUrl: String?,
    title: String,
    creator: String,
    trackCountText: String?,
    actionsEnabled: Boolean,
    accentPalette: ContentAccentPalette,
    onImageLoaded: (Image) -> Unit,
    onPlayAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onSaveLocal: (() -> Unit)?,
) {
    val inset = LocalExpressivePageGutter.current
    CatalogArtworkHeader(
        modifier = Modifier
            .padding(start = inset, end = inset, bottom = 16.dp)
            .background(
                brush = Brush.linearGradient(listOf(accentPalette.container, accentPalette.quietContainer)),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(ExpressiveLayout.FeatureCorner),
            )
            .padding(horizontal = inset, vertical = 16.dp),
        artwork = { modifier ->
            ExpressiveArtwork(
                model = coverUrl,
                contentDescription = if (coverUrl == null) null else strings.playlistCover,
                modifier = modifier,
                shape = MaterialTheme.shapes.large,
                containerColor = accentPalette.pageStart,
                contentColor = accentPalette.onPageStart,
                placeholderIcon = AppIcons.QueueMusic,
                onImageLoaded = onImageLoaded,
            )
        },
        details = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLargeEmphasized,
                    color = accentPalette.onContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                val metadata = listOfNotNull(creator.takeIf { it.isNotBlank() }, trackCountText)
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentPalette.secondaryOnContainer,
                    )
                }
            }
        },
        actions = if (actionsEnabled) {
            { PlaylistActions(accentPalette, onPlayAll, onDownloadAll, onSaveLocal) }
        } else null,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistActions(
    palette: ContentAccentPalette,
    onPlayAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onSaveLocal: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotionEnabled()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reducedMotion) snap() else MotionScheme.standard().fastSpatialSpec(),
        label = "playlistActionMenuArrow",
    )
    BoxWithConstraints {
        val medium = maxWidth >= 220.dp
        val height = if (medium) SplitButtonDefaults.MediumContainerHeight else SplitButtonDefaults.SmallContainerHeight
        // This Compose layout measures the leading button first. Reserve the trailing target before
        // measuring the translated label, so both halves remain reachable at a large text size.
        val trailingWidth = if (medium) 56.dp else 48.dp
        val leadingMax = (maxWidth - trailingWidth - SplitButtonDefaults.Spacing).coerceAtLeast(0.dp)
        val colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onPlayAll,
                    modifier = Modifier.widthIn(max = leadingMax).heightIn(min = height),
                    shapes = SplitButtonDefaults.leadingButtonShapesFor(height),
                    contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(height),
                    colors = colors,
                ) {
                    Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(height)))
                    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                    Text(strings.playAll, style = if (medium) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.labelLargeEmphasized)
                }
            },
            trailingButton = {
                Box {
                    SplitButtonDefaults.TrailingButton(
                        checked = expanded,
                        onCheckedChange = { expanded = it },
                        modifier = Modifier.width(trailingWidth).heightIn(min = height),
                        shapes = SplitButtonDefaults.trailingButtonShapesFor(height),
                        contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(height),
                        colors = colors,
                    ) {
                        Icon(
                            AppIcons.KeyboardArrowDown,
                            contentDescription = strings.moreActions,
                            modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(height)).rotate(arrowRotation),
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, offset = DpOffset(0.dp, 4.dp)) {
                        DropdownMenuItem(
                            text = { Text(strings.downloadAll) },
                            leadingIcon = { Icon(AppIcons.Download, contentDescription = null) },
                            onClick = { expanded = false; onDownloadAll() },
                        )
                        if (onSaveLocal != null) {
                            DropdownMenuItem(
                                text = { Text(strings.saveAsLocalPlaylist) },
                                leadingIcon = { Icon(AppIcons.PlaylistAdd, contentDescription = null) },
                                onClick = { expanded = false; onSaveLocal() },
                            )
                        }
                    }
                }
            },
        )
    }
}
