package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.download.DownloadArtworkStatus
import com.leejlredstar.redefinencm.kmp.download.DownloadLyricStatus
import com.leejlredstar.redefinencm.kmp.download.DownloadTaskStatus
import com.leejlredstar.redefinencm.kmp.download.LocalLibrarySyncState
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.download.SongDownloadTask
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveWavyProgress
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.downloadsNeedExport
import com.leejlredstar.redefinencm.kmp.util.exportDownloadedSong
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The views the list can be narrowed to. Each maps to exactly one task status family, and the
 * label is the one the status itself shows, so a filter and the rows it keeps say the same word.
 */
private enum class DownloadFilter(val label: String) {
    All("全部"),
    Active("下载中"),
    Paused("已暂停"),
    Completed("已下载"),
    Failed("失败"),
    Cancelled("已取消"),
    Deleted("文件已删除"),
}

private fun DownloadFilter.matches(task: SongDownloadTask): Boolean = when (this) {
    DownloadFilter.All -> true
    DownloadFilter.Active -> task.isActive
    DownloadFilter.Paused -> task.status == DownloadTaskStatus.Paused
    DownloadFilter.Completed -> task.status == DownloadTaskStatus.Completed
    DownloadFilter.Failed -> task.status == DownloadTaskStatus.Failed
    DownloadFilter.Cancelled -> task.status == DownloadTaskStatus.Cancelled
    DownloadFilter.Deleted -> task.status == DownloadTaskStatus.Deleted
}

private sealed interface DownloadDestructiveAction {
    data object CancelAll : DownloadDestructiveAction
    data object ClearFinished : DownloadDestructiveAction
    data class CancelTask(val id: Long, val title: String) : DownloadDestructiveAction
    data class RemoveTask(val id: Long, val title: String) : DownloadDestructiveAction
    data class DeleteSong(val id: Long, val title: String) : DownloadDestructiveAction
}

/** One labelled entry of a row's or the page's overflow menu. */
private data class DownloadMenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun DownloadManagementScreen(
    scaffoldPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    downloadManager: SongDownloadManager = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
) {
    val tasks by downloadManager.tasks.collectAsState()
    val localLibrarySyncState by downloadManager.localLibrarySyncState.collectAsState()
    val persistenceError by downloadManager.persistenceError.collectAsState()
    var filter by remember { mutableStateOf(DownloadFilter.All) }
    var pendingDestructiveAction by remember { mutableStateOf<DownloadDestructiveAction?>(null) }
    val palette = contentAccentPalette(MaterialTheme.colorScheme.tertiaryContainer)
    val playWholeList = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(downloadManager) {
        downloadManager.syncWithLocalLibrary()
    }

    val counts = remember(tasks) {
        DownloadFilter.entries.associateWith { f -> tasks.count { f.matches(it) } }
    }
    // An empty category is not offered as a filter: six always-present chips crowded the header
    // and most were zero.
    val offeredFilters = remember(counts) {
        DownloadFilter.entries.filter { it == DownloadFilter.All || (counts[it] ?: 0) > 0 }
    }
    LaunchedEffect(offeredFilters) {
        if (filter !in offeredFilters) filter = DownloadFilter.All
    }
    val visibleTasks = remember(tasks, filter) { tasks.filter { filter.matches(it) } }
    // The downloaded songs, in list order, as a queue: tapping one plays from the downloads.
    val completedTasks = remember(tasks) { tasks.filter { it.status == DownloadTaskStatus.Completed } }
    val completedQueue = remember(completedTasks) { completedTasks.map { it.toMediaInfo() } }

    ExpressivePage(
        accentPalette = palette,
        contentWindowInsets = WindowInsets.statusBars,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "download-header") {
                DownloadHeader(
                    counts = counts,
                    accentPalette = palette,
                    localLibrarySyncState = localLibrarySyncState,
                    storedInBrowser = downloadsNeedExport,
                    onBack = onBack,
                    onPauseAll = downloadManager::pauseAll,
                    onResumeAll = downloadManager::resumeAll,
                    onSyncLocalLibrary = downloadManager::syncWithLocalLibrary,
                    onCancelAll = {
                        pendingDestructiveAction = DownloadDestructiveAction.CancelAll
                    },
                    onClearFinished = {
                        pendingDestructiveAction = DownloadDestructiveAction.ClearFinished
                    },
                )
            }
            if (offeredFilters.size > 2) {
                item(key = "download-filters") {
                    DownloadFilterRow(
                        filters = offeredFilters,
                        counts = counts,
                        selected = filter,
                        onSelected = { filter = it },
                        accentPalette = palette,
                    )
                }
            }
            if (localLibrarySyncState is LocalLibrarySyncState.Error) {
                item(key = "local-library-sync-error") {
                    ExpressiveStatePanel(
                        title = "本地音乐库同步失败",
                        message = (localLibrarySyncState as LocalLibrarySyncState.Error).message,
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = palette,
                        actionLabel = "重试",
                        onAction = downloadManager::syncWithLocalLibrary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            persistenceError?.let { message ->
                item(key = "download-persistence-error") {
                    ExpressiveStatePanel(
                        title = "下载队列保存失败",
                        message = message,
                        icon = AppIcons.Download,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = palette,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (localLibrarySyncState is LocalLibrarySyncState.Syncing && tasks.isEmpty()) {
                item(key = "local-library-syncing") {
                    ExpressiveLoadingState(
                        label = "正在扫描本地音乐库…",
                        accentColor = palette.accent,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else if (
                visibleTasks.isEmpty() &&
                localLibrarySyncState !is LocalLibrarySyncState.Error
            ) {
                item { DownloadEmptyState(filter, palette) }
            } else {
                itemsIndexed(
                    items = visibleTasks,
                    key = { _, task -> task.id },
                ) { index, task ->
                    DownloadTaskRow(
                        task = task,
                        shape = connectedListItemShape(index, visibleTasks.size),
                        accentPalette = palette,
                        onPlay = if (task.status == DownloadTaskStatus.Completed) {
                            {
                                val queueIndex = completedTasks.indexOfFirst { it.id == task.id }
                                playFromList(
                                    player,
                                    completedQueue,
                                    queueIndex,
                                    playWholeList,
                                    source = "下载",
                                )
                            }
                        } else {
                            null
                        },
                        onExport = task.fileName
                            ?.takeIf { downloadsNeedExport && task.status == DownloadTaskStatus.Completed }
                            ?.let { fileName ->
                                {
                                    scope.launch {
                                        runCatching {
                                            exportDownloadedSong(fileName, "${task.artist} - ${task.title}")
                                        }.onFailure { error ->
                                            snackbarHostState.showSnackbar(
                                                "没能保存「${task.title}」：${error.message ?: "未知错误"}",
                                            )
                                        }
                                    }
                                }
                            },
                        onPause = { downloadManager.pause(task.id) },
                        onResume = { downloadManager.resume(task.id) },
                        onCancel = {
                            pendingDestructiveAction = DownloadDestructiveAction.CancelTask(task.id, task.title)
                        },
                        onRetry = { downloadManager.retry(task.id) },
                        onSaveLyrics = { downloadManager.saveLyrics(task.id) },
                        onSaveArtwork = { downloadManager.saveArtwork(task.id) },
                        onRemove = {
                            pendingDestructiveAction = DownloadDestructiveAction.RemoveTask(task.id, task.title)
                        },
                        onDeleteSong = {
                            pendingDestructiveAction = DownloadDestructiveAction.DeleteSong(task.id, task.title)
                        },
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = scaffoldPadding.calculateBottomPadding()),
        )
    }

    pendingDestructiveAction?.let { action ->
        DownloadDestructiveConfirmationDialog(
            action = action,
            onDismiss = { pendingDestructiveAction = null },
            onConfirm = {
                pendingDestructiveAction = null
                when (action) {
                    DownloadDestructiveAction.CancelAll -> downloadManager.cancelAll()
                    DownloadDestructiveAction.ClearFinished -> downloadManager.clearFinished()
                    is DownloadDestructiveAction.CancelTask -> downloadManager.cancel(action.id)
                    is DownloadDestructiveAction.RemoveTask -> downloadManager.remove(action.id)
                    is DownloadDestructiveAction.DeleteSong -> downloadManager.deleteDownloadedSong(action.id)
                }
            },
        )
    }
}

/** A downloaded song as the player takes it; the player prefers the local file for this id. */
private fun SongDownloadTask.toMediaInfo(): MediaInfo = MediaInfo(
    id = id.toString(),
    title = title,
    artist = artist,
    artworkUri = artworkUri,
    placeholderUri = "redefinencm://playbackPlaceHolder?id=$id",
)

@Composable
private fun DownloadHeader(
    counts: Map<DownloadFilter, Int>,
    accentPalette: ContentAccentPalette,
    localLibrarySyncState: LocalLibrarySyncState,
    storedInBrowser: Boolean,
    onBack: (() -> Unit)?,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onSyncLocalLibrary: () -> Unit,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit,
) {
    val active = counts[DownloadFilter.Active] ?: 0
    val paused = counts[DownloadFilter.Paused] ?: 0
    val completed = counts[DownloadFilter.Completed] ?: 0
    val failed = counts[DownloadFilter.Failed] ?: 0
    val finished = completed + failed +
        (counts[DownloadFilter.Cancelled] ?: 0) + (counts[DownloadFilter.Deleted] ?: 0)
    var menuOpen by remember { mutableStateOf(false) }
    val syncing = localLibrarySyncState is LocalLibrarySyncState.Syncing
    val menu = buildList {
        add(
            DownloadMenuAction("重新扫描已下载的文件", AppIcons.Refresh) {
                if (!syncing) onSyncLocalLibrary()
            },
        )
        if (active > 0 || paused > 0) {
            add(DownloadMenuAction("取消全部下载", AppIcons.Clear, destructive = true, onClick = onCancelAll))
        }
        if (finished > 0) {
            add(DownloadMenuAction("从列表清除已结束的记录", AppIcons.Delete, onClick = onClearFinished))
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onBack?.let {
                FilledTonalIconButton(
                    onClick = it,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.quietContainer,
                        contentColor = accentPalette.onQuietContainer,
                    ),
                ) {
                    Icon(AppIcons.ArrowBack, contentDescription = "返回")
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "下载管理",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentPalette.onPageStart,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildList {
                        add("已下载 $completed 首")
                        if (active > 0) add("正在下载 $active 首")
                        if (paused > 0) add("已暂停 $paused 首")
                        if (failed > 0) add("$failed 首失败")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnPageStart,
                )
                if (storedInBrowser && completed > 0) {
                    Text(
                        text = "歌曲存放在浏览器里，不在系统的「下载」文件夹；" +
                            "要得到音频文件，在歌曲的「更多操作」里选「保存到本机」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = accentPalette.secondaryOnPageStart,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (syncing) {
                LoadingIndicator(color = accentPalette.accent, modifier = Modifier.size(32.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(AppIcons.MoreVert, contentDescription = "更多操作")
                }
                DownloadMenu(expanded = menuOpen, actions = menu, onDismiss = { menuOpen = false })
            }
        }
        // Only the bulk actions that apply now, and in words: the old row of bare icons put
        // "cancel everything" and "clear the list" one mis-tap from each other.
        if (active > 0 || paused > 0) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (active > 0) {
                    FilledTonalButton(
                        onClick = onPauseAll,
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentPalette.container,
                            contentColor = accentPalette.onContainer,
                        ),
                    ) {
                        Icon(AppIcons.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("全部暂停")
                    }
                }
                if (paused > 0) {
                    FilledTonalButton(
                        onClick = onResumeAll,
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentPalette.container,
                            contentColor = accentPalette.onContainer,
                        ),
                    ) {
                        Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("全部继续")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadMenu(
    expanded: Boolean,
    actions: List<DownloadMenuAction>,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.forEach { action ->
            val tint = if (action.destructive) MaterialTheme.colorScheme.error else Color.Unspecified
            DropdownMenuItem(
                text = { Text(action.label, color = tint) },
                leadingIcon = {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        tint = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadFilterRow(
    filters: List<DownloadFilter>,
    counts: Map<DownloadFilter, Int>,
    selected: DownloadFilter,
    onSelected: (DownloadFilter) -> Unit,
    accentPalette: ContentAccentPalette,
) {
    // The counts live on the filters themselves; they used to be a second row of pills that
    // repeated the filters under slightly different names.
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters) { filter ->
            val isSelected = selected == filter
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelected(filter) },
                modifier = Modifier.heightIn(min = 40.dp),
                shapes = ToggleButtonDefaults.shapes(),
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = accentPalette.quietContainer,
                    contentColor = accentPalette.onQuietContainer,
                    checkedContainerColor = accentPalette.container,
                    checkedContentColor = accentPalette.onContainer,
                ),
            ) {
                Text("${filter.label} ${counts[filter] ?: 0}")
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(
    task: SongDownloadTask,
    shape: androidx.compose.foundation.shape.RoundedCornerShape,
    accentPalette: ContentAccentPalette,
    onPlay: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onSaveLyrics: () -> Unit,
    onSaveArtwork: () -> Unit,
    onRemove: () -> Unit,
    onDeleteSong: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // One visible action for the state the task is in; everything else is named in the menu.
    val primary: Pair<ImageVector, Pair<String, () -> Unit>>? = when (task.status) {
        DownloadTaskStatus.Queued,
        DownloadTaskStatus.Resolving,
        DownloadTaskStatus.Downloading -> AppIcons.Pause to ("暂停" to onPause)
        DownloadTaskStatus.Paused -> AppIcons.PlayArrow to ("继续下载" to onResume)
        DownloadTaskStatus.Failed,
        DownloadTaskStatus.Cancelled -> AppIcons.Refresh to ("重试" to onRetry)
        DownloadTaskStatus.Deleted -> AppIcons.Download to ("重新下载" to onRetry)
        DownloadTaskStatus.SavingLyrics,
        DownloadTaskStatus.Completed -> null
    }
    val menu = buildList {
        when (task.status) {
            DownloadTaskStatus.Queued,
            DownloadTaskStatus.Resolving,
            DownloadTaskStatus.Downloading,
            DownloadTaskStatus.Paused -> add(
                DownloadMenuAction("取消下载", AppIcons.Clear, destructive = true, onClick = onCancel),
            )
            DownloadTaskStatus.Completed -> {
                val savingLocalAsset = task.lyricStatus == DownloadLyricStatus.Saving ||
                    task.artworkStatus == DownloadArtworkStatus.Saving
                onExport?.let { add(DownloadMenuAction("保存到本机", AppIcons.Download, onClick = it)) }
                if (!savingLocalAsset) {
                    add(
                        DownloadMenuAction(
                            if (task.lyricStatus == DownloadLyricStatus.Saved) "按当前歌词来源重新保存歌词" else "保存歌词文件",
                            AppIcons.FormatQuote,
                            onClick = onSaveLyrics,
                        ),
                    )
                    add(
                        DownloadMenuAction(
                            if (task.artworkStatus == DownloadArtworkStatus.Saved) "重新保存封面" else "保存封面图片",
                            AppIcons.Image,
                            onClick = onSaveArtwork,
                        ),
                    )
                }
                add(DownloadMenuAction("删除已下载的文件", AppIcons.Delete, destructive = true, onClick = onDeleteSong))
                add(DownloadMenuAction("从列表中移除（保留文件）", AppIcons.Clear, onClick = onRemove))
            }
            DownloadTaskStatus.Deleted -> {
                if (task.lyricFileName != null || task.artworkFileName != null) {
                    add(DownloadMenuAction("清理残留的歌词与封面", AppIcons.Delete, destructive = true, onClick = onDeleteSong))
                }
                add(DownloadMenuAction("从列表中移除", AppIcons.Clear, onClick = onRemove))
            }
            DownloadTaskStatus.Failed,
            DownloadTaskStatus.Cancelled -> add(DownloadMenuAction("从列表中移除", AppIcons.Clear, onClick = onRemove))
            DownloadTaskStatus.SavingLyrics -> Unit
        }
    }

    Surface(
        onClick = { onPlay?.invoke() },
        enabled = onPlay != null,
        shape = shape,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.5.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = task.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DownloadTaskStatusLine(task, accentPalette)
            }
            primary?.let { (icon, action) ->
                IconButton(onClick = action.second) {
                    Icon(icon, contentDescription = action.first, tint = accentPalette.onQuietContainer)
                }
            }
            if (menu.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            AppIcons.MoreVert,
                            contentDescription = "更多操作",
                            tint = accentPalette.secondaryOnQuietContainer,
                        )
                    }
                    DownloadMenu(expanded = menuOpen, actions = menu, onDismiss = { menuOpen = false })
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

/**
 * What a task is doing, in words: a progress bar only while bytes are moving, and for finished
 * songs the quality and whether the lyrics and cover were kept.
 */
@Composable
private fun DownloadTaskStatusLine(
    task: SongDownloadTask,
    accentPalette: ContentAccentPalette,
) {
    val secondary = accentPalette.secondaryOnQuietContainer
    when (task.status) {
        DownloadTaskStatus.Downloading,
        DownloadTaskStatus.Paused -> {
            Spacer(Modifier.height(6.dp))
            if (task.status == DownloadTaskStatus.Downloading && task.totalBytes == null) {
                ExpressiveWavyProgress(
                    modifier = Modifier.fillMaxWidth(),
                    color = accentPalette.accent,
                    trackColor = accentPalette.onQuietContainer.copy(alpha = 0.12f),
                )
            } else {
                ExpressiveWavyProgress(
                    progress = { task.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (task.status == DownloadTaskStatus.Paused) secondary else accentPalette.accent,
                    trackColor = accentPalette.onQuietContainer.copy(alpha = 0.12f),
                )
            }
            Text(
                text = buildString {
                    append(if (task.status == DownloadTaskStatus.Paused) "已暂停 · " else "")
                    append(task.progressText())
                },
                style = MaterialTheme.typography.labelSmall,
                color = secondary,
            )
        }
        else -> {
            val (text, isError) = task.statusSummary()
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A one-line account of a task that is not transferring, and whether it is a failure. */
private fun SongDownloadTask.statusSummary(): Pair<String, Boolean> = when (status) {
    DownloadTaskStatus.Queued -> "等待下载" to false
    DownloadTaskStatus.Resolving -> "正在获取下载地址" to false
    DownloadTaskStatus.SavingLyrics -> "正在保存歌词和封面" to false
    DownloadTaskStatus.Failed -> ("下载失败" + (errorMessage?.let { "：$it" } ?: "")) to true
    DownloadTaskStatus.Cancelled -> "已取消" to false
    DownloadTaskStatus.Deleted -> (errorMessage ?: "文件已删除") to false
    DownloadTaskStatus.Completed -> buildList {
        qualityDisplayName(actualQuality)?.let(::add)
        add(
            when (lyricStatus) {
                DownloadLyricStatus.Saved -> lyricFormat?.name?.let { "歌词（$it）" } ?: "歌词"
                DownloadLyricStatus.NoLyric -> "无歌词"
                DownloadLyricStatus.Failed -> "歌词保存失败"
                DownloadLyricStatus.Saving -> "正在保存歌词"
                DownloadLyricStatus.NotStarted -> "未保存歌词"
            },
        )
        add(
            when (artworkStatus) {
                DownloadArtworkStatus.Saved -> "封面"
                DownloadArtworkStatus.NoArtwork -> "无封面"
                DownloadArtworkStatus.Failed -> "封面保存失败"
                DownloadArtworkStatus.Saving -> "正在保存封面"
                DownloadArtworkStatus.NotStarted -> "未保存封面"
            },
        )
    }.joinToString(" · ") to false
    DownloadTaskStatus.Downloading,
    DownloadTaskStatus.Paused -> progressText() to false
}

@Composable
private fun DownloadEmptyState(
    filter: DownloadFilter,
    accentPalette: ContentAccentPalette,
) {
    val title = when (filter) {
        DownloadFilter.All -> "还没有下载过歌曲"
        else -> "没有「${filter.label}」的歌曲"
    }
    val message = when (filter) {
        DownloadFilter.All -> "在歌单页点「下载全部」，或在歌曲的更多菜单里选「下载」。"
        else -> "切换到其他筛选查看。"
    }
    ExpressiveStatePanel(
        title = title,
        message = message,
        icon = AppIcons.Download,
        accentPalette = accentPalette,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun DownloadDestructiveConfirmationDialog(
    action: DownloadDestructiveAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title: String
    val message: String
    val confirmLabel: String
    when (action) {
        DownloadDestructiveAction.CancelAll -> {
            title = "取消全部下载？"
            message = "正在下载和已暂停的歌曲都会停止，之后可以在「已取消」里重试。已下载的歌曲不受影响。"
            confirmLabel = "取消全部"
        }
        DownloadDestructiveAction.ClearFinished -> {
            title = "清除已结束的记录？"
            message = "已下载、失败、已取消的记录会从这个列表里移除。已下载的歌曲文件不会被删除。"
            confirmLabel = "清除记录"
        }
        is DownloadDestructiveAction.CancelTask -> {
            title = "取消下载「${action.title}」？"
            message = "下载会停止，之后可以在「已取消」里重试。"
            confirmLabel = "取消下载"
        }
        is DownloadDestructiveAction.RemoveTask -> {
            title = "从列表中移除「${action.title}」？"
            message = "只移除这条记录，已下载的歌曲文件会保留。"
            confirmLabel = "移除"
        }
        is DownloadDestructiveAction.DeleteSong -> {
            title = "删除「${action.title}」的文件？"
            message = "本地音频文件会被永久删除，之后仍可重新下载。"
            confirmLabel = "删除文件"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AppIcons.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("返回")
            }
        },
    )
}

private fun qualityDisplayName(level: String?): String? {
    val normalized = level?.trim()?.takeIf { it.isNotEmpty() }?.lowercase() ?: return null
    val aliases = mapOf(
        "standard" to "标准",
        "normal" to "标准",
        "lq" to "标准",
        "higher" to "较高",
        "high" to "较高",
        "exhigh" to "极高",
        "ex-high" to "极高",
        "hq" to "极高",
        "lossless" to "无损",
        "sq" to "无损",
        "hires" to "Hi-Res",
        "hi-res" to "Hi-Res",
        "hr" to "Hi-Res",
        "jyeffect" to "高清环绕声",
        "jy-effect" to "高清环绕声",
        "sky" to "沉浸环绕声",
        "dolby" to "杜比全景声",
        "jymaster" to "超清母带",
        "jy-master" to "超清母带",
        "master" to "超清母带",
    )
    aliases[normalized]?.let { return it }
    return SoundQuality.entries
        .firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        ?.displayName
        ?: normalized.uppercase()
}

private fun SongDownloadTask.progressText(): String {
    val current = formatBytes(progressBytes)
    val total = totalBytes?.let(::formatBytes)
    return if (total == null) current else "$current / $total"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        "${(value * 10).toInt() / 10.0} ${units[unitIndex]}"
    }
}
