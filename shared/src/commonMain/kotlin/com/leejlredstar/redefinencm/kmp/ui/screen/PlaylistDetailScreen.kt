package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

/** Playlist detail / 歌单 (M3 Expressive): cover+meta header, pill "play all", connected song rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
) {
    val detail by viewModel.playlistDetail.collectAsState()
    val tracks by viewModel.playlistSongs.collectAsState()
    val songs = tracks?.songs ?: emptyList()
    val playlist = detail?.playlist

    LaunchedEffect(playlistId) {
        viewModel.fetchPlaylistDetail(playlistId)
    }

    fun playFrom(index: Int) {
        if (songs.isEmpty()) return
        player.setQueue(songs.map { it.toMediaInfo() }, index)
        player.play()
        onOpenNowPlaying()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(playlist?.name ?: "歌单", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    AsyncImage(
                        model = playlist?.coverImgUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(120.dp).clip(MaterialTheme.shapes.extraLarge),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = playlist?.name ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${playlist?.trackCount ?: songs.size} 首 · ${playlist?.creator?.nickname ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = { playFrom(0) },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("播放全部")
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(songs) { i, song ->
                SongRow(
                    index = i,
                    title = song.name,
                    artist = song.ar.joinToString(", ") { it.name },
                    artworkUri = song.al.picUrl,
                    shape = connectedListItemShape(i, songs.size),
                    onClick = { playFrom(i) },
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}
