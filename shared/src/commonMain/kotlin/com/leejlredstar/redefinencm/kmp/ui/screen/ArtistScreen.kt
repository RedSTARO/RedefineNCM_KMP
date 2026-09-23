package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.data.ArtistPage
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.AlbumSummary
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * An artist: who they are, their fifty most played songs and their albums. The artist names on
 * songs and on the player lead here.
 */
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
    val load by produceState<CatalogLoad<ArtistPage>>(CatalogLoad.Loading, artistId, reload) {
        value = CatalogLoad.Loading
        value = repository.getArtistPage(artistId)?.let { CatalogLoad.Loaded(it) } ?: CatalogLoad.Failed
    }
    val page = (load as? CatalogLoad.Loaded)?.value
    val picture = page?.profile?.avatar?.ifBlank { null } ?: page?.profile?.cover
    val accent = rememberArtworkAccent(
        requestKey = picture,
        fallback = MaterialTheme.colorScheme.tertiaryContainer,
        label = "artistAccent",
    )
    val palette = accent.palette
    val playWholeList = remember {
        settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT)
    }
    val songs = page?.topSongs.orEmpty()
    val queue = remember(songs) { songs.map { it.toMediaInfo() } }
    val source = "歌手「${page?.profile?.name.orEmpty()}」"

    // Albums after the first page, fetched on request.
    var extraAlbums by remember(artistId) { mutableStateOf<List<AlbumSummary>>(emptyList()) }
    var moreAlbums by remember(page) { mutableStateOf(page?.moreAlbums == true) }
    var albumsLoading by remember(artistId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val albums = page?.albums.orEmpty() + extraAlbums

    ExpressivePage(accentPalette = palette, contentWindowInsets = WindowInsets.statusBars) {
        when (load) {
            CatalogLoad.Loading -> Column {
                CatalogBackRow(palette, onBack)
                ExpressiveLoadingState(
                    label = "正在加载歌手…",
                    accentColor = palette.accent,
                    modifier = Modifier.padding(16.dp),
                )
            }
            CatalogLoad.Failed -> Column {
                CatalogBackRow(palette, onBack)
                ExpressiveStatePanel(
                    title = "歌手加载失败",
                    message = "请检查网络后重试。",
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    accentPalette = palette,
                    actionLabel = "重试",
                    onAction = { reload++ },
                    modifier = Modifier.padding(16.dp),
                )
            }
            is CatalogLoad.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding() + 16.dp),
            ) {
                item(key = "back") { CatalogBackRow(palette, onBack) }
                item(key = "header") {
                    val profile = page!!.profile
                    ArtistHeader(
                        name = profile.name,
                        alias = (profile.transNames + profile.alias).distinct().joinToString(" / "),
                        picture = picture,
                        songCount = profile.musicSize,
                        albumCount = profile.albumSize,
                        briefDesc = profile.briefDesc,
                        accentPalette = palette,
                        onImageLoaded = accent.extract,
                        onPlay = if (queue.isEmpty()) {
                            null
                        } else {
                            {
                                PlaybackSource.set(source)
                                player.setQueue(queue, 0)
                            }
                        },
                    )
                }
                if (songs.isNotEmpty()) {
                    item(key = "songs-title") {
                        ExpressiveSectionTitle(
                            text = "热门歌曲",
                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }
                    itemsIndexed(songs, key = { _, song -> "song-${song.id}" }) { index, song ->
                        val media = queue.getOrNull(index)
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            SongRow(
                                index = index,
                                title = song.name,
                                artist = song.ar.joinToString(" / ") { it.name },
                                artworkUri = song.al.picUrl,
                                shape = connectedListItemShape(index, songs.size),
                                onClick = {
                                    playFromList(player, queue, index, playWholeList, source = source)
                                },
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
                }
                if (albums.isNotEmpty()) {
                    item(key = "albums-title") {
                        ExpressiveSectionTitle(
                            text = "专辑",
                            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                        )
                    }
                    itemsIndexed(albums, key = { _, album -> "album-${album.id}" }) { index, album ->
                        AlbumRow(
                            album = album,
                            shape = connectedListItemShape(index, albums.size),
                            accentPalette = palette,
                            onClick = { onOpenAlbum(album.id) },
                        )
                    }
                    if (moreAlbums) {
                        item(key = "albums-more") {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
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
                                    Text(if (albumsLoading) "正在加载…" else "更多专辑")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistHeader(
    name: String,
    alias: String,
    picture: String?,
    songCount: Int,
    albumCount: Int,
    briefDesc: String,
    accentPalette: ContentAccentPalette,
    onImageLoaded: (coil3.Image) -> Unit,
    onPlay: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { state -> onImageLoaded(state.result.image) },
                modifier = Modifier.size(112.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentPalette.onPageStart,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (alias.isNotBlank()) {
                    Text(
                        text = alias,
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentPalette.secondaryOnPageStart,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "$songCount 首歌 · $albumCount 张专辑",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnPageStart,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (briefDesc.isNotBlank()) {
            Surface(
                onClick = { expanded = !expanded },
                color = accentPalette.quietContainer,
                contentColor = accentPalette.onQuietContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Text(
                    text = briefDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (onPlay != null) {
            FilledTonalButton(
                onClick = onPlay,
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = accentPalette.container,
                    contentColor = accentPalette.onContainer,
                ),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("播放热门歌曲")
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** One album in a list: cover, name, release date and track count. */
@Composable
internal fun AlbumRow(
    album: AlbumSummary,
    shape: androidx.compose.ui.graphics.Shape,
    accentPalette: ContentAccentPalette,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = ExpressiveLayout.ConnectedItemGap),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = album.picUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        formatReleaseDate(album.publishTime).ifBlank { null },
                        album.size.takeIf { it > 0 }?.let { "$it 首" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
        }
    }
}
