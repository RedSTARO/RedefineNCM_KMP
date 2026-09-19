package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel

/**
 * The desktop's playback bar, fixed along the bottom of the window beside the sidebar.
 *
 * The desktop used to have a 116dp pill in the corner for its always-visible controls, and a
 * fuller card only inside the expanded sidebar of tall windows. This is the full set in one
 * place: the song (opening the player), the transport and the progress, and on the right the
 * lyrics, the queue, the comments and the volume. Narrow windows drop the shuffle and the volume
 * slider before anything else.
 */
@Composable
internal fun DesktopPlayerBar(
    player: PlatformPlayer,
    viewModel: NowPlayingViewModel,
    accentPalette: ContentAccentPalette,
    sheets: TransportSheetsState,
    onAccentColor: (Color) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowPlaying = rememberNowPlayingUiState(player, viewModel)
    val volume by player.volume.collectAsState()
    val media = nowPlaying.media
    val hasMedia = nowPlaying.hasMedia
    val artwork = media?.artworkUri.orEmpty()
    val extractAccent = rememberThemeColorExtractor(artwork, onAccentColor = onAccentColor)
    val seek = rememberSeekDragState(media?.id)
    val displayedPosition = seek.positionFor(nowPlaying.safePosition, nowPlaying.totalDuration)

    Surface(
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = modifier.fillMaxWidth().height(DesktopPlayerBarHeight),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val roomy = maxWidth >= 760.dp
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The song: tapping it opens the player.
                Surface(
                    onClick = onOpenNowPlaying,
                    enabled = hasMedia,
                    color = Color.Transparent,
                    contentColor = accentPalette.onQuietContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(MaterialTheme.shapes.small),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (artwork.isNotBlank()) {
                                AsyncImage(
                                    model = artwork,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    onSuccess = { state -> extractAccent(state.result.image) },
                                )
                            } else {
                                Icon(AppIcons.MusicNote, contentDescription = null)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = media?.title?.takeIf { it.isNotBlank() } ?: "未播放",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = media?.artist.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = accentPalette.secondaryOnQuietContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                IconButton(onClick = viewModel::onFavClick, enabled = hasMedia) {
                    Icon(
                        imageVector = if (nowPlaying.isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                        contentDescription = if (nowPlaying.isFavorite) "已喜欢" else "喜欢",
                        tint = if (nowPlaying.isFavorite) accentPalette.accent else accentPalette.onQuietContainer,
                    )
                }

                // The transport and the progress, in the middle.
                Column(
                    modifier = Modifier.weight(1.4f).padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (roomy) {
                            IconToggleButton(
                                checked = nowPlaying.shuffleEnabled,
                                onCheckedChange = { viewModel.onShuffleClick(it) },
                                enabled = hasMedia,
                            ) {
                                Icon(
                                    imageVector = if (nowPlaying.shuffleEnabled) AppIcons.ShuffleOn else AppIcons.Shuffle,
                                    contentDescription = "随机播放",
                                    tint = if (nowPlaying.shuffleEnabled) accentPalette.accent else accentPalette.secondaryOnQuietContainer,
                                )
                            }
                        }
                        IconButton(onClick = viewModel::onPervClick, enabled = hasMedia) {
                            Icon(AppIcons.SkipPrevious, contentDescription = "上一首")
                        }
                        FilledIconButton(
                            onClick = viewModel::onPauseClick,
                            enabled = hasMedia,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accentPalette.accent,
                                contentColor = accentPalette.onAccent,
                            ),
                        ) {
                            Icon(
                                imageVector = if (nowPlaying.isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                                contentDescription = if (nowPlaying.isPlaying) "暂停" else "播放",
                            )
                        }
                        IconButton(onClick = viewModel::onNextClick, enabled = hasMedia) {
                            Icon(AppIcons.SkipNext, contentDescription = "下一首")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatPlaybackDuration(displayedPosition),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentPalette.secondaryOnQuietContainer,
                        )
                        PlaybackSeekBar(
                            state = seek,
                            progress = nowPlaying.progress,
                            totalDuration = nowPlaying.totalDuration,
                            enabled = hasMedia && nowPlaying.totalDuration > 0L,
                            accentPalette = accentPalette,
                            onSeek = viewModel::onPositionSeekClick,
                            modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 8.dp),
                        )
                        Text(
                            text = formatPlaybackDuration(nowPlaying.totalDuration),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentPalette.secondaryOnQuietContainer,
                        )
                    }
                }

                // The rest of the player, on the right.
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOpenLyrics, enabled = hasMedia) {
                        Icon(AppIcons.FormatQuote, contentDescription = "歌词")
                    }
                    IconButton(
                        onClick = {
                            viewModel.onPlaylistClick()
                            sheets.openQueue()
                        },
                        enabled = hasMedia,
                    ) {
                        Icon(AppIcons.QueueMusic, contentDescription = "播放队列")
                    }
                    IconButton(onClick = sheets::openComments, enabled = hasMedia) {
                        Icon(AppIcons.Comment, contentDescription = "评论")
                    }
                    val volumeIcon = when (outputVolumeLevel(volume)) {
                        OutputVolumeLevel.MUTED -> AppIcons.VolumeOff
                        OutputVolumeLevel.LOW -> AppIcons.VolumeDown
                        OutputVolumeLevel.HIGH -> AppIcons.VolumeUp
                    }
                    Icon(
                        imageVector = volumeIcon,
                        contentDescription = "音量 ${formatOutputVolumePercent(volume)}",
                        tint = accentPalette.secondaryOnQuietContainer,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp),
                    )
                    if (roomy) {
                        Slider(
                            value = volume.coerceIn(0f, 1f),
                            onValueChange = player::setVolume,
                            valueRange = 0f..1f,
                            modifier = Modifier.width(112.dp).padding(start = 4.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = accentPalette.accent,
                                activeTrackColor = accentPalette.accent,
                                inactiveTrackColor = accentPalette.accent.copy(alpha = 0.24f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** The height the bar takes from the content above it. */
internal val DesktopPlayerBarHeight = 80.dp
