package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
) {
    val detail by viewModel.playlistDetail.collectAsState()
    val tracks by viewModel.playlistSongs.collectAsState()
    val loading by viewModel.playlistLoading.collectAsState()
    val loadError by viewModel.playlistLoadError.collectAsState()
    val detailLoadError by viewModel.playlistDetailLoadError.collectAsState()
    val playlist = detail?.playlist?.takeIf { it.id == playlistId }
    val songs = tracks?.songs.orEmpty()
    // 原版 ShowPlaylistDetailPage：点单曲时按 replacePlaylist 设置决定替换整单还是单曲队列
    val replacePlaylist = remember { settings.getBoolean(SettingKeys.REPLACE_PLAYLIST, false) }

    LaunchedEffect(playlistId) {
        viewModel.fetchPlaylistDetail(playlistId)
    }

    // 播放后停留在歌单页（与原版一致，靠迷你播放条进入全屏播放器）
    fun playAll() {
        if (songs.isEmpty()) return
        player.setQueue(songs.map { it.toMediaInfo() }, 0)
        viewModel.updatePlaylistPlaycount(playlistId)
    }

    fun playSong(index: Int) {
        val song = songs.getOrNull(index) ?: return
        if (replacePlaylist) {
            player.setQueue(songs.map { it.toMediaInfo() }, index)
        } else {
            // 原版 onPlaySingleSongClick：单曲独立队列
            player.setQueue(listOf(song.toMediaInfo()), 0)
        }
        viewModel.updatePlaylistPlaycount(playlistId)
    }

    val trackCountText = when {
        (playlist?.trackCount ?: 0L) == 0L -> songs.size.toString()
        else -> playlist?.trackCount?.toString() ?: "…"
    }
    val defaultAccentColor = MaterialTheme.colorScheme.primaryContainer
    var rawAccentColor by remember(playlist?.coverImgUrl, defaultAccentColor) {
        mutableStateOf(defaultAccentColor)
    }
    val animatedAccentColor by animateColorAsState(
        targetValue = rawAccentColor,
        animationSpec = spring(),
        label = "playlistAccent",
    )
    val accentPalette = contentAccentPalette(animatedAccentColor)

    ExpressivePage(
        accentPalette = accentPalette,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "playlist-header") {
                PlaylistHeader(
                    coverUrl = playlist?.coverImgUrl,
                    title = playlist?.name ?: "歌单",
                    trackCountText = trackCountText,
                    actionsEnabled = songs.isNotEmpty(),
                    accentPalette = accentPalette,
                    onAccentColor = { rawAccentColor = it },
                    onBack = onBack,
                    onPlayAll = { playAll() },
                    onDownloadAll = {
                        viewModel.onDownloadPlaylistClick(playlistId)
                    },
                )
            }
            if (!loading && loadError == null && detailLoadError != null) {
                item(key = "playlist-detail-error") {
                    ExpressiveStatePanel(
                        title = "歌单资料暂不可用",
                        message = detailLoadError.orEmpty(),
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = accentPalette,
                        actionLabel = "重试",
                        onAction = { viewModel.fetchPlaylistDetail(playlistId) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            when {
                loading -> item(key = "playlist-loading") {
                    ExpressiveLoadingState(
                        label = "正在加载歌单与歌曲…",
                        accentColor = accentPalette.accent,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                loadError != null -> item(key = "playlist-error") {
                    ExpressiveStatePanel(
                        title = "歌单加载失败",
                        message = loadError.orEmpty(),
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = accentPalette,
                        actionLabel = "重试",
                        onAction = { viewModel.fetchPlaylistDetail(playlistId) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                songs.isEmpty() -> item(key = "playlist-empty") {
                    ExpressiveStatePanel(
                        title = "歌单里还没有歌曲",
                        message = "添加歌曲后，它们会显示在这里。",
                        icon = AppIcons.QueueMusic,
                        accentPalette = accentPalette,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                else -> itemsIndexed(
                    items = songs,
                    key = { _, song -> song.id },
                ) { i, song ->
                    SongRow(
                        index = i,
                        title = song.name,
                        artist = song.ar.joinToString(" / ") { it.name },
                        artworkUri = song.al.picUrl,
                        shape = connectedListItemShape(i, songs.size),
                        onClick = { playSong(i) },
                        songId = song.id,
                        accentColor = animatedAccentColor,
                    )
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun PlaylistHeader(
    coverUrl: String?,
    title: String,
    trackCountText: String,
    actionsEnabled: Boolean,
    accentPalette: ContentAccentPalette,
    onAccentColor: (Color) -> Unit,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    val fallbackAccentColor = MaterialTheme.colorScheme.primaryContainer
    val extractAccent = rememberThemeColorExtractor(
        requestKey = coverUrl,
        onAccentColor = onAccentColor,
    )
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
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
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = accentPalette.quietContainer.copy(alpha = 0.72f),
                    contentColor = accentPalette.onQuietContainer,
                ) {
                    Icon(
                        AppIcons.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            AsyncImage(
                model = coverUrl,
                        contentDescription = "歌单封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 8.dp)
                    .size(200.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
                onSuccess = { state -> extractAccent(state.result.image) },
                onError = { onAccentColor(fallbackAccentColor) },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accentPalette.onQuietContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$trackCountText 首歌曲",
                style = MaterialTheme.typography.labelLarge,
                        color = accentPalette.secondaryOnQuietContainer,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPlayAll,
                    enabled = actionsEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = CircleShape,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentPalette.accent,
                        contentColor = accentPalette.onAccent,
                    ),
                ) {
                    Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("播放全部", style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalIconButton(
                    onClick = onDownloadAll,
                    enabled = actionsEnabled,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container,
                        contentColor = accentPalette.onContainer,
                    ),
                ) {
                    Icon(AppIcons.Download, contentDescription = "下载全部")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
