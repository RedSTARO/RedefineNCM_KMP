package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveCacheHint
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource

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
    val playlist = detail?.playlist?.takeIf { it.id == playlistId }
    val songs = tracks?.songs.orEmpty()
    val hasCachedContent = detailFromCache || songsFromCache
    // 原版 ShowPlaylistDetailPage：点单曲时按 replacePlaylist 设置决定替换整单还是单曲队列
    val replacePlaylist = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDownloadAll by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        viewModel.fetchPlaylistDetail(playlistId)
    }

    val queueItems = remember(songs, playlistId) {
        songs.map { it.toMediaInfo(sourceId = playlistId.toString()) }
    }

    // 播放后停留在歌单页（与原版一致，靠迷你播放条进入全屏播放器）
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

    val trackCountText = when {
        (playlist?.trackCount ?: 0L) == 0L -> strings.playlistSongCount(songs.size)
        else -> playlist?.trackCount?.let { strings.playlistSongCount(it) } ?: "…"
    }
    val title = playlist?.name ?: strings.playlist
    val defaultAccentColor = MaterialTheme.colorScheme.primaryContainer
    var rawAccentColor by remember(playlist?.coverImgUrl, defaultAccentColor) {
        mutableStateOf(defaultAccentColor)
    }
    val animatedAccentColor by animateColorAsState(
        targetValue = rawAccentColor,
        animationSpec = spring(),
        label = "playlistAccent",
    )
    val accentPalette = contentAccentPalette(animatedAccentColor)
    // The header, and the back button in it, scroll away with the list; once it has, a compact
    // bar with the same back button and the title takes its place.
    val headerScrolledAway by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    ExpressivePage(
        accentPalette = accentPalette,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "playlist-header") {
                PlaylistHeader(
                    coverUrl = playlist?.coverImgUrl,
                    title = title,
                    trackCountText = trackCountText,
                    actionsEnabled = songs.isNotEmpty(),
                    accentPalette = accentPalette,
                    onAccentColor = { rawAccentColor = it },
                    onBack = onBack,
                    onPlayAll = { playAll() },
                    onDownloadAll = { confirmDownloadAll = true },
                    // A copy in the local account keeps the list even if the account loses it.
                    onSaveLocal = { localLibrary.requestAddition(queueItems, suggestedName = title) },
                )
            }
            if (hasCachedContent) {
                item(key = "playlist-cache-hint") {
                    ExpressiveCacheHint(
                        isRefreshing = loading,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                        accentPalette = accentPalette,
                        actionLabel = strings.retry,
                        onAction = { viewModel.fetchPlaylistDetail(playlistId) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            when {
                loading && tracks == null -> item(key = "playlist-loading") {
                    ExpressiveLoadingState(
                        label = strings.playlistLoading,
                        accentColor = accentPalette.accent,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                loadError != null && tracks == null -> item(key = "playlist-error") {
                    ExpressiveStatePanel(
                        title = strings.playlistLoadFailed,
                        message = loadError?.text.orEmpty(),
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = accentPalette,
                        actionLabel = strings.retry,
                        onAction = { viewModel.fetchPlaylistDetail(playlistId) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                tracks != null && songs.isEmpty() -> item(key = "playlist-empty") {
                    ExpressiveStatePanel(
                        title = strings.playlistNoSongsYet,
                        message = strings.playlistEmptyHint,
                        icon = AppIcons.QueueMusic,
                        accentPalette = accentPalette,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                else -> itemsIndexed(
                    items = songs,
                    key = { _, song -> song.id },
                ) { i, song ->
                    val media = queueItems.getOrNull(i) ?: song.toMediaInfo(playlistId.toString())
                    SongRow(
                        index = i,
                        title = song.name,
                        artist = song.ar.joinToString(" / ") { it.name },
                        artworkUri = song.al.picUrl,
                        shape = connectedListItemShape(i, songs.size),
                        onClick = { playSong(i) },
                        songId = song.id,
                        accentColor = animatedAccentColor,
                        durationMs = song.dt,
                        album = song.al.name,
                        badge = songFeeBadge(song.fee),
                        actions = rememberSongRowActions(
                            media = media,
                            neteaseSong = song,
                            playlistId = playlistId,
                        ),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = headerScrolledAway,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PlaylistCompactBar(
                title = title,
                accentPalette = accentPalette,
                onBack = onBack,
                onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = scaffoldPadding.calculateBottomPadding()),
        )
    }

    if (confirmDownloadAll) {
        val quality = remember {
            val saved = settings.getString(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)
            SoundQuality.entries.firstOrNull { it.name == saved } ?: SoundQuality.STANDARD
        }
        AlertDialog(
            onDismissRequest = { confirmDownloadAll = false },
            icon = { Icon(AppIcons.Download, contentDescription = null) },
            title = { Text(strings.downloadPlaylistTitle(title)) },
            text = {
                Text(
                    strings.downloadPlaylistMessage(songs.size, quality.displayName),
                )
            },
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

/** A short marker for songs a free account cannot play in full. */
internal fun songFeeBadge(fee: Int): String? = when (fee) {
    1 -> "VIP"
    4 -> strings.paid
    else -> null
}

@Composable
private fun PlaylistCompactBar(
    title: String,
    accentPalette: ContentAccentPalette,
    onBack: () -> Unit,
    onScrollToTop: () -> Unit,
) {
    Surface(
        color = accentPalette.pageStart,
        contentColor = accentPalette.onPageStart,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(AppIcons.ArrowBack, contentDescription = strings.back)
            }
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = onScrollToTop,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColors(contentColor = accentPalette.onPageStart),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    coverUrl: String?,
    title: String,
    trackCountText: String,
    actionsEnabled: Boolean,
    accentPalette: ContentAccentPalette,
    onAccentColor: (Color) -> Unit,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onSaveLocal: () -> Unit,
) {
    val fallbackAccentColor = MaterialTheme.colorScheme.primaryContainer
    val extractAccent = rememberThemeColorExtractor(
        requestKey = coverUrl,
        onAccentColor = onAccentColor,
    )
    val cover: @Composable (Modifier) -> Unit = { modifier ->
        AsyncImage(
            model = coverUrl,
            contentDescription = strings.playlistCover,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(200.dp)
                .clip(MaterialTheme.shapes.extraLarge),
            onSuccess = { state -> extractAccent(state.result.image) },
            onError = { onAccentColor(fallbackAccentColor) },
        )
    }
    val info: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accentPalette.onQuietContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = trackCountText,
                style = MaterialTheme.typography.labelLarge,
                color = accentPalette.secondaryOnQuietContainer,
            )
            Spacer(Modifier.height(20.dp))
            // Two plain buttons. The download is not the trailing half of a split button: Material
            // reserves that slot for opening a menu of the leading action's variants.
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPlayAll,
                    enabled = actionsEnabled,
                    shape = CircleShape,
                    modifier = Modifier.height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentPalette.accent,
                        contentColor = accentPalette.onAccent,
                    ),
                ) {
                    Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.playAll, style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalButton(
                    onClick = onDownloadAll,
                    enabled = actionsEnabled,
                    shape = CircleShape,
                    modifier = Modifier.height(52.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accentPalette.container,
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.downloadAll)
                }
                FilledTonalIconButton(
                    onClick = onSaveLocal,
                    enabled = actionsEnabled,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container,
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(AppIcons.Add, contentDescription = strings.saveAsLocalPlaylist)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Wide windows lay the cover and its details side by side, the way a desktop album
        // header reads; narrow ones keep the stacked phone layout.
        val sideBySide = maxWidth >= 720.dp
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Surface(
                        shape = CircleShape,
                        color = accentPalette.quietContainer.copy(alpha = 0.72f),
                        contentColor = accentPalette.onQuietContainer,
                    ) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = strings.back,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
            if (sideBySide) {
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    cover(Modifier)
                    Spacer(Modifier.width(28.dp))
                    info(Modifier.weight(1f))
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    cover(Modifier)
                }
                Spacer(Modifier.height(24.dp))
                info(Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
