package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.sp
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserPlaylistEach
import com.leejlredstar.redefinencm.kmp.download.DownloadTaskStatus
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveCacheHint
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveSectionTitle
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import org.koin.compose.koinInject

/** Map an API song DTO to the player's [MediaInfo] (placeholder URI resolved at play time). */
fun SongDetailSongs.toMediaInfo(
    sourceId: String = "",
): MediaInfo = MediaInfo(
    id = id.toString(),
    title = name,
    artist = ar.joinToString(", ") { it.name },
    albumTitle = al.name,
    artworkUri = al.picUrl,
    placeholderUri = "redefinencm://playbackPlaceHolder?id=$id",
    duration = dt,
    sourceId = sourceId,
)

/** Connected-list song row: index + artwork + title/artist. Reused by Home/Search/Playlist. */
@Composable
fun SongRow(
    index: Int,
    title: String,
    artist: String,
    artworkUri: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    songId: Long? = null,
    accentColor: Color? = null,
) {
    val settings = koinInject<PlatformSettings>()
    val downloadManager = koinInject<SongDownloadManager>()
    val fallbackAccent = MaterialTheme.colorScheme.primaryContainer
    var imageAccent by remember(artworkUri, accentColor, fallbackAccent) {
        mutableStateOf(accentColor ?: fallbackAccent)
    }
    val extractAccent = rememberThemeColorExtractor(artworkUri) { imageAccent = it }
    val animatedAccent by animateColorAsState(
        targetValue = accentColor ?: imageAccent,
        animationSpec = spring(),
        label = "songRowAccent",
    )
    val accentPalette = contentAccentPalette(animatedAccent)
    Surface(
        onClick = onClick,
        shape = shape,
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.5.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = accentPalette.container,
                contentColor = accentPalette.onContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            ExpressiveArtwork(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.medium,
                containerColor = accentPalette.container,
                contentColor = accentPalette.onContainer,
                onImageLoaded = { image ->
                    if (accentColor == null) {
                        extractAccent(image)
                    }
                },
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "未知歌曲" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = accentPalette.onQuietContainer,
                )
                Text(
                    text = artist.ifBlank { "未知歌手" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
            if (songId != null && settings.getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, false)) {
                val downloadedCacheVersion = DownloadedSongsCache.version.collectAsState().value
                val downloadTasks = downloadManager.tasks.collectAsState().value
                val taskStatus = remember(songId, downloadTasks) {
                    downloadTasks.firstOrNull { it.id == songId }?.status
                }
                val downloadedOnDisk = remember(songId, downloadedCacheVersion) {
                    DownloadedSongsCache.isDownloaded(songId)
                }
                val downloaded = downloadedOnDisk
                val isActive = taskStatus == DownloadTaskStatus.Queued ||
                    taskStatus == DownloadTaskStatus.Resolving ||
                    taskStatus == DownloadTaskStatus.Downloading ||
                    taskStatus == DownloadTaskStatus.SavingLyrics
                val isFailed = taskStatus == DownloadTaskStatus.Failed ||
                    taskStatus == DownloadTaskStatus.Cancelled
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = when {
                        downloaded -> accentPalette.container
                        isFailed -> MaterialTheme.colorScheme.errorContainer
                        isActive -> accentPalette.container.copy(alpha = 0.72f)
                        else -> accentPalette.onQuietContainer.copy(alpha = 0.10f)
                    },
                ) {
                    Icon(
                        imageVector = when {
                            downloaded -> AppIcons.Check
                            isFailed -> AppIcons.Clear
                            isActive -> AppIcons.Download
                            else -> AppIcons.AttachFile
                        },
                        contentDescription = when {
                            downloaded -> "已下载"
                            isFailed -> "下载失败"
                            isActive -> "正在下载"
                            else -> "未下载"
                        },
                        tint = when {
                            downloaded || isActive -> accentPalette.onContainer
                            isFailed -> MaterialTheme.colorScheme.onErrorContainer
                            else -> accentPalette.secondaryOnQuietContainer
                        },
                        modifier = Modifier.padding(6.dp).size(18.dp),
                    )
                }
            }
        }
    }
}

fun compactCount(value: Long): String = when {
    value >= 100_000_000L -> "${value / 100_000_000L}亿"
    value >= 10_000L -> "${value / 10_000L}万"
    else -> value.toString()
}

@Composable
fun RecommendSquareCard(
    picUrl: String,
    text: String,
    onAccentColor: ((Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val fallbackAccent = MaterialTheme.colorScheme.tertiaryContainer
    var imageAccent by remember(picUrl, fallbackAccent) { mutableStateOf(fallbackAccent) }
    val extractAccent = rememberThemeColorExtractor(picUrl) { extracted ->
        imageAccent = extracted
        onAccentColor?.invoke(extracted)
    }
    val animatedAccent by animateColorAsState(
        targetValue = imageAccent,
        animationSpec = spring(),
        label = "recommendCardAccent",
    )
    val accentPalette = contentAccentPalette(animatedAccent)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(end = 12.dp, top = 8.dp, bottom = 8.dp)
            .size(168.dp)
            .semantics(mergeDescendants = true) {
                if (text == "私人雷达") contentDescription = text
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = accentPalette.quietContainer,
    ) {
        Box(Modifier.fillMaxSize()) {
            ExpressiveArtwork(
                model = picUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.extraLarge,
                containerColor = accentPalette.quietContainer,
                contentColor = accentPalette.onQuietContainer,
                contentScale = ContentScale.Crop,
                onImageLoaded = extractAccent,
            )
            // 原版特例：私人雷达封面自带文字，不叠加遮罩与标题
            if (text != "私人雷达") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accentPalette.accent.copy(alpha = 0.40f),
                                    Color.Black.copy(alpha = 0.72f),
                                ),
                                startY = 120f,
                            ),
                        ),
                )
                Text(
                    text = text,
                    fontSize = 17.sp,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun <T> SectionWithLazyRow(
    title: String,
    items: List<T>,
    isLoading: Boolean = false,
    isFromCache: Boolean = false,
    isRefreshing: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    key: ((T) -> Any)? = null,
    action: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        ExpressiveSectionTitle(
            text = title,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            action = action,
        )
        if (isFromCache) {
            ExpressiveCacheHint(
                isRefreshing = isRefreshing,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        if (errorMessage != null) {
            ExpressiveStatePanel(
                title = "$title 加载失败",
                message = errorMessage,
                icon = AppIcons.Refresh,
                tone = ExpressiveStateTone.Error,
                actionLabel = onRetry?.let { "重试" },
                onAction = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (isLoading) {
            ExpressiveLoadingState(
                label = "正在加载$title…",
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (items.isEmpty()) {
            ExpressiveStatePanel(
                title = "暂无$title",
                message = "稍后刷新后再来看看。",
                icon = AppIcons.GraphicEq,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LazyRow {
                items(items = items, key = key) { item -> itemContent(item) }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    userPlaylistEach: UserPlaylistEach,
    specialCard: String,
    index: Int,
    count: Int,
    accentColor: Color? = null,
    onClick: () -> Unit,
    onSpecialClick: (() -> Unit)? = null,
    specialActionLoading: Boolean = false,
) {
    val fallbackAccent = MaterialTheme.colorScheme.tertiaryContainer
    var imageAccent by remember(userPlaylistEach.coverImgUrl, specialCard, accentColor, fallbackAccent) {
        mutableStateOf(accentColor ?: fallbackAccent)
    }
    val extractAccent = rememberThemeColorExtractor(
        requestKey = userPlaylistEach.coverImgUrl to specialCard,
    ) { imageAccent = it }
    val animatedAccent by animateColorAsState(
        targetValue = accentColor ?: imageAccent,
        animationSpec = spring(),
        label = "playlistCardAccent",
    )
    val accentPalette = contentAccentPalette(animatedAccent)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.5.dp),
        shape = connectedListItemShape(index, count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveArtwork(
                model = userPlaylistEach.coverImgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp),
                shape = MaterialTheme.shapes.large,
                containerColor = accentPalette.container,
                contentColor = accentPalette.onContainer,
                onImageLoaded = { image ->
                    if (accentColor == null) {
                        extractAccent(image)
                    }
                },
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userPlaylistEach.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentPalette.onQuietContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = userPlaylistEach.creator.nickname,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${userPlaylistEach.trackCount} 首 · ${compactCount(userPlaylistEach.playCount)} 次播放",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentPalette.secondaryOnQuietContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (specialCard != "no") {
                Spacer(modifier = Modifier.width(12.dp))
                if (specialCard == "fav" && onSpecialClick != null) {
                    Surface(
                        onClick = {
                            if (!specialActionLoading) onSpecialClick()
                        },
                        shape = CircleShape,
                        color = accentPalette.container,
                        contentColor = accentPalette.onContainer,
                    ) {
                        if (specialActionLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp)
                                    .semantics { contentDescription = "正在启动心动模式" },
                                color = accentPalette.onContainer,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = AppIcons.Favorite,
                                contentDescription = "启动心动模式",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = accentPalette.container,
                        contentColor = accentPalette.onContainer,
                    ) {
                        Icon(
                            imageVector = if (specialCard == "fav") AppIcons.Favorite
                            else AppIcons.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
