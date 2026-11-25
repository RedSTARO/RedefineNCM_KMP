package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.local.LocalPlaylist
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.LocalLibraryReadErrorPanel
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.component.foreignProviderName
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import org.koin.compose.koinInject

/** One local playlist or the local favourites, with tracks from any provider. */
@Composable
fun LocalPlaylistScreen(
    playlistId: String,
    onBack: () -> Unit,
    scaffoldPadding: PaddingValues = PaddingValues(),
    viewModel: LocalLibraryViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
) {
    val library by viewModel.library.collectAsState()
    val readFailed by viewModel.readFailed.collectAsState()
    val playlist = library?.playlist(playlistId)
    val tracks = playlist?.tracks.orEmpty()
    val queue = remember(tracks) { tracks.mapNotNull { it.toMediaInfo() } }
    val playWholeList = remember {
        settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT)
    }
    val source = UiText { it.playbackSourceLocalPlaylist(playlist?.displayName.orEmpty()) }

    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    // A deleted playlist has nowhere to show; leave its page.
    LaunchedEffect(library, playlist, readFailed) {
        if (!readFailed && library != null && playlist == null) onBack()
    }

    CatalogPage(
        title = playlist?.displayName ?: strings.localPlaylists,
        onBack = onBack,
        scaffoldPadding = scaffoldPadding,
        barActions = {
            // The favourites list is the hearts' own; it is not renamed or deleted.
            if (!readFailed && playlist?.kind == LocalPlaylist.Kind.PLAYLIST) {
                LocalPlaylistActions(onRename = { renaming = true }, onDelete = { confirmingDelete = true })
            }
        },
    ) {
        if (!readFailed && playlist != null) {
            item(key = "local-playlist-header") {
                val favorites = playlist.kind == LocalPlaylist.Kind.FAVORITES
                val scheme = MaterialTheme.colorScheme
                CatalogFeatureHeader(
                    title = playlist.displayName,
                    supportingText = strings.localPlaylistSongCount(tracks.size),
                    icon = if (favorites) AppIcons.Favorite else AppIcons.QueueMusic,
                    iconContainerColor = if (favorites) scheme.tertiaryContainer else scheme.primaryContainer,
                    iconContentColor = if (favorites) scheme.onTertiaryContainer else scheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current, vertical = 12.dp),
                    actions = if (queue.isNotEmpty()) {
                        {
                            CatalogPrimaryButton(strings.playAll, AppIcons.PlayArrow, onClick = {
                                PlaybackSource.set(source)
                                player.setQueue(queue, 0)
                            })
                        }
                    } else null,
                )
            }
        }
        when {
            readFailed -> item(key = "local-playlist-read-error") {
                LocalLibraryReadErrorPanel(viewModel, Modifier.padding(horizontal = LocalExpressivePageGutter.current))
            }
            library == null -> item(key = "local-playlist-loading") {
                ExpressiveLoadingState(
                    label = strings.localPlaylistsLoading,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            tracks.isEmpty() -> item(key = "local-playlist-empty") {
                ExpressiveStatePanel(
                    title = strings.playlistEmpty,
                    message = strings.localPlaylistEmptyHint,
                    icon = AppIcons.PlaylistAdd,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            else -> itemsIndexed(items = tracks, key = { _, track -> track.key }) { index, track ->
                val media = remember(track) { track.toMediaInfo() }
                val baseActions = media?.let { rememberSongRowActions(media = it) }.orEmpty()
                SongRow(
                    index = index,
                    title = track.title,
                    artist = track.artist,
                    artworkUri = track.artworkUri,
                    shape = connectedListItemShape(index, tracks.size),
                    onClick = {
                        val position = media?.let(queue::indexOf) ?: -1
                        if (position >= 0) {
                            playFromList(player, queue, position, playWholeList, source = source)
                            player.play()
                        }
                    },
                    songId = track.itemId?.neteaseIdOrNull,
                    mediaId = media?.id,
                    durationMs = track.durationMillis,
                    album = track.album,
                    badge = media?.id?.let(::foreignProviderName),
                    actions = remember(baseActions, track.key, I18n.language) {
                        baseActions.filterNot { it.label == AddToLocalPlaylistLabel } +
                            SongRowAction(strings.removeFromThisPlaylist, AppIcons.Remove) {
                                viewModel.removeTrack(playlistId, track.key)
                            }
                    },
                )
            }
        }
    }

    if (renaming && playlist != null) {
        TextEntryDialog(
            title = strings.renamePlaylist,
            label = strings.playlistName,
            confirmLabel = strings.save,
            initialValue = playlist.name,
            onDismiss = { renaming = false },
            onConfirm = { name ->
                renaming = false
                viewModel.renamePlaylist(playlistId, name)
            },
        )
    }
    if (confirmingDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            icon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(strings.deletePlaylistTitle(playlist.displayName)) },
            text = { Text(strings.deletePlaylistMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.deletePlaylist(playlistId)
                    },
                ) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(strings.cancel) } },
        )
    }
}

@Composable
private fun LocalPlaylistActions(onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(AppIcons.MoreVert, contentDescription = strings.moreActions)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(strings.rename) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(strings.delete, color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
