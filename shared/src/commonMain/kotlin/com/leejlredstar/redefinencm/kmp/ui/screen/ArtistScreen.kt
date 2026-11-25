package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.AlbumSummary
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** An artist profile, their most played songs, and their albums. */
@Composable
fun ArtistScreen(
    artistId: Long,
    scaffoldPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    repository: Repository = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
) {
    var reload by remember { mutableIntStateOf(0) }
    val load by rememberCatalogLoad("artist:$artistId", reload) { repository.getArtistPage(artistId) }
    val page = (load as? CatalogLoad.Loaded)?.value
    val profile = page?.profile
    val picture = profile?.avatar?.ifBlank { null } ?: profile?.cover
    val accent = rememberArtworkAccent(
        requestKey = picture,
        fallback = MaterialTheme.colorScheme.tertiaryContainer,
        label = "artistAccent",
    )
    val playWholeList = remember {
        settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT)
    }
    val songs = page?.topSongs.orEmpty()
    val queue = remember(songs) { songs.map { it.toMediaInfo() } }
    val artistName = profile?.name.orEmpty()
    val source = UiText { it.playbackSourceArtist(artistName) }

    // Albums after the first page, fetched on request.
    var extraAlbums by remember(artistId) { mutableStateOf<List<AlbumSummary>>(emptyList()) }
    var moreAlbums by remember(page) { mutableStateOf(page?.moreAlbums == true) }
    var albumsLoading by remember(artistId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val albums = page?.albums.orEmpty() + extraAlbums

    CatalogPage(title = artistName, onBack = onBack, scaffoldPadding = scaffoldPadding) {
        when (load) {
            CatalogLoad.Loading -> item(key = "artist-loading") {
                ExpressiveLoadingState(label = strings.artistLoading, modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current))
            }
            CatalogLoad.Failed -> item(key = "artist-error") {
                ExpressiveStatePanel(
                    title = strings.artistLoadFailed,
                    message = strings.checkNetworkAndRetry,
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    actionLabel = strings.retry,
                    onAction = { reload++ },
                    modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current),
                )
            }
            is CatalogLoad.Loaded -> if (profile != null) {
                item(key = "header") {
                    val gutter = LocalExpressivePageGutter.current
                    val alias = (profile.transNames + profile.alias).distinct().joinToString(" / ")
                    val metadata = strings.artistSongAndAlbumCount(profile.musicSize, profile.albumSize)
                    Column(
                        modifier = Modifier.padding(start = gutter, end = gutter, top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CatalogArtworkHeader(
                            artwork = { modifier ->
                                ExpressiveArtwork(
                                    model = picture,
                                    contentDescription = null,
                                    modifier = modifier,
                                    shape = CircleShape,
                                    placeholderIcon = AppIcons.Person,
                                    containerColor = accent.palette.container,
                                    contentColor = accent.palette.onContainer,
                                    onImageLoaded = accent.extract,
                                )
                            },
                            details = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ExpressivePageHeading(
                                        title = profile.name,
                                        supportingText = alias.takeIf { it.isNotBlank() } ?: metadata,
                                    )
                                    if (alias.isNotBlank()) {
                                        Text(metadata, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            actions = if (queue.isNotEmpty()) {
                                {
                                    CatalogPrimaryButton(
                                        label = strings.playPopularSongs,
                                        icon = AppIcons.PlayArrow,
                                        onClick = {
                                            PlaybackSource.set(source)
                                            player.setQueue(queue, 0)
                                        },
                                    )
                                }
                            } else null,
                        )
                        if (profile.briefDesc.isNotBlank()) CatalogDescription(profile.briefDesc)
                    }
                }
                if (songs.isNotEmpty()) {
                    item(key = "songs-title") {
                        ExpressiveSectionTitle(
                            text = strings.popularSongs,
                            modifier = Modifier.padding(horizontal = LocalExpressivePageGutter.current, vertical = 12.dp),
                        )
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
                            album = song.al.name,
                            badge = songFeeBadge(song.fee),
                            actions = media?.let { rememberSongRowActions(media = it, neteaseSong = song) }.orEmpty(),
                        )
                    }
                }
                if (albums.isNotEmpty()) {
                    item(key = "albums-title") {
                        val gutter = LocalExpressivePageGutter.current
                        ExpressiveSectionTitle(
                            text = strings.albums,
                            modifier = Modifier.padding(start = gutter, end = gutter, top = 24.dp, bottom = 8.dp),
                        )
                    }
                    itemsIndexed(albums, key = { _, album -> "album-${album.id}" }) { index, album ->
                        AlbumRow(album, index, albums.size) { onOpenAlbum(album.id) }
                    }
                    if (moreAlbums) {
                        item(key = "albums-more") {
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = LocalExpressivePageGutter.current, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                TextButton(
                                    enabled = !albumsLoading,
                                    onClick = {
                                        albumsLoading = true
                                        scope.launch {
                                            val next = repository.getArtistAlbums(artistId, offset = albums.size)
                                            if (next != null) {
                                                val shown = albums.mapTo(mutableSetOf()) { it.id }
                                                extraAlbums = extraAlbums + next.hotAlbums.filterNot { it.id in shown }
                                                moreAlbums = next.more && next.hotAlbums.isNotEmpty()
                                            }
                                            albumsLoading = false
                                        }
                                    },
                                ) {
                                    Text(if (albumsLoading) strings.loading else strings.moreAlbums)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumRow(album: AlbumSummary, index: Int, count: Int, onClick: () -> Unit) {
    val metadata = listOfNotNull(
        formatReleaseDate(album.publishTime).ifBlank { null },
        album.size.takeIf { it > 0 }?.let { strings.songCount(it) },
    ).joinToString(" · ")
    SegmentedListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().catalogRowPadding(),
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = {
            ExpressiveArtwork(
                model = album.picUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.small,
                placeholderIcon = AppIcons.Album,
            )
        },
        supportingContent = if (metadata.isNotBlank()) { { Text(metadata) } } else null,
        trailingContent = { Icon(AppIcons.KeyboardArrowRight, contentDescription = null) },
    ) {
        Text(album.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
