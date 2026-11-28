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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.Image
import com.leejlredstar.redefinencm.kmp.AppNavigationRequests
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserPlaylistEach
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderTrack
import com.leejlredstar.redefinencm.kmp.data.provider.neteaseFeeTag
import com.leejlredstar.redefinencm.kmp.data.toPlayerMediaInfo
import com.leejlredstar.redefinencm.kmp.download.DownloadTaskStatus
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.compactCount
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ProviderBadge
import com.leejlredstar.redefinencm.kmp.ui.component.formatPlaybackDuration
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.legibleAccentFor
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import org.koin.compose.koinInject

/** Map an API song DTO to the player's [MediaInfo] (placeholder URI resolved at play time). */
fun SongDetailSongs.toMediaInfo(
    sourceId: String = "",
): MediaInfo = toPlayerMediaInfo(sourceId)

/** The same, for a track that may have come from any provider. */
fun ProviderTrack.toMediaInfo(
    sourceId: String = "",
): MediaInfo = toPlayerMediaInfo(sourceId)

/** A short marker for songs a free account cannot play in full. */
internal fun songFeeBadge(fee: Int): String? = neteaseFeeTag(fee)?.label

/** The song menu's entry that opens the add-to-local-playlist dialog. */
internal val AddToLocalPlaylistLabel: String get() = strings.addToLocalPlaylist

/** One entry of a song row's overflow menu. */
data class SongRowAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Plays one song out of a list.
 *
 * With [wholeList] the list becomes the queue and playback starts at [index]; otherwise the song
 * alone replaces the queue. Every list in the app goes through here so the setting that picks
 * between the two means the same thing on the home page, in search results and in playlists.
 */
fun playFromList(
    player: PlatformPlayer,
    items: List<MediaInfo>,
    index: Int,
    wholeList: Boolean,
    source: UiText? = null,
) {
    val song = items.getOrNull(index) ?: return
    PlaybackSource.set(source)
    if (wholeList) {
        player.setQueue(items, index)
    } else {
        player.setQueue(listOf(song), 0)
    }
}

/** The actions a song row offers for a song the player can take: queue it, download it, share it. */
@Composable
fun rememberSongRowActions(
    media: MediaInfo,
    neteaseSong: SongDetailSongs? = null,
    playlistId: Long? = null,
): List<SongRowAction> {
    val player = koinInject<PlatformPlayer>()
    val downloadManager = koinInject<SongDownloadManager>()
    val providers = koinInject<MusicProviderRegistry>()
    val localLibrary = koinInject<LocalLibraryViewModel>()
    val localLibraryEnabled by localLibrary.enabled.collectAsState()
    // LocalClipboard needs a platform ClipEntry and common code has no plain-text factory for
    // one; the text-only manager does the same job on every target.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val language = I18n.language
    return remember(
        media, neteaseSong, playlistId, player, downloadManager, providers, localLibrary, clipboard,
        localLibraryEnabled, language,
    ) {
        buildList {
            add(SongRowAction(strings.songActionAddToQueue, AppIcons.PlaylistAdd) { player.addToQueue(media) })
            // A song of any provider can go into a local playlist, while the local account is on.
            if (localLibraryEnabled) {
                add(SongRowAction(AddToLocalPlaylistLabel, AppIcons.Add) { localLibrary.requestAddition(listOf(media)) })
            }
            if (neteaseSong != null) {
                add(
                    SongRowAction(strings.download, AppIcons.Download) {
                        downloadManager.enqueueSongs(listOf(neteaseSong), playlistId)
                    },
                )
            }
            // Every provider with a web page for the song gets the link, not only NetEase.
            providers.shareUrl(media.id)?.let { url ->
                add(
                    SongRowAction(strings.songActionCopyLink, AppIcons.Link) {
                        clipboard.setText(AnnotatedString(url))
                    },
                )
            }
            if (neteaseSong != null) {
                // The names on a song lead to their pages.
                neteaseSong.ar.filter { it.id != 0L }.take(MaxArtistActions).forEach { artist ->
                    add(
                        SongRowAction(strings.artistWithName(artist.name), AppIcons.Person) {
                            AppNavigationRequests.openArtist(artist.id)
                        },
                    )
                }
                if (neteaseSong.al.id != 0L) {
                    add(
                        SongRowAction(strings.albumWithName(neteaseSong.al.name), AppIcons.Album) {
                            AppNavigationRequests.openAlbum(neteaseSong.al.id)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Segmented multi-action song row. Its leading ordinal marks playback without depending on color,
 * and extra metadata moves into columns only when the row has room for it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun SongRow(
    index: Int,
    title: String,
    artist: String,
    artworkUri: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    songId: Long? = null,
    accentColor: Color? = null,
    mediaId: String? = songId?.toString(),
    durationMs: Long = 0L,
    album: String = "",
    badge: String? = null,
    /** More labels after [badge]: the providers and entitlements for a merged search row. */
    badges: List<String> = emptyList(),
    actions: List<SongRowAction> = emptyList(),
) {
    val settings = koinInject<PlatformSettings>()
    val downloadManager = koinInject<SongDownloadManager>()
    val player = koinInject<PlatformPlayer>()
    val currentMedia = player.currentMedia.collectAsState()
    val isCurrent by remember(mediaId, currentMedia) {
        derivedStateOf { mediaId != null && currentMedia.value?.id == mediaId }
    }
    val isPlaying = if (isCurrent) player.isPlaying.collectAsState().value else false
    val playingState = if (isCurrent) {
        if (isPlaying) strings.playing else strings.paused
    } else null
    val scheme = MaterialTheme.colorScheme
    val accent = if (isCurrent) rememberArtworkAccent(artworkUri, override = accentColor, label = "songRowAccent") else null
    var loadedCover by remember(artworkUri) { mutableStateOf<coil3.Image?>(null) }
    if (isCurrent && accentColor == null) {
        LaunchedEffect(loadedCover, accent?.extract) {
            loadedCover?.let { accent?.extract?.invoke(it) }
        }
    }
    val palette = accent?.palette
    val container = palette?.container ?: scheme.surfaceContainerLow
    val contentColor = palette?.onContainer ?: scheme.onSurface
    val supportingColor = palette?.secondaryOnContainer ?: scheme.onSurfaceVariant
    var menuOpen by remember { mutableStateOf(false) }
    val openMenu = if (actions.isNotEmpty()) { { menuOpen = true } } else null
    // One press stream drives Material's row shape and the cover's press morph.
    val interactionSource = remember { MutableInteractionSource() }
    val visibleBadges = remember(badge, badges) { listOfNotNull(badge) + badges }

    BoxWithConstraints(Modifier.fillMaxWidth().catalogRowPadding()) {
        val wide = maxWidth >= WideRowMinWidth && LocalDensity.current.fontScale <= 1.3f
        val narrow = maxWidth < 300.dp
        SegmentedListItem(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
                .secondaryClick(enabled = openMenu != null) { menuOpen = true }
                .semantics { if (playingState != null) stateDescription = playingState },
            shapes = ListItemDefaults.shapes(shape = shape),
            colors = ListItemDefaults.segmentedColors(
                containerColor = container,
                contentColor = contentColor,
                leadingContentColor = supportingColor,
                supportingContentColor = supportingColor,
                trailingContentColor = supportingColor,
            ),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(start = 12.dp, end = if (openMenu != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
            interactionSource = interactionSource,
            onLongClick = openMenu,
            onLongClickLabel = if (openMenu != null) strings.moreActions else null,
            leadingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(if (narrow) 24.dp else 32.dp), contentAlignment = Alignment.Center) {
                        if (isCurrent) {
                            Icon(
                                if (isPlaying) AppIcons.GraphicEq else AppIcons.Pause,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    ExpressiveArtwork(
                        model = artworkUri,
                        contentDescription = null,
                        modifier = Modifier.size(if (narrow) 36.dp else 48.dp),
                        shape = MaterialTheme.shapes.small,
                        pressInteractionSource = interactionSource,
                        containerColor = palette?.quietContainer ?: scheme.surfaceContainerHighest,
                        contentColor = palette?.onQuietContainer ?: scheme.onSurfaceVariant,
                        onImageLoaded = { if (accentColor == null) loadedCover = it },
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (songId != null && settings.getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, SettingKeys.SHOW_DOWNLOAD_STATUS_DEFAULT)) {
                        Box(
                            modifier = if (wide) Modifier.width(48.dp) else Modifier,
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            SongDownloadMark(songId, downloadManager, supportingColor)
                        }
                    }
                    if (wide) {
                        Text(
                            text = if (durationMs > 0L) formatPlaybackDuration(durationMs) else "",
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp).width(48.dp),
                        )
                    }
                    if (openMenu != null) {
                        Box {
                            IconButton(onClick = openMenu) {
                                Icon(AppIcons.MoreVert, contentDescription = strings.moreActions, tint = supportingColor)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                actions.forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.label) },
                                        leadingIcon = { Icon(action.icon, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            action.onClick()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { strings.unknownSong },
                        style = if (isCurrent) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
                        maxLines = if (narrow) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = artist.ifBlank { strings.unknownArtist },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = supportingColor,
                    )
                    if (visibleBadges.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            visibleBadges.forEach { label ->
                                ProviderBadge(
                                    label = label,
                                    contentColor = palette?.onQuietContainer ?: scheme.onSurfaceVariant,
                                    containerColor = palette?.quietContainer ?: scheme.surfaceContainerHighest,
                                )
                            }
                        }
                    }
                }
                if (wide) {
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = supportingColor,
                        modifier = Modifier.weight(0.65f),
                    )
                }
            }
        }
    }
}

/** App-chosen minimum for making room for distinct album and duration columns. */
private val WideRowMinWidth = 640.dp

/** Right click opens the same menu a long press does. */
private fun Modifier.secondaryClick(enabled: Boolean, onSecondaryClick: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        event.changes.forEach { it.consume() }
                        onSecondaryClick()
                    }
                }
            }
        }
    }

/**
 * The download mark at the end of a row: downloaded, downloading, or failed (tap to retry).
 * A song that is not downloaded, or whose download the user cancelled, has no mark.
 */
@Composable
private fun SongDownloadMark(
    songId: Long,
    downloadManager: SongDownloadManager,
    statusTint: Color,
) {
    val downloadedCacheVersion = DownloadedSongsCache.version.collectAsState().value
    val downloadTasks = downloadManager.tasks.collectAsState().value
    val taskStatus = remember(songId, downloadTasks) {
        downloadTasks.firstOrNull { it.id == songId }?.status
    }
    val downloaded = remember(songId, downloadedCacheVersion) {
        DownloadedSongsCache.isDownloaded(songId)
    }
    val isActive = taskStatus == DownloadTaskStatus.Queued ||
        taskStatus == DownloadTaskStatus.Resolving ||
        taskStatus == DownloadTaskStatus.Downloading ||
        taskStatus == DownloadTaskStatus.SavingLyrics
    val isFailed = !downloaded && taskStatus == DownloadTaskStatus.Failed
    if (!downloaded && !isActive && !isFailed) return
    if (isFailed) {
        IconButton(onClick = { downloadManager.retry(songId) }) {
            Icon(
                imageVector = AppIcons.ErrorOutline,
                contentDescription = strings.downloadFailedTapToRetry,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    } else {
        Icon(
            imageVector = if (downloaded) AppIcons.DownloadDone else AppIcons.Download,
            contentDescription = if (downloaded) strings.downloaded else strings.downloading,
            tint = statusTint,
            modifier = Modifier.padding(horizontal = 4.dp).size(20.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistCard(
    userPlaylistEach: UserPlaylistEach,
    specialCard: String,
    index: Int,
    count: Int,
    accentColor: Color? = null,
    onClick: () -> Unit,
    onSpecialClick: (() -> Unit)? = null,
    specialActionLoading: Boolean = false,
    /** False for the viewer's own playlists, where the creator line would only repeat their name. */
    showCreator: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val special = specialCard != "no"
    val accent = if (special) {
        rememberArtworkAccent(
            requestKey = userPlaylistEach.coverImgUrl to specialCard,
            fallback = scheme.tertiaryContainer,
            override = accentColor,
            label = "playlistCardAccent",
        )
    } else null
    val interactionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxWidth().catalogRowPadding()) {
        // Measure whether the trailing secondary action can be labeled without taking over the row.
        val heartbeatLabelled = maxWidth >= HeartbeatLabelMinWidth && LocalDensity.current.fontScale <= 1.3f
        val coverSize = if (maxWidth < 300.dp) 48.dp else 56.dp
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(index, count),
            modifier = Modifier.fillMaxWidth(),
            colors = ListItemDefaults.segmentedColors(containerColor = scheme.surfaceContainerLow),
            verticalAlignment = Alignment.CenterVertically,
            interactionSource = interactionSource,
            leadingContent = {
                ExpressiveArtwork(
                    model = userPlaylistEach.coverImgUrl,
                    contentDescription = null,
                    modifier = Modifier.size(coverSize),
                    shape = MaterialTheme.shapes.small,
                    pressInteractionSource = interactionSource,
                    containerColor = accent?.palette?.quietContainer ?: scheme.surfaceContainerHighest,
                    contentColor = accent?.palette?.onQuietContainer ?: scheme.onSurfaceVariant,
                    placeholderIcon = AppIcons.QueueMusic,
                    onImageLoaded = { if (accentColor == null) accent?.extract?.invoke(it) },
                )
            },
            supportingContent = {
                Column {
                    if (showCreator) {
                        Text(userPlaylistEach.creator.nickname, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        text = strings.playlistCardStats(userPlaylistEach.trackCount, compactCount(userPlaylistEach.playCount)),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingContent = {
                when {
                    specialCard == "fav" && onSpecialClick != null -> PlaylistHeartbeatAction(
                        labelled = heartbeatLabelled,
                        loading = specialActionLoading,
                        onClick = onSpecialClick,
                        palette = accent!!.palette,
                    )
                    special -> Icon(
                        if (specialCard == "fav") AppIcons.Favorite else AppIcons.GraphicEq,
                        contentDescription = null,
                        tint = legibleAccentFor(accent!!.palette.accent, scheme.surfaceContainerLow, scheme.surface),
                    )
                    else -> Icon(AppIcons.KeyboardArrowRight, contentDescription = null)
                }
            },
        ) {
            Text(userPlaylistEach.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistHeartbeatAction(labelled: Boolean, loading: Boolean, onClick: () -> Unit, palette: ContentAccentPalette) {
    val state = strings.heartbeatModeStarting
    val loadingSemantics = if (loading) Modifier.semantics { stateDescription = state } else Modifier
    if (labelled) {
        FilledTonalButton(
            onClick = onClick,
            enabled = !loading,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(40.dp, hasStartIcon = true),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.container, contentColor = palette.onContainer),
            modifier = loadingSemantics,
        ) {
            HeartbeatIcon(loading)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(strings.heartbeatMode, maxLines = 1)
        }
    } else {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = !loading,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = palette.container, contentColor = palette.onContainer),
            modifier = loadingSemantics.semantics(mergeDescendants = true) { contentDescription = strings.heartbeatMode },
        ) { HeartbeatIcon(loading) }
    }
}

@Composable
private fun HeartbeatIcon(loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
    } else {
        Icon(AppIcons.Favorite, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

/** App-chosen width below which the heartbeat button keeps only the heart. */
private val HeartbeatLabelMinWidth = 480.dp

private const val MaxArtistActions = 3
