package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.screen.DialogEntryField
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import org.koin.compose.koinInject

/**
 * Where songs a menu asked to "add to a local playlist" go: one of the local playlists, or a new
 * one. Hosted once by the app, so every song menu opens the same dialog.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddToLocalPlaylistDialog(viewModel: LocalLibraryViewModel = koinInject()) {
    val pending by viewModel.pendingAddition.collectAsState()
    val library by viewModel.library.collectAsState()
    val readFailed by viewModel.readFailed.collectAsState()
    val addition = pending ?: return
    key(addition) {
        val draft = inMemoryViewModel { PendingPlaylistName(addition.suggestedName) }
        val listState = rememberLazyListState()
        val playlists = library?.userPlaylists.orEmpty()
        fun createPlaylist() {
            val name = draft.field.text.toString()
            if (name.isNotBlank()) viewModel.addPendingToNewPlaylist(name)
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissAddition,
            icon = { Icon(AppIcons.PlaylistAdd, contentDescription = null) },
            title = {
                Text(if (addition.tracks.size == 1) strings.addToLocalPlaylist else strings.addSongsToLocalPlaylist(addition.tracks.size))
            },
            text = {
                Column {
                    when {
                        readFailed -> LocalLibraryReadErrorPanel(viewModel)
                        library == null -> ExpressiveLoadingState(label = strings.localPlaylistsLoading)
                        draft.naming || playlists.isEmpty() -> {
                            DialogEntryField(draft.field, strings.newPlaylistName, onDone = ::createPlaylist)
                        }
                        else -> LazyColumn(
                            modifier = Modifier.heightIn(max = 320.dp),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(ExpressiveLayout.ConnectedItemGap),
                        ) {
                            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                                SegmentedListItem(
                                    onClick = { viewModel.addPendingTo(playlist.id) },
                                    shapes = ListItemDefaults.segmentedShapes(index, playlists.size),
                                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    modifier = Modifier.fillMaxWidth(),
                                    supportingContent = { Text(strings.songCount(playlist.tracks.size)) },
                                    leadingContent = { Icon(AppIcons.QueueMusic, contentDescription = null) },
                                ) { Text(playlist.displayName) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!readFailed && library != null && (draft.naming || playlists.isEmpty())) {
                    TextButton(
                        onClick = ::createPlaylist,
                        enabled = draft.field.text.isNotBlank(),
                    ) { Text(strings.createAndAdd) }
                } else if (!readFailed && library != null) {
                    TextButton(onClick = { draft.naming = true }) { Text(strings.newPlaylist) }
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissAddition) { Text(strings.cancel) } },
        )
    }
}

private class PendingPlaylistName(suggestedName: String) : ViewModel() {
    var naming by mutableStateOf(false)
    val field = TextFieldState(suggestedName)
}

@Composable
internal fun LocalLibraryReadErrorPanel(
    viewModel: LocalLibraryViewModel,
    modifier: Modifier = Modifier,
    accentPalette: ContentAccentPalette? = null,
) {
    val reloading by viewModel.reloading.collectAsState()
    if (reloading) {
        ExpressiveLoadingState(label = strings.localPlaylistsLoading, modifier = modifier)
    } else {
        ExpressiveStatePanel(
            title = strings.localPlaylistsReadFailed,
            message = strings.localPlaylistsReadFailedHint,
            tone = ExpressiveStateTone.Error,
            accentPalette = accentPalette,
            modifier = modifier,
            actionLabel = strings.retry,
            onAction = viewModel::retryLoad,
        )
    }
}
