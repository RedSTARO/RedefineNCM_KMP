package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.SongWikiSection
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.viewmodel.SongWikiUiState

@Composable
fun SongWikiDetailsButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
            disabledContentColor = tint.copy(alpha = 0.38f),
        ),
    ) {
        Icon(
            imageVector = AppIcons.Info,
            contentDescription = "详细信息",
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongWikiDetailsSheet(
    visible: Boolean,
    songTitle: String?,
    state: SongWikiUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    songArtist: String? = null,
    albumTitle: String? = null,
    artworkUri: String? = null,
    durationMs: Long? = null,
    artworkOverlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            SongWikiHero(
                songTitle = songTitle,
                songArtist = songArtist,
                albumTitle = albumTitle,
                artworkUri = artworkUri,
                durationMs = durationMs,
                artworkOverlay = artworkOverlay,
            )

            Text(
                text = "歌曲档案",
                modifier = Modifier
                    .padding(start = 24.dp, top = 24.dp, bottom = 10.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                when (state) {
                    is SongWikiUiState.Idle,
                    is SongWikiUiState.Loading -> ExpressiveLoadingState(
                        label = "正在加载音乐百科…",
                        accentColor = MaterialTheme.colorScheme.primary,
                    )
                    is SongWikiUiState.Empty -> ExpressiveStatePanel(
                        title = "暂无简要信息",
                        message = "这首歌曲暂未提供音乐百科内容。",
                        icon = AppIcons.Info,
                    )
                    is SongWikiUiState.Error -> ExpressiveStatePanel(
                        title = "音乐百科加载失败",
                        message = state.message,
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        actionLabel = "重试",
                        onAction = onRetry,
                    )
                    is SongWikiUiState.Content -> SongWikiSectionList(state.summary.sections)
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SongWikiHero(
    songTitle: String?,
    songArtist: String?,
    albumTitle: String?,
    artworkUri: String?,
    durationMs: Long?,
    artworkOverlay: (@Composable BoxScope.() -> Unit)?,
) {
    val artworkShape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(artworkShape),
                contentAlignment = Alignment.Center,
            ) {
                ExpressiveArtwork(
                    model = artworkUri,
                    contentDescription = songTitle
                        ?.takeIf(String::isNotBlank)
                        ?.let { "$it 的专辑封面" },
                    modifier = Modifier.fillMaxSize(),
                    shape = artworkShape,
                )
                artworkOverlay?.invoke(this)
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = "音乐百科 · 简要信息",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = songTitle?.takeIf(String::isNotBlank) ?: "当前歌曲",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                songArtist?.takeIf(String::isNotBlank)?.let { artist ->
                    SongWikiMetadataLine("作者", artist)
                }
                albumTitle?.takeIf(String::isNotBlank)?.let { album ->
                    SongWikiMetadataLine("专辑", album)
                }
                durationMs?.takeIf { it > 0 }?.let { duration ->
                    SongWikiMetadataLine("时长", formatSongDuration(duration))
                }
            }
        }
    }
}

@Composable
private fun SongWikiMetadataLine(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatSongDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

@Composable
private fun SongWikiSectionList(sections: List<SongWikiSection>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(ExpressiveLayout.ConnectedItemGap),
    ) {
        itemsIndexed(
            items = sections,
            key = { index, section -> "${section.title}:$index" },
        ) { index, section ->
            Surface(
                shape = connectedListItemShape(index, sections.size),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = section.title,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (section.values.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            section.values.forEach { value ->
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) {
                                    Text(
                                        text = value,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                    section.description?.takeIf(String::isNotBlank)?.let { description ->
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
