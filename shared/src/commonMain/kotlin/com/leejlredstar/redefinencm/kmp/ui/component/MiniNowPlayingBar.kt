package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentColorFor
import com.leejlredstar.redefinencm.kmp.util.themeColorFromCoilImage
import org.koin.compose.koinInject

/**
 * 迷你播放条 FAB（原版 MiniNowPlaying）：封面取色作容器色（spring 动画），
 * 内容色按亮度自适应黑/白。全局浮层保持紧凑，避免遮挡列表内容。
 */
@Composable
fun MiniNowPlayingBar(
    onExpand: () -> Unit,
    player: PlatformPlayer = koinInject(),
) {
    val media by player.currentMedia.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    val hasMedia = media != null
    val totalDuration = duration
        .takeIf { it > 0 }
        ?: media?.duration?.takeIf { it > 0 }
        ?: 0L
    val progress = if (totalDuration > 0L) {
        (position.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val defaultContainerColor = MaterialTheme.colorScheme.primaryContainer
    var themeColor by remember { mutableStateOf(defaultContainerColor) }
    val accentPalette = contentAccentPalette(themeColor)
    val containerColor by animateColorAsState(
        targetValue = accentPalette.container,
        animationSpec = spring(),
        label = "miniPlayerColor",
    )
    val contentColor = contentColorFor(containerColor)

    Surface(
        onClick = onExpand,
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 6.dp)
            // FAB slot 没有高度约束，使用固定紧凑高度，避免在列表页遮挡视野。
            .fillMaxWidth(0.88f)
            .widthIn(min = 220.dp, max = 520.dp)
            .height(72.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 4.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasMedia) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(media?.artworkUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(MaterialTheme.shapes.medium),
                        onSuccess = { state ->
                            themeColorFromCoilImage(state.result.image)?.let { themeColor = Color(it) }
                        },
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(50.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = contentColor.copy(alpha = 0.16f),
                        contentColor = contentColor,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = media?.title?.takeIf { it.isNotBlank() } ?: "Not playing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = media?.artist?.takeIf { it.isNotBlank() } ?: "No playback yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(MaterialTheme.shapes.small),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.22f),
                    )
                }

                Spacer(Modifier.width(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { player.seekToPrevious() },
                        enabled = hasMedia,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.KeyboardArrowLeft,
                            contentDescription = "上一首",
                        )
                    }
                    FilledIconButton(
                        onClick = { player.togglePlayPause() },
                        enabled = hasMedia,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = contentColor.copy(alpha = 0.18f),
                            contentColor = contentColor,
                            disabledContainerColor = contentColor.copy(alpha = 0.08f),
                            disabledContentColor = contentColor.copy(alpha = 0.42f),
                        ),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                        )
                    }
                    IconButton(
                        onClick = { player.seekToNext() },
                        enabled = hasMedia,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.KeyboardArrowRight,
                            contentDescription = "下一首",
                        )
                    }
                }
            }
        }
    }
}
