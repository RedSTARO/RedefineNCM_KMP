package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveMotion
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
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
    onOpenMy: () -> Unit,
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
) {
    val recommend by viewModel.recommendSongs.collectAsState()
    val recommendResource by viewModel.recommendResource.collectAsState()
    val accountLoading by viewModel.accountLoading.collectAsState()
    val resourceLoadError by viewModel.recommendResourceLoadError.collectAsState()
    val songsLoadError by viewModel.recommendSongsLoadError.collectAsState()
    val userDetail by viewModel.userDetail.collectAsState()
    val dailySongs = recommend?.data?.dailySongs ?: emptyList()
    val resources = recommendResource?.recommend ?: emptyList()
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val defaultPageAccent = MaterialTheme.colorScheme.primaryContainer
    val avatarUrl = userDetail?.profile?.avatarUrl
    val nickname = userDetail?.profile?.nickname ?: "我的"
    val pageAccentSource = resources.firstOrNull()?.picUrl
        ?: dailySongs.firstOrNull()?.al?.picUrl
        ?: avatarUrl
    var rawPageAccent by remember(pageAccentSource, defaultPageAccent) {
        mutableStateOf(defaultPageAccent)
    }
    val pageAccent by animateColorAsState(
        targetValue = rawPageAccent,
        animationSpec = spring(),
        label = "homePageAccent",
    )
    val pagePalette = contentAccentPalette(pageAccent)

    BackHandler(enabled = showSearch) { showSearch = false }

    ExpressivePage(
        accentPalette = pagePalette,
        contentWindowInsets = WindowInsets.statusBars,
        contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding()),
    ) {
        SharedTransitionLayout {
            val sharedTransitionScope = this

            AnimatedVisibility(
                visible = !showSearch,
                enter = fadeIn(
                    animationSpec = tween(ExpressiveMotion.ShortMillis, easing = LinearOutSlowInEasing),
                ) +
                    scaleIn(
                        initialScale = 0.985f,
                        animationSpec = tween(ExpressiveMotion.MediumMillis, easing = FastOutSlowInEasing),
                    ),
                exit = fadeOut(
                    animationSpec = tween(ExpressiveMotion.FastMillis, easing = LinearOutSlowInEasing),
                ) +
                    scaleOut(
                        targetScale = 0.985f,
                        animationSpec = tween(ExpressiveMotion.ShortMillis, easing = FastOutSlowInEasing),
                    ),
            ) {
                val animatedVisibilityScope = this
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                ) {
                    item {
                        HomeHero(
                            dailySongCount = dailySongs.size,
                            playlistCount = resources.size,
                            accentColor = pageAccent,
                            avatarUrl = avatarUrl,
                            nickname = nickname,
                            onOpenMy = onOpenMy,
                            onAccentColor = if (pageAccentSource == avatarUrl) {
                                { color -> rawPageAccent = color }
                            } else {
                                null
                            },
                            onClick = { showSearch = true },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }

                    item {
                        SectionWithLazyRow(
                            title = "推荐歌单",
                            items = resources,
                            isLoading = accountLoading && recommendResource == null,
                            errorMessage = resourceLoadError,
                            onRetry = viewModel::retryAccountData,
                            key = { resource -> resource.id },
                            itemContent = { res ->
                                RecommendSquareCard(
                                    picUrl = res.picUrl,
                                    text = res.name,
                                    onAccentColor = if (res.picUrl == pageAccentSource) {
                                        { color -> rawPageAccent = color }
                                    } else {
                                        null
                                    },
                                    onClick = { onOpenPlaylist(res.id) },
                                )
                            },
                        )
                    }

                    item {
                        SectionWithLazyRow(
                            title = "每日推荐",
                            items = dailySongs,
                            isLoading = accountLoading && recommend == null,
                            errorMessage = songsLoadError,
                            onRetry = viewModel::retryAccountData,
                            key = { song -> song.id },
                            itemContent = { song ->
                                RecommendSquareCard(
                                    picUrl = song.al.picUrl,
                                    text = song.name,
                                    onAccentColor = if (song.al.picUrl == pageAccentSource) {
                                        { color -> rawPageAccent = color }
                                    } else {
                                        null
                                    },
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
                    animationSpec = tween(
                        ExpressiveMotion.ShortMillis,
                        delayMillis = ExpressiveMotion.EnterDelayMillis,
                        easing = LinearOutSlowInEasing,
                    ),
                ) + slideInVertically(
                    animationSpec = tween(ExpressiveMotion.EmphasizedMillis, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 10 },
                ),
                exit = fadeOut(
                    animationSpec = tween(ExpressiveMotion.FastMillis, easing = LinearOutSlowInEasing),
                ) +
                    slideOutVertically(
                        animationSpec = tween(ExpressiveMotion.ShortMillis, easing = FastOutSlowInEasing),
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeHero(
    dailySongCount: Int,
    playlistCount: Int,
    accentColor: Color,
    avatarUrl: String?,
    nickname: String,
    onOpenMy: () -> Unit,
    onAccentColor: ((Color) -> Unit)?,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val heroPalette = contentAccentPalette(accentColor)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = heroPalette.quietContainer,
        contentColor = heroPalette.onQuietContainer,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            heroPalette.pageStart,
                            heroPalette.container,
                            heroPalette.quietContainer,
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            BoxWithConstraints {
                val compactHeader = maxWidth < 300.dp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                    Text(
                        text = "RedefineNCM",
                        style = if (compactHeader) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.displaySmall
                        },
                        fontWeight = FontWeight.ExtraBold,
                        color = heroPalette.onPageStart,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "推荐 · $dailySongCount 首每日歌曲 · $playlistCount 张歌单",
                        style = MaterialTheme.typography.titleMedium,
                        color = heroPalette.secondaryOnPageStart,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    }
                    Spacer(Modifier.size(if (compactHeader) 8.dp else 16.dp))
                    HomeAccountAvatar(
                        avatarUrl = avatarUrl,
                        nickname = nickname,
                        accentColor = accentColor,
                        onOpenMy = onOpenMy,
                        onAccentColor = onAccentColor,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            SearchBox(
                onClick = onClick,
                accentColor = accentColor,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

@Composable
private fun HomeAccountAvatar(
    avatarUrl: String?,
    nickname: String,
    accentColor: Color,
    onOpenMy: () -> Unit,
    onAccentColor: ((Color) -> Unit)?,
) {
    val avatarPalette = contentAccentPalette(accentColor)
    val extractAccent = rememberThemeColorExtractor(
        requestKey = avatarUrl,
        onAccentColor = onAccentColor ?: {},
    )
    Surface(
        onClick = onOpenMy,
        shape = CircleShape,
        color = avatarPalette.container,
        contentColor = avatarPalette.onContainer,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = "打开${nickname.ifBlank { "我的" }}的个人页" },
    ) {
        if (avatarUrl.isNullOrBlank()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = AppIcons.Person,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
        } else {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    if (onAccentColor != null) extractAccent(state.result.image)
                },
            )
        }
    }
}

/** 搜索入口药丸（原版 SearchBox），与搜索页输入框共享 sharedBounds。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SearchBox(
    onClick: () -> Unit,
    accentColor: Color,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val searchPalette = contentAccentPalette(accentColor)
    with(sharedTransitionScope) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = searchPalette.quietContainer,
            contentColor = searchPalette.onQuietContainer,
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
                    tint = searchPalette.accent,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "搜索歌曲、歌单...",
                    style = MaterialTheme.typography.bodyLarge,
                color = searchPalette.secondaryOnQuietContainer,
                )
            }
        }
    }
}
