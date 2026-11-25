package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.download.DownloadArtworkStatus
import com.leejlredstar.redefinencm.kmp.download.DownloadLyricStatus
import com.leejlredstar.redefinencm.kmp.download.DownloadTaskStatus
import com.leejlredstar.redefinencm.kmp.download.LocalLibrarySyncState
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.download.SongDownloadTask
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.playbackPlaceholderUri
import com.leejlredstar.redefinencm.kmp.ui.adaptive.browseHeader
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveFeatureIcon
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveWavyProgress
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.util.MediaDeletionConsentHost
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.downloadsNeedExport
import com.leejlredstar.redefinencm.kmp.util.exportDownloadedSong
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
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
    var filter by rememberSaveable { mutableStateOf(DownloadFilter.All) }
    var pendingDestructiveAction by remember { mutableStateOf<DownloadDestructiveAction?>(null) }
    val playWholeList = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(downloadManager) {
        downloadManager.syncWithLocalLibrary()
    }
    // Files an earlier install saved are deleted by the system once the user confirms; the row
    // then goes the ordinary way, finding the file gone.
    MediaDeletionConsentHost { songId -> downloadManager.deleteDownloadedSong(songId) }

    val queue = remember(tasks) { DownloadQueuePresentation(tasks) }
    val selectedFilter = queue.resolveFilter(filter)
    val groups = queue.groups(selectedFilter)
    LaunchedEffect(selectedFilter) {
        if (filter != selectedFilter) filter = selectedFilter
    }
    val completedTasks = queue.rows(DownloadFilter.Completed)
    // The completed group preserves the manager's order, including the queue made by tapping a song.
    val completedQueue = remember(completedTasks) { completedTasks.map { it.toMediaInfo() } }
    val syncing = localLibrarySyncState is LocalLibrarySyncState.Syncing
    val syncError = localLibrarySyncState as? LocalLibrarySyncState.Error

    ExpressivePage(
        maxContentWidth = ExpressiveLayout.ReadingContentMaxWidth,
        headerOwnsTopInsets = true,
    ) {
        val gutter = LocalExpressivePageGutter.current
        Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            ExpressivePageBar(
                title = strings.downloadManagement,
                onBack = onBack,
                windowInsets = WindowInsets.browseHeader,
                scrollBehavior = scrollBehavior,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = gutter,
                    end = gutter,
                    top = 8.dp,
                    bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item(key = "download-overview") {
                    DownloadOverview(
                        queue = queue,
                        syncing = syncing,
                        onPauseAll = downloadManager::pauseAll,
                        onResumeAll = downloadManager::resumeAll,
                        onSyncLocalLibrary = downloadManager::syncWithLocalLibrary,
                        onCancelAll = { pendingDestructiveAction = DownloadDestructiveAction.CancelAll },
                        onClearFinished = { pendingDestructiveAction = DownloadDestructiveAction.ClearFinished },
                    )
                }
                // A filter is useful once there are two distinct non-empty status groups.
                if (queue.filters.size > 2) {
                    item(key = "download-filters") {
                        DownloadFilters(
                            filters = queue.filters,
                            selected = selectedFilter,
                            onSelected = {
                                if (it != selectedFilter) {
                                    filter = it
                                    scope.launch { listState.scrollToItem(0) }
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                syncError?.let { error ->
                    item(key = "local-library-sync-error") {
                        ExpressiveStatePanel(
                            title = strings.localLibrarySyncFailed,
                            message = error.message,
                            icon = AppIcons.Refresh,
                            tone = ExpressiveStateTone.Error,
                            actionLabel = strings.retry,
                            onAction = downloadManager::syncWithLocalLibrary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                persistenceError?.let { message ->
                    item(key = "download-persistence-error") {
                        ExpressiveStatePanel(
                            title = strings.downloadQueueSaveFailedTitle,
                            message = message,
                            icon = AppIcons.Download,
                            tone = ExpressiveStateTone.Error,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                if (groups.isEmpty()) {
                    if (syncing) {
                        item(key = "local-library-syncing") {
                            ExpressiveLoadingState(
                                label = strings.scanningLocalLibrary,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    } else if (syncError == null) {
                        item(key = "download-empty") {
                            ExpressiveStatePanel(
                                title = strings.noDownloadedSongsYet,
                                message = strings.downloadsEmptyHint,
                                icon = AppIcons.Download,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
                groups.forEach { group ->
                    val rows = queue.rows(group)
                    item(key = "download-section-${group.name}") {
                        Column {
                            DownloadGroupHeader(group)
                            if (downloadsNeedExport && group == DownloadFilter.Completed) {
                                Text(
                                    text = strings.downloadsStoredInBrowserHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                    itemsIndexed(items = rows, key = { _, task -> task.id }) { index, task ->
                        DownloadTaskRow(
                            task = task,
                            shape = connectedListItemShape(index, rows.size),
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else ExpressiveLayout.ConnectedItemGap),
                            onPlay = if (group == DownloadFilter.Completed) {
                                {
                                    playFromList(
                                        player,
                                        completedQueue,
                                        index,
                                        playWholeList,
                                        source = UiText { it.downloads },
                                    )
                                }
                            } else null,
                            onExport = task.fileName
                                ?.takeIf { downloadsNeedExport && group == DownloadFilter.Completed }
                                ?.let { fileName ->
                                    {
                                        scope.launch {
                                            runCatching {
                                                exportDownloadedSong(fileName, "${task.artist} - ${task.title}")
                                            }.onFailure { error ->
                                                snackbarHostState.showSnackbar(
                                                    strings.saveSongToDeviceFailed(task.title, error.message ?: strings.unknownError),
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
private fun SongDownloadTask.toMediaInfo(): MediaInfo {
    val mediaId = id.toString()
    return MediaInfo(
        id = mediaId,
        title = title,
        artist = artist,
        artworkUri = artworkUri,
        placeholderUri = playbackPlaceholderUri(mediaId),
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadOverview(
    queue: DownloadQueuePresentation,
    syncing: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onSyncLocalLibrary: () -> Unit,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val active = queue.count(DownloadFilter.Active)
    val paused = queue.count(DownloadFilter.Paused)
    val completed = queue.count(DownloadFilter.Completed)
    val failed = queue.count(DownloadFilter.Failed)
    val summary = buildList {
        if (active > 0) add(strings.downloadsActiveCount(active))
        if (paused > 0) add(strings.downloadsPausedCount(paused))
        add(strings.downloadsCompletedCount(completed))
        if (failed > 0) add(strings.downloadsFailedCount(failed))
    }.joinToString(" · ")
    var menuOpen by remember { mutableStateOf(false) }
    val menu = buildList {
        add(DownloadMenuAction(strings.rescanDownloadedFiles, AppIcons.Refresh, enabled = !syncing, onClick = onSyncLocalLibrary))
        if (queue.canCancelAll) {
            add(DownloadMenuAction(strings.cancelAllDownloads, AppIcons.Clear, destructive = true, onClick = onCancelAll))
        }
        if (queue.hasFinished) {
            add(DownloadMenuAction(strings.clearFinishedEntries, AppIcons.Delete, onClick = onClearFinished))
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ExpressiveLayout.FeatureCorner),
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressivePageHeading(
                    title = strings.downloadQueueOverview,
                    supportingText = summary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                DownloadOverflow(menuOpen, { menuOpen = it }, menu)
            }
            if (syncing && queue.count(DownloadFilter.All) > 0) {
                Row(
                    modifier = Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                    },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(Modifier.size(24.dp))
                    Text(strings.scanningLocalLibrary, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (queue.canPauseAll || queue.canResumeAll) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (queue.canPauseAll) {
                        DownloadBulkButton(strings.pauseAll, AppIcons.Pause, onPauseAll)
                    }
                    if (queue.canResumeAll) {
                        DownloadBulkButton(strings.resumeAll, AppIcons.PlayArrow, onResumeAll)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadBulkButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        contentPadding = ButtonDefaults.SmallContentPadding,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.SmallIconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadOverflow(expanded: Boolean, onExpanded: (Boolean) -> Unit, actions: List<DownloadMenuAction>) {
    Box {
        IconButton(
            onClick = { onExpanded(true) },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.minimumInteractiveComponentSize()
                .size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow)),
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Icon(AppIcons.MoreVert, contentDescription = strings.moreActions)
        }
        DownloadMenu(expanded = expanded, actions = actions, onDismiss = { onExpanded(false) })
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
                enabled = action.enabled,
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadFilters(
    filters: List<DownloadFilter>,
    selected: DownloadFilter,
    onSelected: (DownloadFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // A labelled menu leaves room for the queue in compact and short split-screen windows.
        // Let chips wrap only where there is enough width to scan several at once.
        if (maxWidth >= 480.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { filter ->
                    val checked = selected == filter
                    FilterChip(
                        selected = checked,
                        onClick = { onSelected(filter) },
                        leadingIcon = if (checked) {
                            { Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null,
                        label = { Text(filter.label) },
                    )
                }
            }
        } else {
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.SmallContentPadding,
                ) {
                    Text(strings.downloadStatusFilter(selected.label), modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(AppIcons.ArrowDropDown, contentDescription = null, modifier = Modifier.size(ButtonDefaults.SmallIconSize))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    filters.forEach { filter ->
                        val checked = selected == filter
                        DropdownMenuItem(
                            text = { Text(filter.label) },
                            modifier = Modifier.semantics { this.selected = checked },
                            leadingIcon = {
                                if (checked) Icon(AppIcons.Check, contentDescription = null)
                                else Spacer(Modifier.size(24.dp))
                            },
                            onClick = {
                                expanded = false
                                onSelected(filter)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadGroupHeader(group: DownloadFilter) {
    Text(
        text = group.label,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = if (group == DownloadFilter.Active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadTaskRow(
    task: SongDownloadTask,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
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
    // One visible action for the current state; everything else is named in the menu.
    val primary = when (task.status) {
        DownloadTaskStatus.Queued,
        DownloadTaskStatus.Resolving,
        DownloadTaskStatus.Downloading -> DownloadMenuAction(strings.pause, AppIcons.Pause, onClick = onPause)
        DownloadTaskStatus.Paused -> DownloadMenuAction(strings.resumeDownload, AppIcons.PlayArrow, onClick = onResume)
        DownloadTaskStatus.Failed,
        DownloadTaskStatus.Cancelled -> DownloadMenuAction(strings.retry, AppIcons.Refresh, onClick = onRetry)
        DownloadTaskStatus.Deleted -> DownloadMenuAction(strings.downloadAgain, AppIcons.Download, onClick = onRetry)
        DownloadTaskStatus.SavingLyrics,
        DownloadTaskStatus.Completed -> null
    }
    val menu = buildList {
        when (task.status) {
            DownloadTaskStatus.Queued,
            DownloadTaskStatus.Resolving,
            DownloadTaskStatus.Downloading,
            DownloadTaskStatus.Paused -> add(
                DownloadMenuAction(strings.cancelDownload, AppIcons.Clear, destructive = true, onClick = onCancel),
            )
            DownloadTaskStatus.Completed -> {
                val savingLocalAsset = task.lyricStatus == DownloadLyricStatus.Saving ||
                    task.artworkStatus == DownloadArtworkStatus.Saving
                onExport?.let { add(DownloadMenuAction(strings.saveToDevice, AppIcons.Download, onClick = it)) }
                if (!savingLocalAsset) {
                    add(
                        DownloadMenuAction(
                            if (task.lyricStatus == DownloadLyricStatus.Saved) strings.resaveLyricsFromCurrentSource else strings.saveLyricsFile,
                            AppIcons.FormatQuote,
                            onClick = onSaveLyrics,
                        ),
                    )
                    add(
                        DownloadMenuAction(
                            if (task.artworkStatus == DownloadArtworkStatus.Saved) strings.resaveCover else strings.saveCoverImage,
                            AppIcons.Image,
                            onClick = onSaveArtwork,
                        ),
                    )
                }
                add(DownloadMenuAction(strings.deleteDownloadedFile, AppIcons.Delete, destructive = true, onClick = onDeleteSong))
                add(DownloadMenuAction(strings.removeFromListKeepFile, AppIcons.Clear, onClick = onRemove))
            }
            DownloadTaskStatus.Deleted -> {
                if (task.lyricFileName != null || task.artworkFileName != null) {
                    add(DownloadMenuAction(strings.deleteLeftoverLyricsAndCover, AppIcons.Delete, destructive = true, onClick = onDeleteSong))
                }
                add(DownloadMenuAction(strings.removeFromList, AppIcons.Clear, onClick = onRemove))
            }
            DownloadTaskStatus.Failed,
            DownloadTaskStatus.Cancelled -> add(DownloadMenuAction(strings.removeFromList, AppIcons.Clear, onClick = onRemove))
            DownloadTaskStatus.SavingLyrics -> Unit
        }
    }

    val scheme = MaterialTheme.colorScheme
    val downloading = task.status == DownloadTaskStatus.Downloading
    val showProgress = downloading || task.status == DownloadTaskStatus.Paused
    Surface(
        shape = shape,
        color = if (downloading) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Keep room for song information before allocating two independent 48 dp actions.
            val separateActions = maxWidth < 260.dp && primary != null && menu.isNotEmpty()
            val actions: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    primary?.let { action ->
                        FilledTonalIconButton(
                            onClick = action.onClick,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.minimumInteractiveComponentSize()
                                .size(IconButtonDefaults.smallContainerSize(IconButtonDefaults.IconButtonWidthOption.Narrow)),
                        ) {
                            Icon(action.icon, contentDescription = action.label)
                        }
                    }
                    if (menu.isNotEmpty()) {
                        DownloadOverflow(menuOpen, { menuOpen = it }, menu)
                    }
                }
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val onPlayLabel = strings.play
                    val playModifier = if (onPlay == null) Modifier else Modifier.clickable(
                        onClickLabel = onPlayLabel,
                        role = Role.Button,
                        onClick = onPlay,
                    )
                    Row(
                        modifier = Modifier.weight(1f).then(playModifier)
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExpressiveArtwork(
                            model = task.artworkUri,
                            contentDescription = null,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(48.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = task.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!showProgress) {
                                val (text, isError) = task.statusSummary()
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isError) scheme.error else scheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (!separateActions) Box(Modifier.padding(end = 4.dp)) { actions() }
                }
                if (showProgress) DownloadTaskProgress(task)
                if (separateActions) {
                    Box(Modifier.align(Alignment.End).padding(end = 4.dp)) { actions() }
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskProgress(task: SongDownloadTask) {
    val scheme = MaterialTheme.colorScheme
    val paused = task.status == DownloadTaskStatus.Paused
    val progressLabel = if (paused) strings.paused else strings.filterDownloading
    val track = Modifier.fillMaxWidth().semantics { contentDescription = progressLabel }
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (paused) {
            // A paused transfer still shows its retained bytes but has no active motion.
            if (task.totalBytes != null) {
                LinearProgressIndicator(
                    progress = { task.progressFraction },
                    modifier = track,
                    color = scheme.secondary,
                    trackColor = scheme.surfaceContainerHighest,
                )
            }
        } else if (task.totalBytes == null) {
            ExpressiveWavyProgress(modifier = track, trackColor = scheme.secondaryContainer)
        } else {
            ExpressiveWavyProgress(progress = { task.progressFraction }, modifier = track, trackColor = scheme.secondaryContainer)
        }
        Text(
            text = (if (paused) strings.pausedPrefix else "") + task.progressText(),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** A one-line account of a task that is not transferring, and whether it is a failure. */
private fun SongDownloadTask.statusSummary(): Pair<String, Boolean> = when (status) {
    DownloadTaskStatus.Queued -> strings.waitingToDownload to false
    DownloadTaskStatus.Resolving -> strings.gettingDownloadLink to false
    DownloadTaskStatus.SavingLyrics -> strings.savingLyricsAndCover to false
    DownloadTaskStatus.Failed -> (errorMessage?.let { strings.downloadFailedWithReason(it) } ?: strings.downloadFailed) to true
    DownloadTaskStatus.Cancelled -> strings.canceled to false
    DownloadTaskStatus.Deleted -> (errorMessage ?: strings.fileDeleted) to false
    DownloadTaskStatus.Completed -> buildList {
        qualityDisplayName(actualQuality)?.let(::add)
        add(
            when (lyricStatus) {
                DownloadLyricStatus.Saved -> lyricFormat?.name?.let { strings.lyricsSavedWithFormat(it) } ?: strings.lyrics
                DownloadLyricStatus.NoLyric -> strings.lyricStatusNone
                DownloadLyricStatus.Failed -> strings.lyricsSaveFailed
                DownloadLyricStatus.Saving -> strings.savingLyrics
                DownloadLyricStatus.NotStarted -> strings.lyricsNotSaved
            },
        )
        add(
            when (artworkStatus) {
                DownloadArtworkStatus.Saved -> strings.coverSavedStatus
                DownloadArtworkStatus.NoArtwork -> strings.noCover
                DownloadArtworkStatus.Failed -> strings.coverSaveFailed
                DownloadArtworkStatus.Saving -> strings.savingCover
                DownloadArtworkStatus.NotStarted -> strings.coverNotSaved
            },
        )
    }.joinToString(" · ") to false
    DownloadTaskStatus.Downloading,
    DownloadTaskStatus.Paused -> progressText() to false
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
            title = strings.cancelAllDownloadsTitle
            message = strings.cancelAllDownloadsMessage
            confirmLabel = strings.cancelAll
        }
        DownloadDestructiveAction.ClearFinished -> {
            title = strings.clearFinishedEntriesTitle
            message = strings.clearFinishedEntriesMessage
            confirmLabel = strings.clearEntries
        }
        is DownloadDestructiveAction.CancelTask -> {
            title = strings.cancelDownloadTitle(action.title)
            message = strings.cancelDownloadMessage
            confirmLabel = strings.cancelDownload
        }
        is DownloadDestructiveAction.RemoveTask -> {
            title = strings.removeFromListTitle(action.title)
            message = strings.removeFromListMessage
            confirmLabel = strings.remove
        }
        is DownloadDestructiveAction.DeleteSong -> {
            title = strings.deleteSongFileTitle(action.title)
            message = strings.deleteSongFileMessage
            confirmLabel = strings.deleteFile
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
                Text(strings.goBack)
            }
        },
    )
}

private fun qualityDisplayName(level: String?): String? {
    val normalized = level?.trim()?.takeIf { it.isNotEmpty() }?.lowercase() ?: return null
    val aliases = mapOf(
        "standard" to strings.qualityStandard,
        "normal" to strings.qualityStandard,
        "lq" to strings.qualityStandard,
        "higher" to strings.qualityHigh,
        "high" to strings.qualityHigh,
        "exhigh" to strings.qualityVeryHigh,
        "ex-high" to strings.qualityVeryHigh,
        "hq" to strings.qualityVeryHigh,
        "lossless" to strings.lossless,
        "sq" to strings.lossless,
        "hires" to "Hi-Res",
        "hi-res" to "Hi-Res",
        "hr" to "Hi-Res",
        "jyeffect" to strings.qualityHdSurround,
        "jy-effect" to strings.qualityHdSurround,
        "sky" to strings.qualityImmersiveSurround,
        "dolby" to strings.qualityDolbyAtmos,
        "jymaster" to strings.qualityMaster,
        "jy-master" to strings.qualityMaster,
        "master" to strings.qualityMaster,
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
