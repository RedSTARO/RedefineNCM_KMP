package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentColorFor
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import org.koin.compose.koinInject

/**
 * 迷你播放条 FAB：右下角紧凑入口，只保留封面、播放状态和细进度，避免遮挡页面内容。
 */
@Composable
fun MiniNowPlayingBar(
    onExpand: () -> Unit,
    onAccentColor: (Color) -> Unit = {},
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
    var themeColor by remember(media?.artworkUri, defaultContainerColor) {
        mutableStateOf(defaultContainerColor)
    }
    val extractThemeColor = rememberThemeColorExtractor(media?.artworkUri) { extracted ->
        themeColor = extracted
        onAccentColor(extracted)
    }
    val accentPalette = contentAccentPalette(themeColor)
    val containerColor by animateColorAsState(
        targetValue = accentPalette.container,
        animationSpec = spring(),
        label = "miniPlayerColor",
    )
    val contentColor = contentColorFor(containerColor)

    Surface(
        modifier = Modifier
            .padding(end = 2.dp, bottom = 2.dp)
            // FAB slot 没有高度约束，使用固定小尺寸贴右下角，避免覆盖列表主体。
            .width(116.dp)
            .height(60.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 4.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Box {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = onExpand,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = "打开${media?.title ?: "当前歌曲"}播放页"
                            },
                        shape = CircleShape,
                        color = contentColor.copy(alpha = 0.16f),
                        contentColor = contentColor,
                    ) {
                        if (hasMedia) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(media?.artworkUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                onSuccess = { state -> extractThemeColor(state.result.image) },
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    FilledIconButton(
                        onClick = { player.togglePlayPause() },
                        enabled = hasMedia,
                        modifier = Modifier.size(48.dp),
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
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp)
                        .width(70.dp)
                        .height(2.dp)
                        .clip(CircleShape),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.20f),
                )
            }
        }
    }
}
