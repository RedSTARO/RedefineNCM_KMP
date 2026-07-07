package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.util.BackHandler
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

/** 共享元素 key（原版 SharedKeys）。 */
object SharedKeys {
    fun search() = "search-bar"
}

/**
 * 推荐主页（原版 RecommendPage）：搜索药丸 → 搜索页 的共享元素过渡由
 * SharedTransitionLayout + AnimatedVisibility + sharedBounds 实现。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    scaffoldPadding: PaddingValues,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
) {
    val recommend by viewModel.recommendSongs.collectAsState()
    val recommendResource by viewModel.recommendResource.collectAsState()
    val dailySongs = recommend?.data?.dailySongs ?: emptyList()
    val resources = recommendResource?.recommend ?: emptyList()
    var showSearch by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showSearch) { showSearch = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = scaffoldPadding.calculateBottomPadding()),
    ) {
        SharedTransitionLayout {
            val sharedTransitionScope = this

            AnimatedVisibility(
                visible = !showSearch,
                enter = fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                    scaleIn(
                        initialScale = 0.985f,
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                    ),
                exit = fadeOut(animationSpec = tween(120, easing = LinearOutSlowInEasing)) +
                    scaleOut(
                        targetScale = 0.985f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                    ),
            ) {
                val animatedVisibilityScope = this
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                ) {
                    item {
                        SearchBox(
                            onClick = { showSearch = true },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
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
                                        // 原版 onPlaySingleSongClick：单曲独立队列
                                        player.setQueue(listOf(song.toMediaInfo()), 0)
                                    },
                                )
                            },
                        )
                    }

                    item { Spacer(Modifier.height(96.dp)) }
                }
            }

            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn(
                    animationSpec = tween(180, delayMillis = 80, easing = LinearOutSlowInEasing),
                ) + slideInVertically(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 10 },
                ),
                exit = fadeOut(animationSpec = tween(120, easing = LinearOutSlowInEasing)) +
                    slideOutVertically(
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        targetOffsetY = { it / 12 },
                    ),
            ) {
                SearchScreen(
                    onBack = { showSearch = false },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                )
            }
        }
    }
}

/** 搜索入口药丸（原版 SearchBox），与搜索页输入框共享 sharedBounds。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchBox(
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedTransitionScope) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .sharedBounds(
                    rememberSharedContentState(SharedKeys.search()),
                    animatedVisibilityScope,
                )
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcons.Search,
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
}
