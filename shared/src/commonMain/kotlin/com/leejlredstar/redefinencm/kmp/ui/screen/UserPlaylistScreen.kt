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
import androidx.compose.material3.LinearProgressIndicator
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
import com.leejlredstar.redefinencm.kmp.data.PlaybackReportEndpoint
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserLevelResponse
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingKind
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingPhase
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingState
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingStatus
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveCacheHint
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt

internal data class UserLevelDisplay(
    val summary: String,
    val nextLevelLabel: String?,
    val progressLabel: String,
    val progress: Float,
)

internal data class PlaybackSyncDisplay(
    val title: String,
    val message: String,
    val isError: Boolean,
)

internal fun playbackSyncDisplay(state: PlaybackReportingState): PlaybackSyncDisplay? {
    val primary = listOfNotNull(state.scrobble, state.relay)
        .maxWithOrNull(
            compareBy<PlaybackReportingStatus> { it.reportingGeneration }
                .thenBy { if (it.kind == PlaybackReportingKind.SCROBBLE) 1 else 0 },
        )
        ?: return null
    val relaySuffix = state.relay
        ?.takeIf {
            primary.kind == PlaybackReportingKind.SCROBBLE &&
                it.reportingGeneration == primary.reportingGeneration
        }
        ?.let { relay -> "\n${relaySummary(relay)}" }
        .orEmpty()
    return PlaybackSyncDisplay(
        title = if (primary.kind == PlaybackReportingKind.SCROBBLE) {
            "播放记录同步"
        } else {
            "跨端进度提交"
        },
        message = statusSummary(primary) + relaySuffix,
        isError = primary.phase in setOf(
            PlaybackReportingPhase.REJECTED,
            PlaybackReportingPhase.UNSUPPORTED,
            PlaybackReportingPhase.TRANSPORT_FAILURE,
            PlaybackReportingPhase.NOT_REFLECTED,
        ),
    )
}

private fun statusSummary(status: PlaybackReportingStatus): String {
    val endpoint = when (status.endpoint) {
        PlaybackReportEndpoint.SCROBBLE_V1 -> "NCBL 上报"
        PlaybackReportEndpoint.WEBLOG -> "兼容上报"
        PlaybackReportEndpoint.RELAY -> "relay"
        null -> "上报"
    }
    val diagnostic = buildList {
        status.httpStatus?.let { add("HTTP $it") }
        status.serverCode?.let { add("code $it") }
    }.takeIf { it.isNotEmpty() }?.joinToString(prefix = "（", postfix = "）").orEmpty()
    return when (status.phase) {
        PlaybackReportingPhase.SENDING -> "$endpoint 正在发送。"
        PlaybackReportingPhase.ACCEPTED -> "$endpoint 已被服务器接收$diagnostic。"
        PlaybackReportingPhase.VERIFYING -> "$endpoint 已接收，正在回读账号记录与等级。"
        PlaybackReportingPhase.ACCOUNT_VERIFIED -> {
            val comparison = status.accountComparison
            val changed = buildList {
                if (
                    comparison?.recordAppeared == true ||
                    (comparison?.recordPlayCountDelta ?: 0L) > 0L
                ) {
                    add("周听歌记录")
                }
                if (
                    comparison?.recentBecameLatest == true ||
                    comparison?.recentPlayTimeAdvanced == true
                ) {
                    add("最近播放")
                }
                if ((comparison?.levelPlayCountDelta ?: 0L) > 0L) add("等级听歌数")
            }.ifEmpty { listOf("账号记录") }.joinToString("、")
            "$endpoint 已由账号侧确认：$changed 已更新。"
        }
        PlaybackReportingPhase.NOT_REFLECTED ->
            "$endpoint 返回成功，但回读窗口内没有确认到账号记录变化$diagnostic。"
        PlaybackReportingPhase.REJECTED -> statusMessage(
            status.message ?: "$endpoint 被服务器拒绝",
            diagnostic,
        )
        PlaybackReportingPhase.UNSUPPORTED -> statusMessage(
            status.message ?: "当前服务器不支持 $endpoint",
            diagnostic,
        )
        PlaybackReportingPhase.TRANSPORT_FAILURE -> statusMessage(
            status.message ?: "$endpoint 请求失败",
            diagnostic,
        )
        PlaybackReportingPhase.ACCOUNT_CHANGED -> "账号已切换，本次结果已丢弃。"
    }
}

private fun statusMessage(message: String, diagnostic: String): String =
    "${message.trimEnd('。')}$diagnostic。"

private fun relaySummary(status: PlaybackReportingStatus): String = when (status.phase) {
    PlaybackReportingPhase.ACCEPTED -> "跨端进度：服务器已接收。"
    PlaybackReportingPhase.UNSUPPORTED -> "跨端进度：当前服务器未部署 relay。"
    PlaybackReportingPhase.REJECTED -> "跨端进度：服务器拒绝。"
    PlaybackReportingPhase.TRANSPORT_FAILURE -> "跨端进度：请求失败。"
    PlaybackReportingPhase.ACCOUNT_CHANGED -> "跨端进度：因账号切换已丢弃。"
    else -> "跨端进度：${statusSummary(status)}"
}

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
    val playbackReportingState by viewModel.playbackReportingState.collectAsState()
    val uid by viewModel.uid.collectAsState()
    var lastIntelligencePlaylistId by remember { mutableStateOf<Long?>(null) }
    val hasCachedContent =
        (userDetailFromCache && userDetail != null) ||
            (userLevelFromCache && userLevel?.data != null) ||
            userPlaylistsFromCache
    val hasAccountContent = userDetail != null || playlistsLoaded
    val playbackSyncStatusDisplay = playbackSyncDisplay(playbackReportingState)
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
            playbackSyncStatusDisplay?.let { display ->
                item(key = "playback-sync-status") {
                    ExpressiveStatePanel(
                        title = display.title,
                        message = display.message,
                        tone = if (display.isError) {
                            ExpressiveStateTone.Error
                        } else {
                            ExpressiveStateTone.Neutral
                        },
                        accentPalette = accentPalette,
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
                                count = playlists.size,
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
                        text = levelDisplay?.summary ?: if (levelLoading) {
                            "正在加载等级信息…"
                        } else {
                            "等级信息暂不可用"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = accentPalette.secondaryOnQuietContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (levelLoadFailed) {
                        TextButton(onClick = onRetryLevel) {
                            Text("重试")
                        }
                    }
                    levelDisplay?.let { display ->
                        Spacer(Modifier.height(4.dp))
                        display.nextLevelLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentPalette.secondaryOnQuietContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            text = display.progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentPalette.secondaryOnQuietContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { display.progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = accentPalette.accent,
                            trackColor = accentPalette.onQuietContainer.copy(alpha = 0.14f),
                        )
                    }
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
