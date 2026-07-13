package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.download.DownloadQueueSummary
import com.leejlredstar.redefinencm.kmp.download.DownloadLyricStatus
import com.leejlredstar.redefinencm.kmp.download.DownloadTaskStatus
import com.leejlredstar.redefinencm.kmp.download.LocalLibrarySyncState
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.download.SongDownloadTask
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import org.koin.compose.koinInject

private enum class DownloadFilter(val label: String) {
    All("全部"),
    Active("进行中"),
    Paused("已暂停"),
    Completed("已完成"),
    Deleted("已删除"),
    Failed("失败"),
}

private data class DownloadInfoBadge(
    val text: String,
    val tone: DownloadInfoBadgeTone = DownloadInfoBadgeTone.Neutral,
)

private enum class DownloadInfoBadgeTone {
    Neutral,
    Accent,
    Success,
    Error,
}

private sealed interface DownloadDestructiveAction {
    data object CancelAll : DownloadDestructiveAction
    data object ClearFinished : DownloadDestructiveAction
    data class CancelTask(val id: Long, val title: String) : DownloadDestructiveAction
    data class RemoveTask(val id: Long, val title: String) : DownloadDestructiveAction
    data class DeleteSong(val id: Long, val title: String) : DownloadDestructiveAction
}

@Composable
fun DownloadManagementScreen(
    scaffoldPadding: PaddingValues,
    downloadManager: SongDownloadManager = koinInject(),
) {
    val tasks by downloadManager.tasks.collectAsState()
    val summary by downloadManager.summary.collectAsState()
    val localLibrarySyncState by downloadManager.localLibrarySyncState.collectAsState()
    val persistenceError by downloadManager.persistenceError.collectAsState()
    var filter by remember { mutableStateOf(DownloadFilter.All) }
    var pendingDestructiveAction by remember { mutableStateOf<DownloadDestructiveAction?>(null) }
    val representativeTask = remember(tasks) {
        tasks.firstOrNull { it.isActive } ?: tasks.firstOrNull()
    }
    val defaultAccent = MaterialTheme.colorScheme.tertiaryContainer
    var rawAccent by remember(representativeTask?.artworkUri, defaultAccent) {
        mutableStateOf(defaultAccent)
    }
    val pageAccent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = spring(),
        label = "downloadPageAccent",
    )
    val palette = contentAccentPalette(pageAccent)

    LaunchedEffect(downloadManager) {
        downloadManager.syncWithLocalLibrary()
    }

    val visibleTasks = remember(tasks, filter) {
        tasks.filter { task ->
            when (filter) {
                DownloadFilter.All -> true
                DownloadFilter.Active -> task.status == DownloadTaskStatus.Queued ||
                    task.status == DownloadTaskStatus.Resolving ||
                    task.status == DownloadTaskStatus.Downloading ||
                    task.status == DownloadTaskStatus.SavingLyrics
                DownloadFilter.Paused -> task.status == DownloadTaskStatus.Paused
                DownloadFilter.Completed -> task.status == DownloadTaskStatus.Completed
                DownloadFilter.Deleted -> task.status == DownloadTaskStatus.Deleted
                DownloadFilter.Failed -> task.status == DownloadTaskStatus.Failed ||
                    task.status == DownloadTaskStatus.Cancelled
            }
        }
    }

    ExpressivePage(
        accentPalette = palette,
        contentWindowInsets = WindowInsets.statusBars,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding() + 96.dp),
        ) {
            item {
                DownloadHero(
                    summary = summary,
                    representativeTask = representativeTask,
                    accentPalette = palette,
                    onAccentColor = { rawAccent = it },
                    onPauseAll = downloadManager::pauseAll,
                    onResumeAll = downloadManager::resumeAll,
                    onCancelAll = {
                        pendingDestructiveAction = DownloadDestructiveAction.CancelAll
                    },
                    onClearFinished = {
                        pendingDestructiveAction = DownloadDestructiveAction.ClearFinished
                    },
                    localLibrarySyncState = localLibrarySyncState,
                    onSyncLocalLibrary = downloadManager::syncWithLocalLibrary,
                )
            }
            item {
                DownloadFilterRow(
                    selected = filter,
                    onSelected = { filter = it },
                    accentPalette = palette,
                )
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
                        onPause = { downloadManager.pause(task.id) },
                        onResume = { downloadManager.resume(task.id) },
                        onCancel = {
                            pendingDestructiveAction = DownloadDestructiveAction.CancelTask(task.id, task.title)
                        },
                        onRetry = { downloadManager.retry(task.id) },
                        onRemove = {
                            pendingDestructiveAction = DownloadDestructiveAction.RemoveTask(task.id, task.title)
                        },
                        onDeleteSong = {
                            pendingDestructiveAction = DownloadDestructiveAction.DeleteSong(task.id, task.title)
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
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

@Composable
private fun DownloadHero(
    summary: DownloadQueueSummary,
    representativeTask: SongDownloadTask?,
    accentPalette: ContentAccentPalette,
    onAccentColor: (Color) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit,
    localLibrarySyncState: LocalLibrarySyncState,
    onSyncLocalLibrary: () -> Unit,
) {
    val extractAccent = rememberThemeColorExtractor(
        requestKey = representativeTask?.artworkUri,
        onAccentColor = onAccentColor,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accentPalette.pageStart,
                        accentPalette.pageMiddle,
                        Color.Transparent,
                    ),
                ),
            )
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(112.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = accentPalette.container,
                contentColor = accentPalette.onContainer,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        AppIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                    if (!representativeTask?.artworkUri.isNullOrBlank()) {
                        AsyncImage(
                            model = representativeTask.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onSuccess = { state -> extractAccent(state.result.image) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "下载管理",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentPalette.onPageMiddle,
                )
                Text(
                    text = "队列、进度和失败项都在这里处理",
                    style = MaterialTheme.typography.bodyLarge,
                    color = accentPalette.secondaryOnPageMiddle,
                )
                representativeTask?.let { task ->
                    Text(
                        text = if (task.isActive) "正在处理 · ${task.title}" else "最近任务 · ${task.title}",
                        style = MaterialTheme.typography.labelLarge,
                        color = accentPalette.onPageMiddle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { DownloadStatPill("全部", summary.total, accentPalette) }
            item { DownloadStatPill("进行中", summary.active, accentPalette) }
            item { DownloadStatPill("完成", summary.completed, accentPalette) }
            item { DownloadStatPill("已删除", summary.deleted, accentPalette) }
            item { DownloadStatPill("失败", summary.failed, accentPalette) }
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Button(
                    onClick = onPauseAll,
                    enabled = summary.active > 0,
                    modifier = Modifier.height(48.dp),
                    shape = CircleShape,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentPalette.accent,
                        contentColor = accentPalette.onAccent,
                    ),
                ) {
                    Icon(AppIcons.Pause, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("暂停")
                }
            }
            item {
                Button(
                    onClick = onResumeAll,
                    enabled = summary.paused > 0,
                    modifier = Modifier.height(48.dp),
                    shape = CircleShape,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentPalette.container,
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("继续")
                }
            }
            item {
                FilledTonalIconButton(
                    onClick = onSyncLocalLibrary,
                    enabled = localLibrarySyncState !is LocalLibrarySyncState.Syncing,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.quietContainer,
                        contentColor = accentPalette.onQuietContainer,
                    ),
                ) {
                    if (localLibrarySyncState is LocalLibrarySyncState.Syncing) {
                        LoadingIndicator(
                            color = accentPalette.onQuietContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Icon(AppIcons.Refresh, contentDescription = "同步本地库")
                    }
                }
            }
            item {
                FilledTonalIconButton(
                    onClick = onCancelAll,
                    enabled = summary.active > 0 || summary.paused > 0,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.quietContainer,
                        contentColor = accentPalette.onQuietContainer,
                    ),
                ) {
                    Icon(AppIcons.Clear, contentDescription = "取消全部")
                }
            }
            item {
                FilledTonalIconButton(
                    onClick = onClearFinished,
                    enabled = summary.completed > 0 || summary.failed > 0,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.quietContainer,
                        contentColor = accentPalette.onQuietContainer,
                    ),
                ) {
                    Icon(AppIcons.Delete, contentDescription = "清理已结束")
                }
            }
        }
    }
}

@Composable
private fun DownloadStatPill(
    label: String,
    value: Int,
    accentPalette: ContentAccentPalette,
) {
    Surface(
        shape = CircleShape,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
    ) {
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun DownloadFilterRow(
    selected: DownloadFilter,
    onSelected: (DownloadFilter) -> Unit,
    accentPalette: ContentAccentPalette,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(DownloadFilter.entries) { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelected(filter) },
                modifier = Modifier.height(48.dp),
                label = { Text(filter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = accentPalette.quietContainer,
                    labelColor = accentPalette.onQuietContainer,
                    selectedContainerColor = accentPalette.container,
                    selectedLabelColor = accentPalette.onContainer,
                    selectedLeadingIconColor = accentPalette.onContainer,
                ),
                leadingIcon = if (selected == filter) {
                    { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun DownloadTaskRow(
    task: SongDownloadTask,
    shape: androidx.compose.foundation.shape.RoundedCornerShape,
    accentPalette: ContentAccentPalette,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onDeleteSong: () -> Unit,
) {
    Surface(
        shape = shape,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = task.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.large),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    DownloadStatusPill(task.status)
                }
                Text(
                    text = task.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                if (task.status == DownloadTaskStatus.Downloading && task.totalBytes == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = accentPalette.accent,
                        trackColor = accentPalette.onQuietContainer.copy(alpha = 0.12f),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { task.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = progressColor(task.status, accentPalette),
                        trackColor = accentPalette.onQuietContainer.copy(alpha = 0.12f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                DownloadInfoBadges(task, accentPalette)
            }
            Spacer(Modifier.width(12.dp))
            DownloadActions(
                status = task.status,
                accentPalette = accentPalette,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onRetry = onRetry,
                onRemove = onRemove,
                onDeleteSong = onDeleteSong,
            )
        }
    }
}

@Composable
private fun DownloadInfoBadges(
    task: SongDownloadTask,
    accentPalette: ContentAccentPalette,
) {
    val badges = task.infoBadges()
    if (badges.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(badges) { badge ->
            DownloadInfoBadgeSurface(badge, accentPalette)
        }
    }
}

@Composable
private fun DownloadInfoBadgeSurface(
    badge: DownloadInfoBadge,
    accentPalette: ContentAccentPalette,
) {
    val containerColor = when (badge.tone) {
        DownloadInfoBadgeTone.Accent -> accentPalette.container
        DownloadInfoBadgeTone.Success -> MaterialTheme.colorScheme.primaryContainer
        DownloadInfoBadgeTone.Error -> MaterialTheme.colorScheme.errorContainer
        DownloadInfoBadgeTone.Neutral -> accentPalette.onQuietContainer.copy(alpha = 0.08f)
    }
    val contentColor = when (badge.tone) {
        DownloadInfoBadgeTone.Accent -> accentPalette.onContainer
        DownloadInfoBadgeTone.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        DownloadInfoBadgeTone.Error -> MaterialTheme.colorScheme.onErrorContainer
                DownloadInfoBadgeTone.Neutral -> accentPalette.secondaryOnQuietContainer
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.10f)),
    ) {
        Text(
            text = badge.text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun DownloadStatusPill(status: DownloadTaskStatus) {
    val color = when (status) {
        DownloadTaskStatus.Completed -> MaterialTheme.colorScheme.primaryContainer
        DownloadTaskStatus.Deleted -> MaterialTheme.colorScheme.secondaryContainer
        DownloadTaskStatus.Failed,
        DownloadTaskStatus.Cancelled -> MaterialTheme.colorScheme.errorContainer
        DownloadTaskStatus.Paused -> MaterialTheme.colorScheme.secondaryContainer
        DownloadTaskStatus.SavingLyrics -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = when (status) {
        DownloadTaskStatus.Completed -> MaterialTheme.colorScheme.onPrimaryContainer
        DownloadTaskStatus.Deleted -> MaterialTheme.colorScheme.onSecondaryContainer
        DownloadTaskStatus.Failed,
        DownloadTaskStatus.Cancelled -> MaterialTheme.colorScheme.onErrorContainer
        DownloadTaskStatus.Paused -> MaterialTheme.colorScheme.onSecondaryContainer
        DownloadTaskStatus.SavingLyrics -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(shape = CircleShape, color = color, contentColor = contentColor) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DownloadActions(
    status: DownloadTaskStatus,
    accentPalette: ContentAccentPalette,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onDeleteSong: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status) {
            DownloadTaskStatus.Queued,
            DownloadTaskStatus.Resolving,
            DownloadTaskStatus.Downloading -> {
                SmallDownloadAction(AppIcons.Pause, "暂停", accentPalette, onPause)
                SmallDownloadAction(AppIcons.Clear, "取消", accentPalette, onCancel)
            }
            DownloadTaskStatus.SavingLyrics -> Unit
            DownloadTaskStatus.Paused -> {
                SmallDownloadAction(AppIcons.PlayArrow, "继续", accentPalette, onResume)
                SmallDownloadAction(AppIcons.Clear, "取消", accentPalette, onCancel)
            }
            DownloadTaskStatus.Failed,
            DownloadTaskStatus.Cancelled,
            DownloadTaskStatus.Deleted -> {
                SmallDownloadAction(AppIcons.Refresh, "重试", accentPalette, onRetry)
                SmallDownloadAction(AppIcons.Clear, "移除任务", accentPalette, onRemove)
            }
            DownloadTaskStatus.Completed -> {
                SmallDownloadAction(AppIcons.Delete, "删除歌曲", accentPalette, onDeleteSong, isDestructive = true)
                SmallDownloadAction(AppIcons.Clear, "移除任务", accentPalette, onRemove)
            }
        }
    }
}

@Composable
private fun SmallDownloadAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    accentPalette: ContentAccentPalette,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = MaterialTheme.shapes.large,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (isDestructive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                accentPalette.container
            },
            contentColor = if (isDestructive) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                accentPalette.onContainer
            },
        ),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DownloadEmptyState(
    filter: DownloadFilter,
    accentPalette: ContentAccentPalette,
) {
    val title = when (filter) {
        DownloadFilter.All -> "暂无下载任务"
        DownloadFilter.Active -> "没有正在进行的任务"
        DownloadFilter.Paused -> "没有暂停的任务"
        DownloadFilter.Completed -> "没有已完成任务"
        DownloadFilter.Deleted -> "没有已删除任务"
        DownloadFilter.Failed -> "没有失败或取消的任务"
    }
    val message = when (filter) {
        DownloadFilter.All -> "从歌单或歌曲页面发起下载后，进度会显示在这里。"
        else -> "当前筛选条件下没有任务，可以切换上方筛选项查看其他下载。"
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
            message = "进行中和已暂停的任务将标记为已取消，已完成的下载不受影响。"
            confirmLabel = "取消全部"
        }
        DownloadDestructiveAction.ClearFinished -> {
            title = "清理已结束任务？"
            message = "已完成、失败、取消和已删除的任务记录会从列表移除，本地歌曲不会被删除。"
            confirmLabel = "清理"
        }
        is DownloadDestructiveAction.CancelTask -> {
            title = "取消「${action.title}」？"
            message = "这个下载会停止，并保留为可重试的已取消任务。"
            confirmLabel = "取消下载"
        }
        is DownloadDestructiveAction.RemoveTask -> {
            title = "移除「${action.title}」？"
            message = "只移除任务记录，不会删除已经保存的本地歌曲。"
            confirmLabel = "移除"
        }
        is DownloadDestructiveAction.DeleteSong -> {
            title = "删除「${action.title}」？"
            message = "本地音频文件将被永久删除；之后仍可从任务列表重新下载。"
            confirmLabel = "删除歌曲"
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

@Composable
private fun progressColor(
    status: DownloadTaskStatus,
    accentPalette: ContentAccentPalette,
): Color = when (status) {
    DownloadTaskStatus.Completed -> MaterialTheme.colorScheme.primary
    DownloadTaskStatus.Deleted -> MaterialTheme.colorScheme.secondary
    DownloadTaskStatus.Failed,
    DownloadTaskStatus.Cancelled -> MaterialTheme.colorScheme.error
    DownloadTaskStatus.Paused -> MaterialTheme.colorScheme.secondary
    DownloadTaskStatus.SavingLyrics -> accentPalette.accent
    else -> accentPalette.accent
}

private fun DownloadTaskStatus.label(): String = when (this) {
    DownloadTaskStatus.Queued -> "排队"
    DownloadTaskStatus.Resolving -> "解析"
    DownloadTaskStatus.Downloading -> "下载"
    DownloadTaskStatus.SavingLyrics -> "歌词"
    DownloadTaskStatus.Paused -> "暂停"
    DownloadTaskStatus.Completed -> "完成"
    DownloadTaskStatus.Deleted -> "已删除"
    DownloadTaskStatus.Failed -> "失败"
    DownloadTaskStatus.Cancelled -> "取消"
}

private fun SongDownloadTask.infoBadges(): List<DownloadInfoBadge> = when (status) {
    DownloadTaskStatus.Failed -> listOf(
        DownloadInfoBadge(errorMessage ?: "下载失败", DownloadInfoBadgeTone.Error),
    )
    DownloadTaskStatus.Cancelled -> listOf(DownloadInfoBadge("已取消", DownloadInfoBadgeTone.Error))
    DownloadTaskStatus.Deleted -> listOf(
        DownloadInfoBadge(errorMessage ?: "本地文件已删除", DownloadInfoBadgeTone.Neutral),
    )
    DownloadTaskStatus.Completed -> buildList {
        qualityBadge(actualQuality)?.let(::add)
        add(lyricStatusBadge())
        add(DownloadInfoBadge("已保存", DownloadInfoBadgeTone.Success))
    }
    DownloadTaskStatus.Queued -> listOf(DownloadInfoBadge("等待", DownloadInfoBadgeTone.Neutral))
    DownloadTaskStatus.Resolving -> buildList {
        add(DownloadInfoBadge("解析直链", DownloadInfoBadgeTone.Accent))
        qualityBadge(requestedQuality)?.let(::add)
    }
    DownloadTaskStatus.Paused -> listOf(DownloadInfoBadge("可继续", DownloadInfoBadgeTone.Neutral))
    DownloadTaskStatus.SavingLyrics -> buildList {
        qualityBadge(actualQuality)?.let(::add)
        add(DownloadInfoBadge("保存歌词", DownloadInfoBadgeTone.Accent))
    }
    DownloadTaskStatus.Downloading -> buildList {
        qualityBadge(actualQuality)?.let(::add)
        add(DownloadInfoBadge(progressText(), DownloadInfoBadgeTone.Neutral))
    }
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

private fun SongDownloadTask.qualityBadge(level: String?): DownloadInfoBadge? =
    qualityDisplayName(level)?.let { DownloadInfoBadge(it, DownloadInfoBadgeTone.Accent) }

private fun SongDownloadTask.lyricStatusBadge(): DownloadInfoBadge = when (lyricStatus) {
    DownloadLyricStatus.NotStarted -> DownloadInfoBadge("歌词待存", DownloadInfoBadgeTone.Neutral)
    DownloadLyricStatus.Saving -> DownloadInfoBadge("保存歌词", DownloadInfoBadgeTone.Accent)
    DownloadLyricStatus.Saved -> DownloadInfoBadge("歌词已存", DownloadInfoBadgeTone.Success)
    DownloadLyricStatus.NoLyric -> DownloadInfoBadge("无歌词", DownloadInfoBadgeTone.Neutral)
    DownloadLyricStatus.Failed -> DownloadInfoBadge("歌词失败", DownloadInfoBadgeTone.Error)
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
