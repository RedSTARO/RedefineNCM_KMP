package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusicComments
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette

private data class QueueSheetEntry(
    val key: String,
    val media: MediaInfo,
)

private data class CommentSheetEntry(
    val key: String,
    val comment: CommentMusicComments,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    playlist: List<MediaInfo>,
    currentIndex: Int,
    accentPalette: ContentAccentPalette,
    onDismiss: () -> Unit,
    onSeekClick: (Int) -> Unit,
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

    LaunchedEffect(currentIndex, playlist.size) {
        if (currentIndex >= 0 && currentIndex < playlist.size) {
            listState.scrollToItem(currentIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = accentPalette.pageEnd,
        contentColor = accentPalette.onQuietContainer,
    ) {
        ExpressiveSectionTitle(
            text = "播放队列",
            supportingText = if (playlist.isEmpty()) "暂无待播放歌曲" else "共 ${playlist.size} 首歌曲",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (queueEntries.isEmpty()) {
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
                items = queueEntries,
                key = { _, entry -> entry.key },
            ) { index, entry ->
                val item = entry.media
                val isCurrent = index == currentIndex
                val title = item.title.ifBlank { "未知歌曲" }
                val artist = item.artist.ifBlank { "未知歌手" }
                Surface(
                    onClick = { onSeekClick(index); onDismiss() },
                    shape = connectedListItemShape(index, queueEntries.size),
                    color = if (isCurrent) accentPalette.container else accentPalette.quietContainer,
                    contentColor = if (isCurrent) accentPalette.onContainer else accentPalette.onQuietContainer,
                    modifier = Modifier
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
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) accentPalette.onContainer else accentPalette.accent,
                            modifier = Modifier.width(36.dp),
                        )
                        AsyncImage(
                            model = item.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(MaterialTheme.shapes.medium),
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
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    comments: List<CommentMusicComments>,
    accentPalette: ContentAccentPalette,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
) {
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = accentPalette.pageEnd,
        contentColor = accentPalette.onQuietContainer,
    ) {
        ExpressiveSectionTitle(
            text = "歌曲评论",
            supportingText = when {
                isLoading -> "正在加载评论"
                errorMessage != null -> "评论暂时无法加载"
                comments.isEmpty() -> "暂无评论"
                else -> "共 ${comments.size} 条评论"
            },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (isLoading) {
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
                        title = "还没有评论",
                        message = "这首歌暂时没有可显示的评论。",
                        icon = AppIcons.Comment,
                        accentPalette = accentPalette,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (!isLoading && errorMessage == null) {
                itemsIndexed(
                    items = commentEntries,
                    key = { _, entry -> entry.key },
                ) { index, entry ->
                    val comment = entry.comment
                    val nickname = comment.user.nickname.ifBlank { "网易云音乐用户" }
                    Surface(
                        shape = connectedListItemShape(index, commentEntries.size),
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
            }
        }
    }
}
