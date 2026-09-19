package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusicComments
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.screen.compactCount
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import kotlinx.coroutines.delay

private data class QueueSheetEntry(
    val key: String,
    val media: MediaInfo,
)

private data class CommentSheetEntry(
    val key: String,
    val comment: CommentMusicComments,
)

/** What the queue panel can do to the queue. Positions are in play order, as the panel lists. */
internal class QueueActions(
    val onSkipTo: (Int) -> Unit,
    val onRemove: (Int) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onClear: () -> Unit,
)

/** The paging and ordering state of the comment panel, beside the comments themselves. */
internal class CommentPaging(
    val showHot: Boolean,
    val onShowHot: (Boolean) -> Unit,
    val moreAvailable: Boolean,
    val moreLoading: Boolean,
    val moreError: String?,
    val onLoadMore: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueBottomSheet(
    playlist: List<MediaInfo>,
    currentIndex: Int,
    shuffleEnabled: Boolean,
    accentPalette: ContentAccentPalette,
    actions: QueueActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = accentPalette.pageEnd,
        contentColor = accentPalette.onQuietContainer,
    ) {
        QueuePanelContent(
            playlist = playlist,
            currentIndex = currentIndex,
            shuffleEnabled = shuffleEnabled,
            accentPalette = accentPalette,
            actions = actions,
            onSkipped = onDismiss,
        )
    }
}

/**
 * The queue: tap a row to play it, remove it, drag its handle to move it, or clear everything.
 *
 * It used to offer only the tap. A drag reorders a copy of the list drawn here and asks the
 * player for the one move on release, so the rows never wait on the player mid-drag; the copy
 * is dropped when the player's queue comes back.
 */
@Composable
internal fun QueuePanelContent(
    playlist: List<MediaInfo>,
    currentIndex: Int,
    shuffleEnabled: Boolean,
    accentPalette: ContentAccentPalette,
    actions: QueueActions,
    onSkipped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val queueEntries = remember(playlist) {
        val occurrences = mutableMapOf<String, Int>()
        playlist.map { media ->
            val occurrence = occurrences[media.id] ?: 0
            occurrences[media.id] = occurrence + 1
            QueueSheetEntry(
                key = "queue:${media.id}:$occurrence",
                media = media,
            )
        }
    }
    val reorder = remember(listState) { QueueReorderState(listState) }
    LaunchedEffect(playlist) { reorder.settle() }
    // A move the player did not take would otherwise leave the copy on screen for good.
    LaunchedEffect(reorder.working, reorder.draggingKey) {
        if (reorder.working != null && reorder.draggingKey == null) {
            delay(1_500)
            reorder.settle()
        }
    }
    val shown = reorder.working ?: queueEntries
    val currentShown by rememberUpdatedState(shown)
    val currentKey = queueEntries.getOrNull(currentIndex)?.key
    val canReorder = !shuffleEnabled && queueEntries.size > 1
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex, playlist.size) {
        if (currentIndex >= 0 && currentIndex < playlist.size && reorder.working == null) {
            listState.scrollToItem(currentIndex)
        }
    }

    Column(modifier) {
        ExpressiveSectionTitle(
            text = "播放队列",
            supportingText = when {
                playlist.isEmpty() -> "暂无待播放歌曲"
                shuffleEnabled -> "共 ${playlist.size} 首 · 随机播放时不能调整顺序"
                playlist.size > 1 -> "共 ${playlist.size} 首 · 拖动右侧手柄调整顺序"
                else -> "共 1 首"
            },
            action = if (playlist.isNotEmpty()) {
                { TextButton(onClick = { confirmClear = true }) { Text("清空") } }
            } else {
                null
            },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (shown.isEmpty()) {
                item(key = "empty-queue") {
                    ExpressiveStatePanel(
                        title = "播放队列为空",
                        message = "开始播放歌曲后，待播放曲目会显示在这里。",
                        icon = AppIcons.QueueMusic,
                        accentPalette = accentPalette,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            itemsIndexed(
                items = shown,
                key = { _, entry -> entry.key },
            ) { index, entry ->
                val dragging = reorder.draggingKey == entry.key
                QueueRow(
                    entry = entry,
                    index = index,
                    count = shown.size,
                    isCurrent = entry.key == currentKey,
                    canReorder = canReorder,
                    accentPalette = accentPalette,
                    onClick = {
                        if (reorder.working == null) {
                            actions.onSkipTo(index)
                            onSkipped()
                        }
                    },
                    onRemove = { if (reorder.working == null) actions.onRemove(index) },
                    onMove = { to -> if (reorder.working == null) actions.onMove(index, to) },
                    dragHandle = Modifier.pointerInput(entry.key) {
                        detectDragGestures(
                            onDragStart = { reorder.start(currentShown, entry.key) },
                            onDrag = { change, amount ->
                                change.consume()
                                reorder.drag(amount.y)
                            },
                            onDragEnd = { reorder.end()?.let { (from, to) -> actions.onMove(from, to) } },
                            onDragCancel = reorder::cancel,
                        )
                    },
                    modifier = if (dragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = reorder.dragOffset }
                    } else {
                        Modifier.animateItem()
                    },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空播放队列？") },
            text = { Text("队列里的 ${playlist.size} 首歌会全部移除，播放随之停止。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        actions.onClear()
                    },
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun QueueRow(
    entry: QueueSheetEntry,
    index: Int,
    count: Int,
    isCurrent: Boolean,
    canReorder: Boolean,
    accentPalette: ContentAccentPalette,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    dragHandle: Modifier,
    modifier: Modifier = Modifier,
) {
    val item = entry.media
    val title = item.title.ifBlank { "未知歌曲" }
    val artist = item.artist.ifBlank { "未知歌手" }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = rememberConnectedListItemShape(
            index = index,
            count = count,
            interactionSource = interactionSource,
        ),
        color = if (isCurrent) accentPalette.container else accentPalette.quietContainer,
        contentColor = if (isCurrent) accentPalette.onContainer else accentPalette.onQuietContainer,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ExpressiveLayout.MinimumTouchTarget)
            .padding(
                horizontal = ExpressiveLayout.PageHorizontalPadding,
                vertical = ExpressiveLayout.ConnectedItemGap,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "第 ${index + 1} 首，$title，歌手 $artist"
                selected = isCurrent
                stateDescription = if (isCurrent) "正在播放" else "等待播放"
                // Dragging is a pointer gesture; screen readers move rows through these instead.
                customActions = buildList {
                    if (canReorder && index > 0) {
                        add(CustomAccessibilityAction("上移") { onMove(index - 1); true })
                    }
                    if (canReorder && index < count - 1) {
                        add(CustomAccessibilityAction("下移") { onMove(index + 1); true })
                    }
                    add(CustomAccessibilityAction("从队列移除") { onRemove(); true })
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) accentPalette.onContainer else accentPalette.accent,
                modifier = Modifier.width(36.dp),
            )
            ExpressiveArtwork(
                model = item.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp),
                pressInteractionSource = interactionSource,
                containerColor = accentPalette.container,
                contentColor = accentPalette.onContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) accentPalette.onContainer else accentPalette.onQuietContainer,
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) {
                        accentPalette.secondaryOnContainer
                    } else {
                        accentPalette.secondaryOnQuietContainer
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(AppIcons.Clear, contentDescription = "从队列移除")
            }
            if (canReorder) {
                Box(
                    modifier = Modifier.size(48.dp).then(dragHandle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.DragIndicator,
                        contentDescription = null,
                        tint = if (isCurrent) {
                            accentPalette.secondaryOnContainer
                        } else {
                            accentPalette.secondaryOnQuietContainer
                        },
                    )
                }
            }
        }
    }
}

/**
 * A drag in progress over the queue: the reordered copy of the list, the row being dragged and
 * how far it sits from where the layout put it.
 */
@Stable
private class QueueReorderState(private val listState: LazyListState) {
    var working by mutableStateOf<List<QueueSheetEntry>?>(null)
        private set
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var startPosition = -1

    fun start(entries: List<QueueSheetEntry>, key: String) {
        startPosition = entries.indexOfFirst { it.key == key }
        if (startPosition < 0) return
        working = entries
        draggingKey = key
        dragOffset = 0f
    }

    /** Moves the dragged row past a neighbour once its middle is over that neighbour. */
    fun drag(delta: Float) {
        val list = working ?: return
        val key = draggingKey ?: return
        dragOffset += delta
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == key } ?: return
        val from = list.indexOfFirst { it.key == key }
        // The previous step has to reach the layout before the next one is judged against it.
        if (dragged.index != from) return
        val middle = dragged.offset + dragOffset + dragged.size / 2f
        val target = visible.firstOrNull { item ->
            item.key != key &&
                list.any { it.key == item.key } &&
                middle > item.offset &&
                middle < item.offset + item.size
        } ?: return
        val to = list.indexOfFirst { it.key == target.key }
        working = list.toMutableList().apply { add(to, removeAt(from)) }
        // The row is laid out in the neighbour's place next; keep it under the pointer.
        dragOffset += dragged.offset - target.offset
    }

    /** The drop: the play-order move to ask for, or null when the row is back where it began. */
    fun end(): Pair<Int, Int>? {
        val key = draggingKey
        draggingKey = null
        dragOffset = 0f
        val to = working?.indexOfFirst { it.key == key } ?: -1
        return if (startPosition >= 0 && to >= 0 && to != startPosition) {
            startPosition to to
        } else {
            working = null
            null
        }
    }

    fun cancel() {
        draggingKey = null
        dragOffset = 0f
        working = null
    }

    /** The player's queue has come back; draw from it again. */
    fun settle() {
        if (draggingKey == null) working = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentBottomSheet(
    comments: List<CommentMusicComments>,
    hasLoadedData: Boolean,
    accentPalette: ContentAccentPalette,
    paging: CommentPaging,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    isFromCache: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    totalCount: Long = 0L,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = accentPalette.pageEnd,
        contentColor = accentPalette.onQuietContainer,
    ) {
        CommentPanelContent(
            comments = comments,
            hasLoadedData = hasLoadedData,
            accentPalette = accentPalette,
            paging = paging,
            isLoading = isLoading,
            isFromCache = isFromCache,
            errorMessage = errorMessage,
            onRetry = onRetry,
            totalCount = totalCount,
        )
    }
}

/**
 * A song's comments, hot or latest, loading the next page on reaching the end.
 *
 * It used to show the first page only — about fifteen of a song's thousands — under a count
 * that was the length of that page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentPanelContent(
    comments: List<CommentMusicComments>,
    hasLoadedData: Boolean,
    accentPalette: ContentAccentPalette,
    paging: CommentPaging,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isFromCache: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    totalCount: Long = 0L,
) {
    val showInitialLoading = isLoading && !hasLoadedData
    val commentEntries = remember(comments) {
        val occurrences = mutableMapOf<String, Int>()
        comments.map { comment ->
            val identity = if (comment.commentId != 0L) {
                "${comment.commentId}"
            } else {
                "${comment.user.userId}:${comment.time}:${comment.content.hashCode()}"
            }
            val occurrence = occurrences[identity] ?: 0
            occurrences[identity] = occurrence + 1
            CommentSheetEntry(
                key = "comment:$identity:$occurrence",
                comment = comment,
            )
        }
    }

    Column(modifier) {
        ExpressiveSectionTitle(
            text = "歌曲评论",
            supportingText = when {
                showInitialLoading -> "正在加载评论"
                errorMessage != null -> "评论暂时无法加载"
                totalCount > 0 -> "共 ${compactCount(totalCount)} 条"
                comments.isEmpty() -> "暂无评论"
                else -> "${comments.size} 条"
            },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (hasLoadedData && errorMessage == null) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                listOf(true to "热门", false to "最新").forEachIndexed { i, (hot, label) ->
                    SegmentedButton(
                        selected = paging.showHot == hot,
                        onClick = { paging.onShowHot(hot) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = accentPalette.container,
                            activeContentColor = accentPalette.onContainer,
                            activeBorderColor = accentPalette.container,
                            inactiveContainerColor = accentPalette.pageEnd,
                            inactiveContentColor = accentPalette.onQuietContainer,
                            inactiveBorderColor = accentPalette.secondaryOnQuietContainer,
                        ),
                    ) {
                        Text(label)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (isFromCache) {
                item(key = "comments-cache-hint") {
                    ExpressiveCacheHint(
                        isRefreshing = isLoading,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (showInitialLoading) {
                item(key = "loading-comments") {
                    ExpressiveLoadingState(
                        label = "正在加载评论…",
                        accentColor = accentPalette.accent,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else if (errorMessage != null) {
                item(key = "error-comments") {
                    ExpressiveStatePanel(
                        title = "评论加载失败",
                        message = errorMessage,
                        icon = AppIcons.Comment,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = accentPalette,
                        actionLabel = onRetry?.let { "重试" },
                        onAction = onRetry,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else if (commentEntries.isEmpty()) {
                item(key = "empty-comments") {
                    ExpressiveStatePanel(
                        title = if (paging.showHot) "还没有热门评论" else "还没有评论",
                        message = if (paging.showHot) "可以看看最新的评论。" else "这首歌暂时没有可显示的评论。",
                        icon = AppIcons.Comment,
                        accentPalette = accentPalette,
                        actionLabel = if (paging.showHot) "看最新评论" else null,
                        onAction = if (paging.showHot) ({ paging.onShowHot(false) }) else null,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (!showInitialLoading && errorMessage == null) {
                itemsIndexed(
                    items = commentEntries,
                    key = { _, entry -> entry.key },
                ) { index, entry ->
                    CommentRow(entry.comment, index, commentEntries.size, accentPalette)
                }
                if (commentEntries.isNotEmpty()) {
                    item(key = "comments-footer") {
                        CommentsFooter(
                            paging = paging,
                            shownCount = commentEntries.size,
                            accentPalette = accentPalette,
                        )
                    }
                }
            }
        }
    }
}

/** The end of the list: the next page loading, a retry, or the word that there is no more. */
@Composable
private fun CommentsFooter(
    paging: CommentPaging,
    shownCount: Int,
    accentPalette: ContentAccentPalette,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            paging.moreLoading -> Text(
                text = "正在加载更多评论…",
                style = MaterialTheme.typography.bodyMedium,
                color = accentPalette.secondaryOnQuietContainer,
            )
            paging.moreError != null -> TextButton(onClick = paging.onLoadMore) {
                Text("${paging.moreError}，点按重试")
            }
            paging.moreAvailable -> {
                // Reaching the end loads the next page; the button is there if that stalls.
                LaunchedEffect(shownCount) { paging.onLoadMore() }
                TextButton(onClick = paging.onLoadMore) { Text("加载更多") }
            }
            else -> Text(
                text = "没有更多评论了",
                style = MaterialTheme.typography.bodyMedium,
                color = accentPalette.secondaryOnQuietContainer,
            )
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentMusicComments,
    index: Int,
    count: Int,
    accentPalette: ContentAccentPalette,
) {
    val nickname = comment.user.nickname.ifBlank { "网易云音乐用户" }
    Surface(
        shape = connectedListItemShape(index, count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ExpressiveLayout.PageHorizontalPadding,
                vertical = ExpressiveLayout.ConnectedItemGap,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AsyncImage(
                model = comment.user.avatarUrl,
                contentDescription = "$nickname 的头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentPalette.container),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nickname,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (comment.likedCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = accentPalette.container,
                            contentColor = accentPalette.onContainer,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = "获得 ${comment.likedCount} 个赞"
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = AppIcons.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "${comment.likedCount} 赞",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = comment.content.ifBlank { "（无文字内容）" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (comment.timeStr.isNotBlank()) {
                    Text(
                        text = comment.timeStr,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentPalette.secondaryOnQuietContainer,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
