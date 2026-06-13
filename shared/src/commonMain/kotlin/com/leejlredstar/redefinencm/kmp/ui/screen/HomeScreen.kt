package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    scaffoldPadding: PaddingValues,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
) {
    val recommend by viewModel.recommendSongs.collectAsState()
    val recommendResource by viewModel.recommendResource.collectAsState()
    val dailySongs = recommend?.data?.dailySongs ?: emptyList()
    val resources = recommendResource?.recommend ?: emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = scaffoldPadding.calculateBottomPadding()),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
    ) {
        item {
            Surface(
                onClick = onOpenSearch,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "搜索歌曲、歌单...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionWithLazyRow(
                title = "推荐歌单",
                items = resources,
                itemContent = { res ->
                    RecommendSquareCard(
                        picUrl = res.picUrl,
                        text = res.name,
                        onClick = { onOpenPlaylist(res.id) },
                    )
                },
            )
        }

        item {
            SectionWithLazyRow(
                title = "每日推荐",
                items = dailySongs,
                itemContent = { song ->
                    RecommendSquareCard(
                        picUrl = song.al.picUrl,
                        text = song.name,
                        onClick = {
                            player.setQueue(listOf(song.toMediaInfo()), 0)
                            player.play()
                        },
                    )
                },
            )
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}
