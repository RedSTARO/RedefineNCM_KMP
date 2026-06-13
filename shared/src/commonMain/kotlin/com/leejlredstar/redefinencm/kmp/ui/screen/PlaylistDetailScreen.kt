package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

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

    val trackCountText = when {
        (playlist?.trackCount ?: 0L) == 0L -> songs.size.toString()
        else -> playlist?.trackCount?.toString() ?: "…"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        item {
            PlaylistHeader(
                coverUrl = playlist?.coverImgUrl,
                title = playlist?.name ?: "加载中…",
                trackCountText = trackCountText,
                onBack = onBack,
                onPlayAll = { playFrom(0) },
            )
        }
        itemsIndexed(songs) { i, song ->
            SongRow(
                index = i,
                title = song.name,
                artist = song.ar.joinToString(" / ") { it.name },
                artworkUri = song.al.picUrl,
                shape = connectedListItemShape(i, songs.size),
                onClick = { playFrom(i) },
                songId = song.id,
            )
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun PlaylistHeader(
    coverUrl: String?,
    title: String,
    trackCountText: String,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
) {
    val heroColor = MaterialTheme.colorScheme.primaryContainer

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            heroColor,
                            heroColor.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }

            AsyncImage(
                model = coverUrl,
                contentDescription = "Playlist Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 8.dp)
                    .size(200.dp)
                    .clip(RoundedCornerShape(36.dp)),
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$trackCountText 首歌曲",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = CircleShape,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("播放全部", style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalIconButton(
                    onClick = {},
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "下载全部")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
