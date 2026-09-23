package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.local.LocalPlaylist
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.component.foreignProviderName
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import org.koin.compose.koinInject

/**
 * One local playlist or the local favourites: its tracks, from any provider, played as one queue.
 * A track of a provider this build does not know is listed but cannot be played.
 */
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
    val playlist = library?.playlist(playlistId)
    val tracks = playlist?.tracks.orEmpty()
    val queue = remember(tracks) { tracks.mapNotNull { it.toMediaInfo() } }
    val playWholeList = remember {
        settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT)
    }
    val palette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)
    val playlistForSource = playlist
    val source = UiText { it.playbackSourceLocalPlaylist(playlistForSource?.displayName.orEmpty()) }

    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    // A deleted playlist has nowhere to show; leave its page.
    LaunchedEffect(library, playlist) {
        if (library != null && playlist == null) onBack()
    }

    ExpressivePage(accentPalette = palette, contentWindowInsets = WindowInsets.statusBars) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding() + 16.dp),
            ) {
                item(key = "local-playlist-header") {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        BackButton(palette, onBack)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = playlist?.displayName.orEmpty(),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.onPageStart,
                            )
                            Text(
                                text = strings.localPlaylistSongCount(tracks.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.secondaryOnPageStart,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        PlaybackSource.set(source)
                                        player.setQueue(queue, 0)
                                    },
                                    enabled = queue.isNotEmpty(),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.accent,
                                        contentColor = palette.onAccent,
                                    ),
                                ) {
                                    Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.playAll)
                                }
                                // The favourites list is the hearts' own; it is not renamed or deleted.
                                if (playlist?.kind == LocalPlaylist.Kind.PLAYLIST) {
                                    FilledTonalButton(
                                        onClick = { renaming = true },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = palette.container,
                                            contentColor = palette.onContainer,
                                        ),
                                    ) { Text(strings.rename) }
                                    FilledTonalButton(
                                        onClick = { confirmingDelete = true },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = palette.container,
                                            contentColor = palette.onContainer,
                                        ),
                                    ) { Text(strings.delete) }
                                }
                            }
                        }
                    }
                }
                if (tracks.isEmpty()) {
                    item(key = "local-playlist-empty") {
                        ExpressiveStatePanel(
                            title = strings.playlistEmpty,
                            message = strings.localPlaylistEmptyHint,
                            icon = AppIcons.PlaylistAdd,
                            accentPalette = palette,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    itemsIndexed(items = tracks, key = { _, track -> track.key }) { index, track ->
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
                            accentColor = MaterialTheme.colorScheme.primaryContainer,
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
            icon = { Icon(AppIcons.Delete, contentDescription = null) },
            title = { Text(strings.deletePlaylistTitle(playlist.displayName)) },
            text = { Text(strings.deletePlaylistMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.deletePlaylist(playlistId)
                    },
                ) { Text(strings.delete) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(strings.cancel) } },
        )
    }
}
