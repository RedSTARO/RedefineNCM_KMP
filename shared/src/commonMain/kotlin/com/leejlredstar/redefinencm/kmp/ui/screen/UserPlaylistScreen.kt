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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserLevelResponse
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveWavyProgress
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveCacheHint
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.AccentColorSaver
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import org.koin.compose.koinInject
import kotlin.math.roundToInt

internal data class UserLevelDisplay(
    val summary: String,
    val nextLevelLabel: String?,
    val progressLabel: String,
    val progress: Float,
)

internal fun isFavoritePlaylist(
    specialType: Int,
    name: String,
    creatorUserId: Long,
    currentUserId: Long,
): Boolean = currentUserId > 0L &&
    creatorUserId == currentUserId &&
    (specialType == 5 || (specialType == 0 && name.endsWith("喜欢的音乐")))

internal fun userLevelDisplay(response: UserLevelResponse?): UserLevelDisplay? {
    val data = response?.data ?: return null
    val progress = when {
        response.full -> 1f
        data.progress.isFinite() -> data.progress.coerceIn(0.0, 1.0).toFloat()
        else -> 0f
    }
    return UserLevelDisplay(
        summary = "Lv.${data.level} · 听歌 ${data.nowPlayCount} 首 · 登录 ${data.nowLoginCount} 天",
        nextLevelLabel = if (response.full) {
            null
        } else {
            buildList {
                if (data.nextPlayCount > 0) add("听歌 ${data.nextPlayCount} 首")
                if (data.nextLoginCount > 0) add("登录 ${data.nextLoginCount} 天")
            }.takeIf { it.isNotEmpty() }?.joinToString(
                separator = " · ",
                prefix = "下一级门槛：",
            )
        },
        progressLabel = if (response.full) {
            "已达到最高等级"
        } else {
            "等级进度 ${(progress * 100).roundToInt()}%"
        },
        progress = progress,
    )
}

@Composable
fun UserPlaylistScreen(
    scaffoldPadding: PaddingValues,
    onOpenPlaylist: (Long) -> Unit,
    onOpenLogin: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    viewModel: MainViewModel = koinInject(),
) {
    val userDetail by viewModel.userDetail.collectAsState()
    val userLevel by viewModel.userLevel.collectAsState()
    val playlists by viewModel.userPlaylists.collectAsState()
    val playlistsLoaded by viewModel.userPlaylistsLoaded.collectAsState()
    val accountLoading by viewModel.accountLoading.collectAsState()
    val accountLoadError by viewModel.accountLoadError.collectAsState()
    val userDetailLoadError by viewModel.userDetailLoadError.collectAsState()
    val userLevelLoadError by viewModel.userLevelLoadError.collectAsState()
    val userPlaylistsLoadError by viewModel.userPlaylistsLoadError.collectAsState()
    val intelligenceLoadingPlaylistId by viewModel.intelligenceLoadingPlaylistId.collectAsState()
    val intelligenceError by viewModel.intelligenceError.collectAsState()
    val userDetailFromCache by viewModel.userDetailFromCache.collectAsState()
    val userLevelFromCache by viewModel.userLevelFromCache.collectAsState()
    val userPlaylistsFromCache by viewModel.userPlaylistsFromCache.collectAsState()
    val uid by viewModel.uid.collectAsState()
    var lastIntelligencePlaylistId by remember { mutableStateOf<Long?>(null) }
    val hasCachedContent =
        (userDetailFromCache && userDetail != null) ||
            (userLevelFromCache && userLevel?.data != null) ||
            userPlaylistsFromCache
    val hasAccountContent = userDetail != null || playlistsLoaded
    val defaultAccentColor = MaterialTheme.colorScheme.primaryContainer
    var rawAccentColor by rememberSaveable(
        userDetail?.profile?.backgroundUrl,
        userDetail?.profile?.avatarUrl,
        defaultAccentColor,
        stateSaver = AccentColorSaver,
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
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // contentPadding, not container padding, so rows scroll under the floating toolbar.
            contentPadding = PaddingValues(bottom = scaffoldPadding.calculateBottomPadding() + 16.dp),
        ) {
            userDetail?.let { detail ->
                item(key = "user-hero") {
                    UserPlaylistHero(
                        backgroundUrl = detail.profile.backgroundUrl,
                        avatarUrl = detail.profile.avatarUrl,
                        nickname = detail.profile.nickname,
                        userLevel = userLevel,
                        levelLoading = userLevel == null && userLevelLoadError == null && accountLoading,
                        levelLoadFailed = userLevel == null && userLevelLoadError != null,
                        accentPalette = accentPalette,
                        onAccentColor = { rawAccentColor = it },
                        onRetryLevel = viewModel::retryAccountData,
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
            // Downloads live on the device, so the way to them does not depend on being signed
            // in. On phones this is the only way to them outside a download notification.
            item(key = "library-downloads") {
                LibraryShortcut(
                    title = "下载管理",
                    subtitle = "已下载的歌曲与下载进度",
                    icon = com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons.Download,
                    accentPalette = accentPalette,
                    onClick = onOpenDownloads,
                )
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
                    ExpressiveStatePanel(
                        title = "登录后查看你的歌单",
                        message = "登录网易云音乐账号，这里会显示你创建和收藏的歌单。",
                        icon = com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons.Person,
                        accentPalette = accentPalette,
                        actionLabel = "登录",
                        onAction = onOpenLogin,
                        modifier = Modifier.padding(16.dp),
                    )
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
                    if (intelligenceError != null) {
                        item(key = "intelligence-error") {
                            ExpressiveStatePanel(
                                title = "心动模式启动失败",
                                message = intelligenceError.orEmpty(),
                                icon = com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons.Favorite,
                                tone = ExpressiveStateTone.Error,
                                accentPalette = accentPalette,
                                actionLabel = lastIntelligencePlaylistId?.let { "重试" },
                                onAction = lastIntelligencePlaylistId?.let { playlistId ->
                                    { viewModel.startIntelligenceMode(playlistId) }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
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
                                message = "你创建或收藏的歌单会显示在这里。",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                accentPalette = accentPalette,
                            )
                        }
                    } else {
                        // Created and collected playlists are different things to look for, so
                        // they get a heading each instead of one interleaved list.
                        val (created, collected) = playlists.partition { it.creator.userId == uid }
                        listOf(
                            "创建的歌单" to created,
                            "收藏的歌单" to collected,
                        ).forEach { (heading, group) ->
                            if (group.isEmpty()) return@forEach
                            item(key = "playlist-heading-$heading") {
                                ExpressiveSectionTitle(
                                    text = heading,
                                    supportingText = "${group.size} 个",
                                    modifier = Modifier.padding(
                                        start = 24.dp,
                                        end = 24.dp,
                                        top = 20.dp,
                                        bottom = 12.dp,
                                    ),
                                )
                            }
                            itemsIndexed(
                                items = group,
                                key = { _, playlist -> playlist.id },
                            ) { index, pl ->
                                val isFavorite = isFavoritePlaylist(
                                    specialType = pl.specialType,
                                    name = pl.name,
                                    creatorUserId = pl.creator.userId,
                                    currentUserId = uid,
                                )
                                PlaylistCard(
                                    userPlaylistEach = pl,
                                    specialCard = when {
                                        isFavorite -> "fav"
                                        pl.name.contains("私人雷达") -> "radar"
                                        else -> "no"
                                    },
                                    index = index,
                                    count = group.size,
                                    accentColor = animatedAccentColor,
                                    onClick = { onOpenPlaylist(pl.id) },
                                    onSpecialClick = if (isFavorite) {
                                        {
                                            lastIntelligencePlaylistId = pl.id
                                            viewModel.startIntelligenceMode(pl.id)
                                        }
                                    } else {
                                        null
                                    },
                                    specialActionLoading = intelligenceLoadingPlaylistId == pl.id,
                                    showCreator = pl.creator.userId != uid,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPlaylistHero(
    backgroundUrl: String?,
    avatarUrl: String?,
    nickname: String,
    userLevel: UserLevelResponse?,
    levelLoading: Boolean,
    levelLoadFailed: Boolean,
    accentPalette: ContentAccentPalette,
    onAccentColor: (Color) -> Unit,
    onRetryLevel: () -> Unit,
) {
    val levelDisplay = userLevelDisplay(userLevel)
    var backgroundAccent by remember(backgroundUrl) { mutableStateOf<Color?>(null) }
    var avatarAccent by remember(avatarUrl) { mutableStateOf<Color?>(null) }
    val extractBackgroundAccent = rememberThemeColorExtractor(backgroundUrl) { backgroundAccent = it }
    val extractAvatarAccent = rememberThemeColorExtractor(avatarUrl) { avatarAccent = it }
    LaunchedEffect(backgroundAccent, avatarAccent) {
        (backgroundAccent ?: avatarAccent)?.let(onAccentColor)
    }
    // Avatar beside the name rather than stacked above it, and a height that follows the content:
    // the old fixed 320dp block pushed the playlists below the first screen and had no room left
    // for a larger system font.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp),
    ) {
        AsyncImage(
            model = backgroundUrl,
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .blur(3.dp)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentPalette.pageStart.copy(alpha = 0.78f),
                                accentPalette.pageMiddle.copy(alpha = 0.52f),
                                accentPalette.pageStart,
                            ),
                        ),
                    )
                },
            contentScale = ContentScale.Crop,
            onSuccess = { state -> extractBackgroundAccent(state.result.image) },
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(3.dp, accentPalette.container, CircleShape),
                onSuccess = { state -> extractAvatarAccent(state.result.image) },
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = nickname,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentPalette.onPageStart,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = levelDisplay?.summary ?: if (levelLoading) {
                        "正在加载等级信息…"
                    } else {
                        "等级信息暂不可用"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = accentPalette.secondaryOnPageStart,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (levelLoadFailed) {
                    TextButton(onClick = onRetryLevel) {
                        Text("重试")
                    }
                }
                levelDisplay?.let { display ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveWavyProgress(
                            progress = { display.progress },
                            modifier = Modifier.weight(1f),
                            color = accentPalette.accent,
                            trackColor = accentPalette.onPageStart.copy(alpha = 0.14f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = display.progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentPalette.secondaryOnPageStart,
                            maxLines = 1,
                        )
                    }
                    display.nextLevelLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentPalette.secondaryOnPageStart,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

/** A tappable row for a destination inside the library, such as the downloaded songs. */
@Composable
private fun LibraryShortcut(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentPalette: ContentAccentPalette,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accentPalette.container,
                contentColor = accentPalette.onContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(icon, contentDescription = null)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
            androidx.compose.material3.Icon(
                com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = accentPalette.secondaryOnQuietContainer,
            )
        }
    }
}
