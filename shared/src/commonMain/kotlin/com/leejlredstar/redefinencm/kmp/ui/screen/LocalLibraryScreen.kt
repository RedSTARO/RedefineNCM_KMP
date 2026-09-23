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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.local.LocalPlaylist
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
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
    val importing by viewModel.importing.collectAsState()
    val palette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)

    var creating by remember { mutableStateOf(false) }
    var importingDialog by remember { mutableStateOf(false) }

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
                                text = "本地歌单",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.onPageStart,
                            )
                            Text(
                                text = "歌曲可以来自任意平台；只保存在此设备，随设置备份导出",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.secondaryOnPageStart,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { creating = true },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.accent,
                                        contentColor = palette.onAccent,
                                    ),
                                ) {
                                    Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("新建歌单")
                                }
                                FilledTonalButton(
                                    onClick = { importingDialog = true },
                                    enabled = !importing,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = palette.container,
                                        contentColor = palette.onContainer,
                                    ),
                                ) {
                                    Icon(AppIcons.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (importing) "正在导入…" else "导入平台歌单")
                                }
                            }
                        }
                    }
                }
                when {
                    library == null -> item(key = "local-loading") {
                        ExpressiveLoadingState(
                            label = "正在读取本地歌单…",
                            accentColor = palette.accent,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    rows.isEmpty() -> item(key = "local-empty") {
                        ExpressiveStatePanel(
                            title = "还没有本地歌单",
                            message = "新建一个，或在歌曲的「更多操作」里选「添加到本地歌单」。点亮 QQ音乐等平台歌曲的心形，也会收进「本地喜欢」。",
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
            title = "新建本地歌单",
            label = "歌单名称",
            confirmLabel = "新建",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                viewModel.createPlaylist(name)
            },
        )
    }
    if (importingDialog) {
        TextEntryDialog(
            title = "导入平台歌单",
            label = "歌单链接或 ID",
            supportingText = "支持网易云音乐与 QQ音乐的歌单链接；纯数字按网易云歌单读取，QQ 歌单 ID 前加 qq:",
            confirmLabel = "导入",
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
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${playlist.tracks.size} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryOnQuietContainer,
                )
            }
            Icon(AppIcons.KeyboardArrowRight, contentDescription = null, tint = palette.secondaryOnQuietContainer)
        }
    }
}

/** The round back button the list pages open with. */
@Composable
internal fun BackButton(palette: ContentAccentPalette, onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Surface(
            shape = CircleShape,
            color = palette.quietContainer.copy(alpha = 0.72f),
            contentColor = palette.onQuietContainer,
        ) {
            Icon(AppIcons.ArrowBack, contentDescription = "返回", modifier = Modifier.padding(10.dp))
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
) {
    var draft by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(label) },
                supportingText = supportingText?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, enabled = draft.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
