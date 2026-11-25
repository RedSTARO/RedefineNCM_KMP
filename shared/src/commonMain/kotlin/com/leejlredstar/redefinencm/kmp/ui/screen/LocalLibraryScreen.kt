package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.local.LocalPlaylist
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveFeatureIcon
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.LocalLibraryReadErrorPanel
import com.leejlredstar.redefinencm.kmp.ui.component.inMemoryViewModel
import com.leejlredstar.redefinencm.kmp.ui.component.rememberEditorFocus
import com.leejlredstar.redefinencm.kmp.ui.component.rememberSaveableEditorText
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import org.koin.compose.koinInject

/** The local account's provider-neutral favourites and playlists, stored on this device. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LocalLibraryScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    scaffoldPadding: PaddingValues = PaddingValues(),
    viewModel: LocalLibraryViewModel = koinInject(),
) {
    val library by viewModel.library.collectAsState()
    val readFailed by viewModel.readFailed.collectAsState()
    val importing by viewModel.importing.collectAsState()
    var creating by rememberSaveable { mutableStateOf(false) }
    var importingDialog by rememberSaveable { mutableStateOf(false) }
    val rows = library?.let { listOfNotNull(it.favorites) + it.userPlaylists }.orEmpty()

    CatalogPage(title = strings.localPlaylists, onBack = onBack, scaffoldPadding = scaffoldPadding) {
        if (!readFailed && library != null) {
            item(key = "local-header") {
                CatalogFeatureHeader(
                    title = strings.localPlaylists,
                    supportingText = strings.localPlaylistsSubtitle,
                    icon = AppIcons.FolderOpen,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current, vertical = 12.dp),
                    actions = {
                        // Reflow whole actions, not the text within an overcrowded fixed row.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            CatalogPrimaryButton(strings.newPlaylist, AppIcons.Add, onClick = { creating = true })
                            OutlinedButton(
                                onClick = { importingDialog = true },
                                enabled = !importing,
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = ButtonDefaults.contentPaddingFor(40.dp, hasStartIcon = true),
                            ) {
                                Icon(AppIcons.Link, contentDescription = null, modifier = Modifier.size(ButtonDefaults.SmallIconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(if (importing) strings.importing else strings.importPlaylist)
                            }
                        }
                    },
                )
            }
        }
        when {
            readFailed -> item(key = "local-read-error") {
                LocalLibraryReadErrorPanel(viewModel, Modifier.padding(horizontal = LocalExpressivePageGutter.current))
            }
            library == null -> item(key = "local-loading") {
                ExpressiveLoadingState(
                    label = strings.localPlaylistsLoading,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            rows.isEmpty() -> item(key = "local-empty") {
                ExpressiveStatePanel(
                    title = strings.noLocalPlaylistsYet,
                    message = strings.localPlaylistsEmptyHint,
                    icon = AppIcons.PlaylistAdd,
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            else -> itemsIndexed(items = rows, key = { _, playlist -> playlist.id }) { index, playlist ->
                LocalPlaylistRow(playlist, index, rows.size) { onOpenPlaylist(playlist.id) }
            }
        }
    }

    if (creating) {
        TextEntryDialog(
            title = strings.newLocalPlaylistTitle,
            label = strings.playlistName,
            confirmLabel = strings.create,
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                viewModel.createPlaylist(name)
            },
            icon = AppIcons.PlaylistAdd,
        )
    }
    if (importingDialog) {
        TextEntryDialog(
            title = strings.importPlaylist,
            label = strings.playlistLinkOrId,
            supportingText = strings.importPlaylistHint,
            confirmLabel = strings.importAction,
            saveText = false,
            onDismiss = { importingDialog = false },
            onConfirm = { reference ->
                importingDialog = false
                viewModel.importPlaylist(reference)
            },
            icon = AppIcons.Link,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalPlaylistRow(playlist: LocalPlaylist, index: Int, count: Int, onClick: () -> Unit) {
    val favorites = playlist.kind == LocalPlaylist.Kind.FAVORITES
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().catalogRowPadding(),
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = scheme.surfaceContainerLow),
        leadingContent = {
            if (favorites) {
                ExpressiveFeatureIcon(AppIcons.Favorite, containerColor = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer)
            } else {
                Icon(AppIcons.QueueMusic, contentDescription = null, modifier = Modifier.size(48.dp).padding(12.dp))
            }
        },
        supportingContent = { Text(strings.songCount(playlist.tracks.size)) },
        trailingContent = { Icon(AppIcons.KeyboardArrowRight, contentDescription = null) },
    ) {
        Text(
            text = playlist.displayName,
            style = if (favorites) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A dialog asking for one line of text: a name, a link. */
@Composable
internal fun TextEntryDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialValue: String = "",
    supportingText: String? = null,
    saveText: Boolean = true,
    allowBlank: Boolean = false,
    icon: ImageVector? = null,
) {
    val draft = if (saveText) {
        rememberSaveableEditorText(initialValue)
    } else {
        // Pasted playlist links may contain URL credentials or session parameters.
        inMemoryViewModel { PrivateEntryDraft(initialValue) }.field
    }
    fun confirm() {
        val text = draft.text.toString()
        if (allowBlank || text.isNotBlank()) onConfirm(text)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, contentDescription = null) } },
        title = { Text(title) },
        text = {
            DialogEntryField(draft, label, supportingText, onDone = ::confirm)
        },
        confirmButton = {
            TextButton(onClick = ::confirm, enabled = allowBlank || draft.text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

private class PrivateEntryDraft(initial: String) : androidx.lifecycle.ViewModel() {
    val field = TextFieldState(initial)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DialogEntryField(
    state: TextFieldState,
    label: String,
    supportingText: String? = null,
    onDone: () -> Unit,
) {
    // A Dialog is a separate Android window; the root shell's IME inset is not its inset.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val editor = rememberEditorFocus(imeVisibility = imeVisible)
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, showKeyboardOnFocus = editor.showKeyboardOnFocus),
        onKeyboardAction = { onDone() },
        modifier = editor.modifier.fillMaxWidth(),
    )
}
