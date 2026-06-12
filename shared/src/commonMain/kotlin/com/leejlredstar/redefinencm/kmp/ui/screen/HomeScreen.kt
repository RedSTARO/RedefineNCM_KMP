package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

/**
 * Home / 主页 — the app entry point (M3 Expressive). Shows the logged-in user's playlists and the
 * daily-recommend songs; tapping a song plays it and opens Now Playing. A mini-player FAB appears
 * once something is playing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLogin: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
) {
    val recommend by viewModel.recommendSongs.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    val currentMedia by player.currentMedia.collectAsState()
    val dailySongs = recommend?.data?.dailySongs ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RedefineNCM", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = onOpenLogin) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "登录")
                    }
                },
            )
        },
        floatingActionButton = {
            val media = currentMedia
            if (media != null) {
                ExtendedFloatingActionButton(
                    onClick = onOpenNowPlaying,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = {
                        Text(media.title.ifBlank { "正在播放" }, maxLines = 1)
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding),
        ) {
            if (playlists.isNotEmpty()) {
                item {
                    ExpressiveSectionTitle(
                        "我的歌单",
                        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                itemsIndexed(playlists) { i, pl ->
                    PlaylistRow(
                        name = pl.name,
                        coverUrl = pl.coverImgUrl,
                        trackCount = pl.trackCount,
                        shape = connectedListItemShape(i, playlists.size),
                        onClick = { onOpenPlaylist(pl.id) },
                    )
                }
            }

            item {
                ExpressiveSectionTitle(
                    "每日推荐",
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            if (dailySongs.isEmpty()) {
                item {
                    EmptyHint(
                        text = "登录后查看每日推荐",
                        actionLabel = "去登录",
                        onAction = onOpenLogin,
                    )
                }
            }
            itemsIndexed(dailySongs) { i, song ->
                SongRow(
                    index = i,
                    title = song.name,
                    artist = song.ar.joinToString(", ") { it.name },
                    artworkUri = song.al.picUrl,
                    shape = connectedListItemShape(i, dailySongs.size),
                    onClick = {
                        player.setQueue(dailySongs.map { it.toMediaInfo() }, i)
                        player.play()
                        onOpenNowPlaying()
                    },
                )
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}
