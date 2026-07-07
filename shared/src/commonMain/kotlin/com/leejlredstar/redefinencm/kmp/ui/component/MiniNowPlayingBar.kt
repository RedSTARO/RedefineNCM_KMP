package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.util.themeColorFromCoilImage
import org.koin.compose.koinInject

/**
 * 迷你播放条 FAB（原版 MiniNowPlaying）：封面取色作容器色（spring 动画），
 * 内容色按亮度自适应黑/白，封面居左，标题跑马灯。
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
    val containerColor by animateColorAsState(
        targetValue = themeColor,
        animationSpec = spring(),
        label = "miniPlayerColor",
    )
    val contentColor = if (containerColor.luminance() > 0.5f) Color.Black else Color.White

    Surface(
        onClick = onExpand,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            // 固定尺寸（原版 300×112）：FAB slot 没有高度约束，内部 fillMaxHeight
            // 会让条子撑满整个窗口（825c22c 修过的回归）
            .size(width = 300.dp, height = 112.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 4.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier.padding(10.dp),
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
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.large),
                        onSuccess = { state ->
                            themeColorFromCoilImage(state.result.image)?.let { themeColor = Color(it) }
                        },
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = MaterialTheme.shapes.large,
                        color = contentColor.copy(alpha = 0.16f),
                        contentColor = contentColor,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = media?.title?.takeIf { it.isNotBlank() } ?: "Not playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                    )
                    Text(
                        text = media?.artist?.takeIf { it.isNotBlank() } ?: "No playback yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(MaterialTheme.shapes.small),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.22f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (hasMedia) {
                                "${formatMiniDuration(position)} / ${formatMiniDuration(totalDuration)}"
                            } else {
                                "0:00 / 0:00"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.72f),
                            maxLines = 1,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { player.seekToPrevious() },
                                enabled = hasMedia,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = AppIcons.KeyboardArrowLeft,
                                    contentDescription = "Previous",
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
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                )
                            }
                            IconButton(
                                onClick = { player.seekToNext() },
                                enabled = hasMedia,
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    imageVector = AppIcons.KeyboardArrowRight,
                                    contentDescription = "Next",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMiniDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
