package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveCacheHint
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

@Composable
fun UserPlaylistScreen(
    scaffoldPadding: PaddingValues,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: MainViewModel = koinInject(),
) {
    val userDetail by viewModel.userDetail.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    val playlistsLoaded by viewModel.userPlaylistsLoaded.collectAsState()
    val accountLoading by viewModel.accountLoading.collectAsState()
    val accountLoadError by viewModel.accountLoadError.collectAsState()
    val userDetailLoadError by viewModel.userDetailLoadError.collectAsState()
    val userPlaylistsLoadError by viewModel.userPlaylistsLoadError.collectAsState()
    val userDetailFromCache by viewModel.userDetailFromCache.collectAsState()
    val userPlaylistsFromCache by viewModel.userPlaylistsFromCache.collectAsState()
    val uid by viewModel.uid.collectAsState()
    val hasCachedContent =
        (userDetailFromCache && userDetail != null) ||
            userPlaylistsFromCache
    val hasAccountContent = userDetail != null || playlistsLoaded
    val defaultAccentColor = MaterialTheme.colorScheme.primaryContainer
    var rawAccentColor by remember(
        userDetail?.profile?.backgroundUrl,
        userDetail?.profile?.avatarUrl,
        defaultAccentColor,
    ) {
        mutableStateOf(defaultAccentColor)
    }
    val animatedAccentColor by animateColorAsState(
        targetValue = rawAccentColor,
        animationSpec = spring(),
        label = "userAccent",
    )
    val accentPalette = contentAccentPalette(animatedAccentColor)

    ExpressivePage(
        accentPalette = accentPalette,
        contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding()),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            userDetail?.let { detail ->
                item(key = "user-hero") {
                    UserPlaylistHero(
                        backgroundUrl = detail.profile.backgroundUrl,
                        avatarUrl = detail.profile.avatarUrl,
                        nickname = detail.profile.nickname,
                        userId = detail.profile.userId.toString(),
                        accentPalette = accentPalette,
                        onAccentColor = { rawAccentColor = it },
                    )
                }
            }
            if (hasCachedContent) {
                item(key = "account-cache-hint") {
                    ExpressiveCacheHint(
                        isRefreshing = accountLoading,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            when {
                (accountLoadError != null || userDetailLoadError != null) && !hasAccountContent -> item(
                    key = "account-error",
                ) {
                    ExpressiveStatePanel(
                        title = "账号数据加载失败",
                        message = accountLoadError ?: userDetailLoadError.orEmpty(),
                        icon = com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = accentPalette,
                        actionLabel = "重试",
                        onAction = viewModel::retryAccountData,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                accountLoading && !hasAccountContent -> item(key = "account-loading") {
                    ExpressiveLoadingState(
                        label = "正在加载账号与歌单…",
                        accentColor = accentPalette.accent,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                uid == 0L -> item(key = "login-hint") {
                    LoginMovedHint(accentPalette)
                }
                !hasAccountContent -> item(key = "profile-unavailable") {
                    ExpressiveStatePanel(
                        title = "用户资料暂不可用",
                        message = "账号已登录，但用户资料未能加载。",
                        icon = com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = accentPalette,
                        actionLabel = "重试",
                        onAction = viewModel::retryAccountData,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                else -> {
                    item(key = "playlist-heading") {
                        ExpressiveSectionTitle(
                            text = "我的歌单",
                            supportingText = "收藏与创建的歌单",
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
                        )
                    }
                    if (userPlaylistsLoadError != null && !playlistsLoaded) {
                        item(key = "playlist-error") {
                            ExpressiveStatePanel(
                                title = "歌单加载失败",
                                message = userPlaylistsLoadError.orEmpty(),
                                icon = com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons.Refresh,
                                tone = ExpressiveStateTone.Error,
                                accentPalette = accentPalette,
                                actionLabel = "重试",
                                onAction = viewModel::retryAccountData,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else if (accountLoading && !playlistsLoaded) {
                        item(key = "playlist-loading") {
                            ExpressiveLoadingState(
                                label = "正在加载我的歌单…",
                                accentColor = accentPalette.accent,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else if (playlists.isEmpty()) {
                        item(key = "playlist-empty") {
                            ExpressiveStatePanel(
                                title = "还没有歌单",
                                message = "登录后的收藏与创建歌单会显示在这里。",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                accentPalette = accentPalette,
                            )
                        }
                    } else {
                        itemsIndexed(
                            items = playlists,
                            key = { _, playlist -> playlist.id },
                        ) { index, pl ->
                            PlaylistCard(
                                userPlaylistEach = pl,
                                specialCard = when {
                                    pl.name.contains("喜欢的音乐") -> "fav"
                                    pl.name.contains("私人雷达") -> "radar"
                                    else -> "no"
                                },
                                index = index,
                                count = playlists.size,
                                accentColor = animatedAccentColor,
                                onClick = { onOpenPlaylist(pl.id) },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun UserPlaylistHero(
    backgroundUrl: String?,
    avatarUrl: String?,
    nickname: String,
    userId: String,
    accentPalette: ContentAccentPalette,
    onAccentColor: (Color) -> Unit,
) {
    var backgroundAccent by remember(backgroundUrl) { mutableStateOf<Color?>(null) }
    var avatarAccent by remember(avatarUrl) { mutableStateOf<Color?>(null) }
    val extractBackgroundAccent = rememberThemeColorExtractor(backgroundUrl) { backgroundAccent = it }
    val extractAvatarAccent = rememberThemeColorExtractor(avatarUrl) { avatarAccent = it }
    LaunchedEffect(backgroundAccent, avatarAccent) {
        (backgroundAccent ?: avatarAccent)?.let(onAccentColor)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentPalette.pageStart,
                        accentPalette.pageMiddle,
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        AsyncImage(
            model = backgroundUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(3.dp)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentPalette.pageStart.copy(alpha = 0.78f),
                                accentPalette.pageMiddle.copy(alpha = 0.38f),
                                Color.Transparent,
                            ),
                        ),
                    )
                },
            contentScale = ContentScale.Crop,
            onSuccess = { state -> extractBackgroundAccent(state.result.image) },
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
                    .border(4.dp, accentPalette.container, CircleShape),
                onSuccess = { state -> extractAvatarAccent(state.result.image) },
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = accentPalette.quietContainer.copy(alpha = 0.86f),
                contentColor = accentPalette.onQuietContainer,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = nickname,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "ID: $userId",
                        style = MaterialTheme.typography.labelLarge,
                        color = accentPalette.secondaryOnQuietContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginMovedHint(accentPalette: ContentAccentPalette) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(
            text = "请在设置页登录后查看歌单",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        )
    }
}
