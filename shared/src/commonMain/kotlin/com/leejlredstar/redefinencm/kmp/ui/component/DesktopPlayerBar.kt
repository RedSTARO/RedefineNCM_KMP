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
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel

/**
 * The desktop's playback bar, fixed along the bottom of the window beside the sidebar.
 *
 * It holds the full set of controls in one place: the song (opening the player), the transport
 * and the progress, and on the right the lyrics, the queue, the comments and the volume. Narrow
 * windows drop the shuffle and the volume slider before anything else.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val colors = MaterialTheme.colorScheme
    val clockStyle = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
    Surface(
        color = colors.surfaceContainer,
        contentColor = colors.onSurface,
        modifier = modifier.fillMaxWidth().height(DesktopPlayerBarHeight),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val roomy = maxWidth >= 760.dp
            // The right-hand controls keep their own width and the transport never goes below
            // what its buttons need; the song takes what is left. Shared out by weight, the
            // right group was clipped in windows about 600 to 760dp and 856 to 1155dp wide.
            val rightGroupWidth = BarIconButtonWidth * 3 + VolumeIconWidth +
                if (roomy) VolumeSliderWidth else 0.dp
            val flexibleWidth = (maxWidth - BarHorizontalPadding * 2 - BarIconButtonWidth - rightGroupWidth)
                .coerceAtLeast(0.dp)
            val transportWidth = (flexibleWidth * TransportShare)
                .coerceAtLeast(if (roomy) TransportMinWidthRoomy else TransportMinWidth)
                .coerceAtMost(flexibleWidth)
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = BarHorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The song: tapping it opens the player.
                Surface(
                    onClick = onOpenNowPlaying,
                    enabled = hasMedia,
                    color = accentPalette.quietContainer,
                    contentColor = accentPalette.onQuietContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = strings.openPlayerFor(media?.title ?: strings.currentSong)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ExpressiveArtwork(
                            model = artwork.takeIf(String::isNotBlank),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            shape = MaterialTheme.shapes.small,
                            containerColor = accentPalette.container,
                            contentColor = accentPalette.onContainer,
                            onImageLoaded = extractAccent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = media?.title?.takeIf { it.isNotBlank() } ?: strings.notPlaying,
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                nowPlaying.providerBadge?.let { label ->
                                    ProviderBadge(
                                        label = label,
                                        contentColor = accentPalette.onContainer,
                                        containerColor = accentPalette.container,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                }
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
                }
                IconButton(onClick = viewModel::onFavClick, enabled = nowPlaying.canFavorite) {
                    Icon(
                        imageVector = if (nowPlaying.isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                        contentDescription = if (nowPlaying.isFavorite) strings.liked else strings.like,
                        // An explicit tint ignores the button's disabled state, so dim it here.
                        tint = when {
                            !nowPlaying.canFavorite -> colors.onSurface.copy(alpha = 0.38f)
                            nowPlaying.isFavorite -> accentPalette.accent
                            else -> colors.onSurfaceVariant
                        },
                    )
                }

                // The transport and the progress, in the middle.
                Column(
                    modifier = Modifier.width(transportWidth).padding(horizontal = 12.dp),
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
                                    contentDescription = strings.shuffle,
                                    tint = if (nowPlaying.shuffleEnabled) accentPalette.accent else accentPalette.secondaryOnQuietContainer,
                                )
                            }
                        }
                        IconButton(onClick = viewModel::onPervClick, enabled = hasMedia) {
                            Icon(AppIcons.SkipPrevious, contentDescription = strings.previous)
                        }
                        PlaybackPlayButton(
                            isPlaying = nowPlaying.isPlaying,
                            enabled = hasMedia,
                            onClick = viewModel::onPauseClick,
                            modifier = Modifier.size(
                                width = if (roomy) RoomyPlayWidth else BarIconButtonWidth,
                                height = BarIconButtonWidth,
                            ),
                            containerColor = accentPalette.accent,
                            contentColor = accentPalette.onAccent,
                        )
                        IconButton(onClick = viewModel::onNextClick, enabled = hasMedia) {
                            Icon(AppIcons.SkipNext, contentDescription = strings.next)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatPlaybackDuration(displayedPosition),
                            style = clockStyle,
                            color = colors.onSurfaceVariant,
                        )
                        PlaybackSeekBar(
                            state = seek,
                            progress = nowPlaying.progress,
                            totalDuration = nowPlaying.totalDuration,
                            enabled = hasMedia && nowPlaying.totalDuration > 0L,
                            accentPalette = accentPalette,
                            onSeek = viewModel::onPositionSeekClick,
                            modifier = Modifier.weight(1f).height(24.dp).padding(horizontal = 8.dp)
                                .semantics { contentDescription = strings.playbackPosition },
                        )
                        Text(
                            text = formatPlaybackDuration(nowPlaying.totalDuration),
                            style = clockStyle,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }

                // The rest of the player, on the right.
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenLyrics,
                        enabled = hasMedia,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = accentPalette.quietContainer,
                            contentColor = accentPalette.onQuietContainer,
                        ),
                    ) {
                        Icon(AppIcons.FormatQuote, contentDescription = strings.lyrics)
                    }
                    IconButton(
                        onClick = {
                            viewModel.onPlaylistClick()
                            sheets.openQueue()
                        },
                        enabled = hasMedia,
                    ) {
                        Icon(AppIcons.QueueMusic, contentDescription = strings.queue)
                    }
                    IconButton(onClick = sheets::openComments, enabled = nowPlaying.canComment) {
                        Icon(AppIcons.Comment, contentDescription = strings.comments)
                    }
                    Box(Modifier.width(8.dp), contentAlignment = Alignment.CenterStart) {
                        Spacer(Modifier.width(1.dp).height(20.dp).background(colors.outlineVariant))
                    }
                    val volumeIcon = when (outputVolumeLevel(volume)) {
                        OutputVolumeLevel.MUTED -> AppIcons.VolumeOff
                        OutputVolumeLevel.LOW -> AppIcons.VolumeDown
                        OutputVolumeLevel.HIGH -> AppIcons.VolumeUp
                    }
                    Icon(
                        imageVector = volumeIcon,
                        contentDescription = strings.volumePercent(formatOutputVolumePercent(volume)),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    if (roomy) {
                        Slider(
                            value = volume.coerceIn(0f, 1f),
                            onValueChange = player::setVolume,
                            valueRange = 0f..1f,
                            // Named like the lyric page's volume slider; the icon beside it is a
                            // separate node a screen reader reaches first.
                            modifier = Modifier
                                .width(VolumeSliderWidth)
                                .padding(start = 4.dp)
                                .semantics {
                                    contentDescription = strings.outputVolumePercent(formatOutputVolumePercent(volume))
                                },
                            colors = SliderDefaults.colors(
                                thumbColor = accentPalette.accent,
                                activeTrackColor = accentPalette.accent,
                                inactiveTrackColor = colors.onSurfaceVariant.copy(alpha = 0.24f),
                            ),
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
}

/** The height the bar takes from the content above it. */
internal val DesktopPlayerBarHeight = 80.dp

private val BarHorizontalPadding = 16.dp

/** An IconButton's width: its minimum touch target. */
private val BarIconButtonWidth = 48.dp

/** The volume icon with its leading space. */
private val VolumeIconWidth = 28.dp

/** The volume slider with its leading space, shown when the bar is roomy. */
private val VolumeSliderWidth = 116.dp

/** Previous, play and next with their spacing, plus the column's own padding. */
private val RoomyPlayWidth = 60.dp
private val TransportMinWidth = BarIconButtonWidth * 3 + 32.dp

/** The same with shuffle in front. */
private val TransportMinWidthRoomy = TransportMinWidth + RoomyPlayWidth + 4.dp

/** The transport's share of the width the song and the transport split. */
private const val TransportShare = 1.4f / 2.4f
