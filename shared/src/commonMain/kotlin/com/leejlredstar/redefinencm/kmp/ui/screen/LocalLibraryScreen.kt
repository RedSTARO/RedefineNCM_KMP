package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.local.LocalPlaylist
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.LocalLibraryReadErrorPanel
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.component.inMemoryViewModel
import com.leejlredstar.redefinencm.kmp.ui.component.rememberEditorFocus
import com.leejlredstar.redefinencm.kmp.ui.component.rememberSaveableEditorText
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import org.koin.compose.koinInject

/**
 * The local account's library: its favourites and its playlists, whose tracks may come from any
 * provider. Nothing here needs a sign-in; it all stays on this device and travels with the
 * settings backup.
 */
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
    val palette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)

    var creating by rememberSaveable { mutableStateOf(false) }
    var importingDialog by rememberSaveable { mutableStateOf(false) }

    val rows: List<LocalPlaylist> = library?.let { listOfNotNull(it.favorites) + it.userPlaylists }.orEmpty()

    ExpressivePage(accentPalette = palette, contentWindowInsets = WindowInsets.statusBars) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding() + 16.dp),
            ) {
                item(key = "local-header") {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        BackButton(palette, onBack)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = strings.localPlaylists,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.onPageStart,
                            )
                            Text(
                                text = strings.localPlaylistsSubtitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.secondaryOnPageStart,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { creating = true },
                                    enabled = library != null && !readFailed,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.accent,
                                        contentColor = palette.onAccent,
                                    ),
                                ) {
                                    Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.newPlaylist)
                                }
                                FilledTonalButton(
                                    onClick = { importingDialog = true },
                                    enabled = !importing && library != null && !readFailed,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = palette.container,
                                        contentColor = palette.onContainer,
                                    ),
                                ) {
                                    Icon(AppIcons.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (importing) strings.importing else strings.importPlaylist)
                                }
                            }
                        }
                    }
                }
                when {
                    readFailed -> item(key = "local-read-error") {
                        LocalLibraryReadErrorPanel(viewModel, Modifier.padding(horizontal = 16.dp), palette)
                    }
                    library == null -> item(key = "local-loading") {
                        ExpressiveLoadingState(
                            label = strings.localPlaylistsLoading,
                            accentColor = palette.accent,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    rows.isEmpty() -> item(key = "local-empty") {
                        ExpressiveStatePanel(
                            title = strings.noLocalPlaylistsYet,
                            message = strings.localPlaylistsEmptyHint,
                            icon = AppIcons.PlaylistAdd,
                            accentPalette = palette,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    else -> itemsIndexed(items = rows, key = { _, playlist -> playlist.id }) { index, playlist ->
                        LocalPlaylistRow(
                            playlist = playlist,
                            index = index,
                            count = rows.size,
                            palette = palette,
                            onClick = { onOpenPlaylist(playlist.id) },
                        )
                    }
                }
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
        )
    }
}

@Composable
private fun LocalPlaylistRow(
    playlist: LocalPlaylist,
    index: Int,
    count: Int,
    palette: ContentAccentPalette,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = connectedListItemShape(index, count),
        color = palette.quietContainer,
        contentColor = palette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = ExpressiveLayout.ConnectedItemGap),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = palette.container,
                contentColor = palette.onContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (playlist.kind == LocalPlaylist.Kind.FAVORITES) AppIcons.Favorite else AppIcons.QueueMusic,
                        contentDescription = null,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = strings.songCount(playlist.tracks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryOnQuietContainer,
                )
            }
            Icon(AppIcons.KeyboardArrowRight, contentDescription = null, tint = palette.secondaryOnQuietContainer)
        }
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
