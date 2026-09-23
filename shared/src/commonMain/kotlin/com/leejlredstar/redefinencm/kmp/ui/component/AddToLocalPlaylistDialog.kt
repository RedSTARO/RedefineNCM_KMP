package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import org.koin.compose.koinInject

/**
 * Where songs a menu asked to "add to a local playlist" go: one of the local playlists, or a new
 * one. Hosted once by the app, so every song menu opens the same dialog.
 */
@Composable
fun AddToLocalPlaylistDialog(viewModel: LocalLibraryViewModel = koinInject()) {
    val pending by viewModel.pendingAddition.collectAsState()
    val library by viewModel.library.collectAsState()
    val addition = pending ?: return
    var naming by remember(addition) { mutableStateOf(false) }
    var name by remember(addition) { mutableStateOf(addition.suggestedName) }
    val playlists = library?.userPlaylists.orEmpty()

    AlertDialog(
        onDismissRequest = viewModel::dismissAddition,
        icon = { Icon(AppIcons.PlaylistAdd, contentDescription = null) },
        title = {
            Text(if (addition.tracks.size == 1) "添加到本地歌单" else "把 ${addition.tracks.size} 首歌添加到本地歌单")
        },
        text = {
            Column {
                if (naming || playlists.isEmpty()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("新歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                supportingContent = { Text("${playlist.tracks.size} 首") },
                                leadingContent = { Icon(AppIcons.QueueMusic, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth().clickableListItem {
                                    viewModel.addPendingTo(playlist.id)
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("新建歌单") },
                        leadingContent = { Icon(AppIcons.Add, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickableListItem { naming = true },
                    )
                }
            }
        },
        confirmButton = {
            if (naming || playlists.isEmpty()) {
                TextButton(
                    onClick = { viewModel.addPendingToNewPlaylist(name) },
                    enabled = name.isNotBlank(),
                ) { Text("新建并添加") }
            }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissAddition) { Text("取消") } },
    )
}

private fun Modifier.clickableListItem(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
