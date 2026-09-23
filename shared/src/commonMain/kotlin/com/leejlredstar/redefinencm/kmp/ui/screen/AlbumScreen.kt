package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.AlbumDetail
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongArtist
import com.leejlredstar.redefinencm.kmp.i18n.UiText
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import org.koin.compose.koinInject

/** An album and its songs, reachable from any song on it. */
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
    val load by produceState<CatalogLoad<AlbumDetail>>(CatalogLoad.Loading, albumId, reload) {
        value = CatalogLoad.Loading
        value = repository.getAlbum(albumId)?.let { CatalogLoad.Loaded(it) } ?: CatalogLoad.Failed
    }
    val detail = (load as? CatalogLoad.Loaded)?.value
    val accent = rememberArtworkAccent(
        requestKey = detail?.album?.picUrl,
        fallback = MaterialTheme.colorScheme.tertiaryContainer,
        label = "albumAccent",
    )
    val palette = accent.palette
    val playWholeList = remember {
        settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, SettingKeys.REPLACE_PLAYLIST_DEFAULT)
    }
    val songs = detail?.songs.orEmpty()
    val queue = remember(songs) { songs.map { it.toMediaInfo() } }
    val albumName = detail?.album?.name.orEmpty()
    val source = UiText { it.playbackSourceAlbum(albumName) }

    ExpressivePage(accentPalette = palette, contentWindowInsets = WindowInsets.statusBars) {
        when (load) {
            CatalogLoad.Loading -> Column {
                CatalogBackRow(palette, onBack)
                ExpressiveLoadingState(
                    label = strings.albumLoading,
                    accentColor = palette.accent,
                    modifier = Modifier.padding(16.dp),
                )
            }
            CatalogLoad.Failed -> Column {
                CatalogBackRow(palette, onBack)
                ExpressiveStatePanel(
                    title = strings.albumLoadFailed,
                    message = strings.checkNetworkAndRetry,
                    icon = AppIcons.Refresh,
                    tone = ExpressiveStateTone.Error,
                    accentPalette = palette,
                    actionLabel = strings.retry,
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
                    val album = detail!!.album
                    AlbumHeader(
                        name = album.name,
                        cover = album.picUrl,
                        artists = album.artists,
                        releaseDate = formatReleaseDate(album.publishTime),
                        company = album.company.orEmpty(),
                        trackCount = songs.size,
                        description = album.description.orEmpty(),
                        accentPalette = palette,
                        onImageLoaded = accent.extract,
                        onOpenArtist = onOpenArtist,
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
                itemsIndexed(songs, key = { _, song -> "song-${song.id}" }) { index, song ->
                    val media = queue.getOrNull(index)
                    Box(Modifier.padding(horizontal = 16.dp)) {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumHeader(
    name: String,
    cover: String,
    artists: List<SongArtist>,
    releaseDate: String,
    company: String,
    trackCount: Int,
    description: String,
    accentPalette: ContentAccentPalette,
    onImageLoaded: (coil3.Image) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlay: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { state -> onImageLoaded(state.result.image) },
                modifier = Modifier.size(140.dp).clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentPalette.onPageStart,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        releaseDate.ifBlank { null }?.let { strings.albumReleaseDate(it) },
                        company.ifBlank { null },
                        strings.songCount(trackCount),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnPageStart,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        // Each artist opens their page.
        if (artists.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                artists.filter { it.id != 0L }.forEach { artist ->
                    AssistChip(
                        onClick = { onOpenArtist(artist.id) },
                        label = { Text(artist.name) },
                        leadingIcon = { Icon(AppIcons.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = accentPalette.quietContainer,
                            labelColor = accentPalette.onQuietContainer,
                            leadingIconContentColor = accentPalette.onQuietContainer,
                        ),
                        border = null,
                    )
                }
            }
        }
        if (description.isNotBlank()) {
            Surface(
                onClick = { expanded = !expanded },
                color = accentPalette.quietContainer,
                contentColor = accentPalette.onQuietContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(
                    text = description,
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
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            ) {
                Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.playAll)
            }
        }
    }
}
