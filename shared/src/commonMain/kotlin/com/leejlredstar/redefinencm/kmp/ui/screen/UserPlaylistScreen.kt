package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

@Composable
fun UserPlaylistScreen(
    scaffoldPadding: PaddingValues,
    onOpenLogin: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: MainViewModel = koinInject(),
) {
    val userDetail by viewModel.userDetail.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = scaffoldPadding.calculateBottomPadding()),
    ) {
        item {
            UserPlaylistHero(
                backgroundUrl = userDetail?.profile?.backgroundUrl,
                avatarUrl = userDetail?.profile?.avatarUrl,
                nickname = userDetail?.profile?.nickname ?: "Unknown User",
                userId = userDetail?.profile?.userId?.toString() ?: "N/A",
            )
        }
        item {
            ExpressiveSectionTitle(
                text = "我的歌单",
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 12.dp),
            )
        }
        if (userDetail == null) {
            item {
                EmptyHint(
                    text = "登录后查看歌单",
                    actionLabel = "去登录",
                    onAction = onOpenLogin,
                )
            }
        } else {
            itemsIndexed(playlists) { index, pl ->
                PlaylistCard(
                    userPlaylistEach = pl,
                    specialCard = when {
                        pl.name.contains("喜欢的音乐") -> "fav"
                        pl.name.contains("私人雷达") -> "radar"
                        else -> "no"
                    },
                    index = index,
                    count = playlists.size,
                    onClick = { onOpenPlaylist(pl.id) },
                )
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun UserPlaylistHero(
    backgroundUrl: String?,
    avatarUrl: String?,
    nickname: String,
    userId: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        AsyncImage(
            model = backgroundUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                        ),
                    )
                },
            contentScale = ContentScale.Crop,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = nickname,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "ID: $userId",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}
