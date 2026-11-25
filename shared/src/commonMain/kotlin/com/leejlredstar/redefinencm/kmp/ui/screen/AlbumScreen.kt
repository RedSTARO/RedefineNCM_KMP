package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import org.koin.compose.koinInject

/** An album and its songs, reachable from any song on it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumScreen(
    albumId: Long,
    scaffoldPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    repository: Repository = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
) {
    var reload by remember { mutableIntStateOf(0) }
    val load by rememberCatalogLoad("album:$albumId", reload) { repository.getAlbum(albumId) }
    val detail = (load as? CatalogLoad.Loaded)?.value
    val album = detail?.album
    val accent = rememberArtworkAccent(
        requestKey = album?.picUrl,
        fallback = MaterialTheme.colorScheme.tertiaryContainer,
        label = "albumAccent",
    )
    val playWholeList = remember {
        settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT)
    }
    val songs = detail?.songs.orEmpty()
    val queue = remember(songs) { songs.map { it.toMediaInfo() } }
    val albumName = album?.name.orEmpty()
    val source = UiText { it.playbackSourceAlbum(albumName) }

    CatalogPage(title = album?.name ?: strings.albums, onBack = onBack, scaffoldPadding = scaffoldPadding) {
        when (load) {
            CatalogLoad.Loading -> item(key = "album-loading") {
                ExpressiveLoadingState(label = strings.albumLoading, modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current))
            }
            CatalogLoad.Failed -> item(key = "album-error") {
                ExpressiveStatePanel(
                    title = strings.albumLoadFailed,
                    message = strings.checkNetworkAndRetry,
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    actionLabel = strings.retry,
                    onAction = { reload++ },
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            is CatalogLoad.Loaded -> if (album != null) {
                item(key = "header") {
                    val gutter = LocalExpressivePageGutter.current
                    val releaseDate = formatReleaseDate(album.publishTime)
                    val metadata = listOfNotNull(
                        releaseDate.takeIf { it.isNotBlank() }?.let { strings.albumReleaseDate(it) },
                        album.company?.takeIf { it.isNotBlank() },
                        strings.songCount(songs.size),
                    ).joinToString(" · ")
                    Column(
                        modifier = Modifier.padding(start = gutter, end = gutter, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CatalogArtworkHeader(
                            artwork = { modifier ->
                                ExpressiveArtwork(
                                    model = album.picUrl,
                                    contentDescription = null,
                                    modifier = modifier,
                                    shape = MaterialTheme.shapes.extraLarge,
                                    placeholderIcon = AppIcons.Album,
                                    containerColor = accent.palette.container,
                                    contentColor = accent.palette.onContainer,
                                    onImageLoaded = accent.extract,
                                )
                            },
                            details = { ExpressivePageHeading(album.name, supportingText = metadata) },
                            actions = if (queue.isNotEmpty()) {
                                {
                                    CatalogPrimaryButton(
                                        label = strings.playAll,
                                        icon = AppIcons.PlayArrow,
                                        onClick = {
                                            PlaybackSource.set(source)
                                            player.setQueue(queue, 0)
                                        },
                                    )
                                }
                            } else null,
                        )
                        val artists = album.artists.filter { it.id != 0L }
                        if (artists.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                artists.forEach { artist ->
                                    AssistChip(
                                        onClick = { onOpenArtist(artist.id) },
                                        label = { Text(artist.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                        leadingIcon = {
                                            Icon(AppIcons.Person, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                                        },
                                    )
                                }
                            }
                        }
                        album.description?.takeIf { it.isNotBlank() }?.let { CatalogDescription(it) }
                    }
                }
                itemsIndexed(songs, key = { _, song -> "song-${song.id}" }) { index, song ->
                    val media = queue.getOrNull(index)
                    SongRow(
                        index = index,
                        title = song.name,
                        artist = song.ar.joinToString(" / ") { it.name },
                        artworkUri = song.al.picUrl,
                        shape = connectedListItemShape(index, songs.size),
                        onClick = { playFromList(player, queue, index, playWholeList, source = source) },
                        songId = song.id,
                        accentColor = accent.color,
                        mediaId = media?.id,
                        durationMs = song.dt,
                        badge = songFeeBadge(song.fee),
                        actions = media?.let { rememberSongRowActions(media = it, neteaseSong = song) }.orEmpty(),
                    )
                }
            }
        }
    }
}
