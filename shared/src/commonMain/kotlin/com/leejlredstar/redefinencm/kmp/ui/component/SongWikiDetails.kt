package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.64f),
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
    val colorScheme = MaterialTheme.colorScheme
    val heroShape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = heroShape,
        color = Color.Transparent,
        contentColor = colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .songWikiHeroBackground(
                    primary = colorScheme.primary,
                    surfaceContainer = colorScheme.surfaceContainer,
                    surfaceContainerHighest = colorScheme.surfaceContainerHighest,
                ),
        ) {
            val compactLayout = maxWidth <= 600.dp
            val artworkSize = if (compactLayout) 108.dp else 144.dp
            val artworkShape = RoundedCornerShape(if (compactLayout) 26.dp else 32.dp)
            val heroPadding = if (compactLayout) 20.dp else 24.dp
            val heroGap = if (compactLayout) 18.dp else 24.dp
            Row(
                modifier = Modifier.fillMaxWidth().padding(heroPadding),
                horizontalArrangement = Arrangement.spacedBy(heroGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(artworkSize)
                        .shadow(
                            elevation = 10.dp,
                            shape = artworkShape,
                            clip = false,
                        )
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
                    Text(
                        text = "音乐百科 · 简要信息",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = colorScheme.primary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = songTitle?.takeIf(String::isNotBlank) ?: "当前歌曲",
                        modifier = Modifier.semantics { heading() },
                        style = if (compactLayout) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.headlineLarge
                        },
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val metadata = buildList {
                        songArtist?.takeIf(String::isNotBlank)?.let { add("作者" to it) }
                        albumTitle?.takeIf(String::isNotBlank)?.let { add("专辑" to it) }
                        durationMs
                            ?.takeIf { it > 0 }
                            ?.let { add("时长" to formatSongDuration(it)) }
                    }
                    if (metadata.isNotEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(
                                ExpressiveLayout.ConnectedItemGap,
                            ),
                        ) {
                            metadata.forEachIndexed { index, (label, value) ->
                                SongWikiMetadataLine(
                                    label = label,
                                    value = value,
                                    index = index,
                                    count = metadata.size,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongWikiMetadataLine(
    label: String,
    value: String,
    index: Int,
    count: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = connectedListItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                modifier = Modifier.width(42.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Mirrors the layered `.wiki-hero` background in the shared AMLL player page.
 * The color roles intentionally map to the same values as the AMLL dark palette while
 * continuing to respect light and dynamic Material color schemes on native surfaces.
 */
private fun Modifier.songWikiHeroBackground(
    primary: Color,
    surfaceContainer: Color,
    surfaceContainerHighest: Color,
): Modifier = drawWithCache {
    val linearGradient = Brush.linearGradient(
        colors = listOf(surfaceContainerHighest, surfaceContainer),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val radialGradient = Brush.radialGradient(
        colors = listOf(primary.copy(alpha = 0.20f), Color.Transparent),
        center = Offset(size.width * 0.18f, size.height * 0.12f),
        radius = size.maxDimension * 0.56f,
    )
    onDrawBehind {
        drawRect(linearGradient)
        drawRect(radialGradient)
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
