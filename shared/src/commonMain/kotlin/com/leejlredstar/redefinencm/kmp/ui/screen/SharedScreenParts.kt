package com.leejlredstar.redefinencm.kmp.ui.screen

import com.leejlredstar.redefinencm.kmp.AppNavigationRequests
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderTrack
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.data.api.dto.UserPlaylistEach
import com.leejlredstar.redefinencm.kmp.data.toPlayerMediaInfo
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
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.formatPlaybackDuration
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import com.leejlredstar.redefinencm.kmp.getPlatform
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import org.koin.compose.koinInject
import com.leejlredstar.redefinencm.kmp.player.PlaybackSource

/** Map an API song DTO to the player's [MediaInfo] (placeholder URI resolved at play time). */
fun SongDetailSongs.toMediaInfo(
    sourceId: String = "",
): MediaInfo = toPlayerMediaInfo(sourceId)

/** The same, for a track that may have come from any provider. */
fun ProviderTrack.toMediaInfo(
    sourceId: String = "",
): MediaInfo = toPlayerMediaInfo(sourceId)

/** One entry of a song row's overflow menu. */
data class SongRowAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Plays one song out of a list.
 *
 * With [wholeList] the list becomes the queue and playback starts at [index]; otherwise the song
 * alone replaces the queue. Every list in the app goes through here so the setting that picks
 * between the two means the same thing on the home page, in search results and in playlists.
 */
fun playFromList(
    player: PlatformPlayer,
    items: List<MediaInfo>,
    index: Int,
    wholeList: Boolean,
    source: String? = null,
) {
    val song = items.getOrNull(index) ?: return
    PlaybackSource.set(source)
    if (wholeList) {
        player.setQueue(items, index)
    } else {
        player.setQueue(listOf(song), 0)
    }
}

/** The actions a song row offers for a song the player can take: queue it, download it, share it. */
@Composable
fun rememberSongRowActions(
    media: MediaInfo,
    neteaseSong: SongDetailSongs? = null,
    playlistId: Long? = null,
): List<SongRowAction> {
    val player = koinInject<PlatformPlayer>()
    val downloadManager = koinInject<SongDownloadManager>()
    // LocalClipboard needs a platform ClipEntry and common code has no plain-text factory for
    // one; the text-only manager does the same job on every target.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    return remember(media, neteaseSong, playlistId, player, downloadManager, clipboard) {
        buildList {
            add(SongRowAction("加入播放队列", AppIcons.PlaylistAdd) { player.addToQueue(media) })
            if (neteaseSong != null) {
                add(
                    SongRowAction("下载", AppIcons.Download) {
                        downloadManager.enqueueSongs(listOf(neteaseSong), playlistId)
                    },
                )
                add(
                    SongRowAction("复制歌曲链接", AppIcons.Link) {
                        clipboard.setText(AnnotatedString(neteaseSongUrl(neteaseSong.id)))
                    },
                )
                // The names on a song lead to their pages now that there are pages.
                neteaseSong.ar.filter { it.id != 0L }.take(MaxArtistActions).forEach { artist ->
                    add(
                        SongRowAction("歌手：${artist.name}", AppIcons.Person) {
                            AppNavigationRequests.openArtist(artist.id)
                        },
                    )
                }
                if (neteaseSong.al.id != 0L) {
                    add(
                        SongRowAction("专辑：${neteaseSong.al.name}", AppIcons.Album) {
                            AppNavigationRequests.openAlbum(neteaseSong.al.id)
                        },
                    )
                }
            }
        }
    }
}

internal fun neteaseSongUrl(songId: Long): String = "https://music.163.com/song?id=$songId"

/**
 * Connected-list song row, shared by search results, playlists and the daily list.
 *
 * Narrow windows get index, cover, title/artist and the overflow menu. From [WideRowMinWidth] the
 * row also has the album and the duration columns a desktop list is read by.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    mediaId: String? = songId?.toString(),
    durationMs: Long = 0L,
    album: String = "",
    badge: String? = null,
    actions: List<SongRowAction> = emptyList(),
) {
    val settings = koinInject<PlatformSettings>()
    val downloadManager = koinInject<SongDownloadManager>()
    val player = koinInject<PlatformPlayer>()
    val currentMedia by player.currentMedia.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isCurrent = mediaId != null && currentMedia?.id == mediaId
    val artworkAccent = rememberArtworkAccent(
        requestKey = artworkUri,
        override = accentColor,
        label = "songRowAccent",
    )
    val extractAccent = artworkAccent.extract
    val accentPalette = artworkAccent.palette
    var menuOpen by remember { mutableStateOf(false) }
    // Shared with the cover below so a row press morphs the artwork silhouette.
    val interactionSource = remember { MutableInteractionSource() }
    val rowShape = shape
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.5.dp),
    ) {
        val wide = maxWidth >= WideRowMinWidth
        Surface(
            shape = rowShape,
            color = if (isCurrent) accentPalette.container else accentPalette.quietContainer,
            contentColor = accentPalette.onQuietContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClickLabel = if (actions.isNotEmpty()) "更多操作" else null,
                    onLongClick = if (actions.isNotEmpty()) {
                        { menuOpen = true }
                    } else {
                        null
                    },
                )
                .secondaryClick(enabled = actions.isNotEmpty()) { menuOpen = true },
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCurrent) {
                        Icon(
                            imageVector = if (isPlaying) AppIcons.GraphicEq else AppIcons.Pause,
                            contentDescription = if (isPlaying) "正在播放" else "已暂停",
                            tint = accentPalette.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFeatureSettings = "tnum",
                            ),
                            color = accentPalette.secondaryOnQuietContainer,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                ExpressiveArtwork(
                    model = artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.small,
                    pressInteractionSource = interactionSource,
                    containerColor = accentPalette.container,
                    contentColor = accentPalette.onContainer,
                    onImageLoaded = { image ->
                        if (accentColor == null) {
                            extractAccent(image)
                        }
                    },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "未知歌曲" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrent) accentPalette.accent else accentPalette.onQuietContainer,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        badge?.let { label ->
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = accentPalette.onQuietContainer.copy(alpha = 0.10f),
                                contentColor = accentPalette.secondaryOnQuietContainer,
                                modifier = Modifier.padding(end = 6.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = artist.ifBlank { "未知歌手" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = accentPalette.secondaryOnQuietContainer,
                        )
                    }
                }
                if (wide && album.isNotBlank()) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = accentPalette.secondaryOnQuietContainer,
                        modifier = Modifier.weight(0.7f),
                    )
                }
                if (songId != null && settings.getBoolean(SettingKeys.SHOW_DOWNLOAD_STATUS, SettingKeys.SHOW_DOWNLOAD_STATUS_DEFAULT)) {
                    // A fixed slot on wide rows, so the album column lines up whether or not a
                    // row carries a download mark.
                    Box(
                        modifier = if (wide) Modifier.width(44.dp) else Modifier,
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        SongDownloadMark(
                            songId = songId,
                            downloadManager = downloadManager,
                            accentPalette = accentPalette,
                        )
                    }
                }
                if (wide && durationMs > 0L) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = formatPlaybackDuration(durationMs),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = accentPalette.secondaryOnQuietContainer,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 40.dp),
                    )
                }
                if (actions.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = AppIcons.MoreVert,
                                contentDescription = "更多操作",
                                tint = accentPalette.secondaryOnQuietContainer,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            actions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.label) },
                                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        action.onClick()
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

/** Rows switch to the desktop column layout (album, duration) from this width. */
private val WideRowMinWidth = 640.dp

/** Right click opens the same menu a long press does. */
private fun Modifier.secondaryClick(enabled: Boolean, onSecondaryClick: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        event.changes.forEach { it.consume() }
                        onSecondaryClick()
                    }
                }
            }
        }
    }

/**
 * The download mark at the end of a row: downloaded, downloading, or failed (tap to retry).
 * A song that is simply not downloaded, or whose download the user cancelled, has no mark.
 */
@Composable
private fun SongDownloadMark(
    songId: Long,
    downloadManager: SongDownloadManager,
    accentPalette: ContentAccentPalette,
) {
    val downloadedCacheVersion = DownloadedSongsCache.version.collectAsState().value
    val downloadTasks = downloadManager.tasks.collectAsState().value
    val taskStatus = remember(songId, downloadTasks) {
        downloadTasks.firstOrNull { it.id == songId }?.status
    }
    val downloaded = remember(songId, downloadedCacheVersion) {
        DownloadedSongsCache.isDownloaded(songId)
    }
    val isActive = taskStatus == DownloadTaskStatus.Queued ||
        taskStatus == DownloadTaskStatus.Resolving ||
        taskStatus == DownloadTaskStatus.Downloading ||
        taskStatus == DownloadTaskStatus.SavingLyrics
    val isFailed = !downloaded && taskStatus == DownloadTaskStatus.Failed
    if (!downloaded && !isActive && !isFailed) return
    Spacer(Modifier.width(8.dp))
    Surface(
        onClick = { downloadManager.retry(songId) },
        enabled = isFailed,
        shape = CircleShape,
        color = when {
            isFailed -> MaterialTheme.colorScheme.errorContainer
            downloaded -> accentPalette.container
            else -> accentPalette.container.copy(alpha = 0.72f)
        },
        modifier = Modifier.semantics {
            contentDescription = when {
                isFailed -> "下载失败，点按重试"
                downloaded -> "已下载"
                else -> "正在下载"
            }
        },
    ) {
        Icon(
            imageVector = when {
                isFailed -> AppIcons.ErrorOutline
                downloaded -> AppIcons.DownloadDone
                else -> AppIcons.Download
            },
            contentDescription = null,
            tint = if (isFailed) MaterialTheme.colorScheme.onErrorContainer else accentPalette.onContainer,
            modifier = Modifier.padding(6.dp).size(18.dp),
        )
    }
}

fun compactCount(value: Long): String = when {
    value >= 100_000_000L -> "${value / 100_000_000L}亿"
    value >= 10_000L -> "${value / 10_000L}万"
    else -> value.toString()
}

@Composable
fun CarouselItemScope.RecommendSquareCard(
    picUrl: String,
    text: String,
    onAccentColor: ((Color) -> Unit)? = null,
    subtitle: String? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
) {
    // Fully opaque while the tile is near full width, gone by the time it is a sliver.
    val overlayAlpha = ((expandedFraction - 0.45f) / 0.35f).coerceIn(0f, 1f)
    val artworkAccent = rememberArtworkAccent(
        requestKey = picUrl,
        fallback = MaterialTheme.colorScheme.tertiaryContainer,
        label = "recommendCardAccent",
        onAccentColor = onAccentColor,
    )
    val extractAccent = artworkAccent.extract
    val accentPalette = artworkAccent.palette
    // Shared with the artwork below so pressing the card morphs the cover's silhouette rather
    // than only rippling the container.
    val interactionSource = remember { MutableInteractionSource() }
    // One silhouette, not three. maskClip applies the carousel's own mask, which is the shape
    // that actually animates as the tile squeezes; the Surface and the artwork therefore draw
    // square. Previously the container rounded at `large` and the cover at `extraLarge` inside
    // it, so two mismatched arcs sat on top of each other and both got clipped again by the
    // squeeze — the doubled outline.
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .height(CarouselItemWidth)
            .maskClip(MaterialTheme.shapes.large)
            .semantics(mergeDescendants = true) {
                if (text == "私人雷达") contentDescription = text
            },
        shape = RectangleShape,
        color = accentPalette.quietContainer,
        interactionSource = interactionSource,
    ) {
        Box(Modifier.fillMaxSize()) {
            // No press morph here: a MaterialShapes silhouette inside a masked, squeezing tile
            // would be a second animating outline. The morph stays on SongRow, which has no mask.
            ExpressiveArtwork(
                model = picUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
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
                            // Fractional stops, not a pixel startY: `startY = 120f` is 120 raw
                            // pixels, which is a third of the way down a 168dp tile on a 3.5x
                            // phone but almost the whole tile on a 1x one, so the scrim covered
                            // a different amount of artwork on every density.
                            //
                            // Every stop darkens, and none of them is the artwork's accent. The
                            // middle stop used to be `accent`, which is a *light* colour in a
                            // dark scheme (tone 0.78) — and the title's own line sits inside
                            // that band, so a pale cover was being lightened exactly where the
                            // white text had to be read.
                            brush = Brush.verticalGradient(
                                0.32f to Color.Transparent,
                                0.62f to Color.Black.copy(alpha = 0.42f * overlayAlpha),
                                1f to Color.Black.copy(alpha = 0.92f * overlayAlpha),
                            ),
                        ),
                )
                // 16dp keeps the last line clear of the rounded corner's inward curve. The
                // alpha is what keeps a squeezed item legible: the carousel mask would otherwise
                // slice the title mid-character, so it is faded out entirely before the item
                // narrows enough for that to show.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = overlayAlpha },
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = if (subtitle.isNullOrBlank()) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // The song that is playing, marked the way a SongRow marks it.
            if (isCurrent) {
                Surface(
                    shape = CircleShape,
                    color = accentPalette.container,
                    contentColor = accentPalette.onContainer,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .graphicsLayer { alpha = overlayAlpha },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) AppIcons.GraphicEq else AppIcons.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "正在播放" else "已暂停",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SectionWithCarousel(
    title: String,
    items: List<T>,
    isLoading: Boolean = false,
    isFromCache: Boolean = false,
    isRefreshing: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    key: ((T) -> Any)? = null,
    supportingText: String? = null,
    action: (@Composable () -> Unit)? = null,
    onOpenAll: (() -> Unit)? = null,
    onOpenAllLabel: String? = null,
    itemContent: @Composable CarouselItemScope.(T) -> Unit,
) {
    val carouselState = rememberCarouselState { items.size }
    val carouselShown = errorMessage == null && !isLoading && items.isNotEmpty()
    Column(modifier = Modifier.padding(top = 20.dp)) {
        ExpressiveSectionTitle(
            text = title,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            supportingText = supportingText,
            onClick = onOpenAll?.takeIf { carouselShown },
            onClickLabel = onOpenAllLabel,
            action = if (action == null && !(showCarouselPager && carouselShown)) {
                null
            } else {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        action?.invoke()
                        // A mouse wheel only scrolls vertically, so pointer-first platforms get
                        // paging buttons beside the title; touch keeps the swipe alone.
                        if (showCarouselPager && carouselShown) {
                            CarouselPager(state = carouselState)
                        }
                    }
                }
            },
        )
        if (isFromCache) {
            ExpressiveCacheHint(
                isRefreshing = isRefreshing,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        if (errorMessage != null) {
            ExpressiveStatePanel(
                title = "${title}加载失败",
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
                message = if (onRetry != null) "可以点下面的按钮重新加载。" else "稍后再来看看。",
                icon = AppIcons.QueueMusic,
                actionLabel = onRetry?.let { "重新加载" },
                onAction = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Multi-browse carousel: items shrink toward the trailing edge and grow back as they
            // scroll in, which is the row's signature motion.
            //
            // The mask that produces that motion also clips whatever the item draws, so a title
            // laid over the artwork gets sliced mid-character once an item narrows. Items are
            // therefore handed how expanded they currently are, and fade their own overlay out
            // before the mask can cut it — see RecommendSquareCard.
            HorizontalMultiBrowseCarousel(
                state = carouselState,
                preferredItemWidth = CarouselItemWidth,
                itemSpacing = 12.dp,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { index ->
                itemContent(items[index])
            }
        }
    }
}

/**
 * How expanded a carousel item currently is: `1f` at full width, `0f` fully squeezed.
 *
 * Items animate continuously between [CarouselItemDrawInfo.minSize] and
 * [CarouselItemDrawInfo.maxSize] as the row scrolls, so this is read every frame rather than
 * derived once from the index.
 */
@OptIn(ExperimentalMaterial3Api::class)
private val CarouselItemScope.expandedFraction: Float
    get() {
        val info = carouselItemDrawInfo
        val range = info.maxSize - info.minSize
        return if (range <= 0f) 1f else ((info.size - info.minSize) / range).coerceIn(0f, 1f)
    }

/** Home row card size; fixed so every tile is uniform and the overlaid title has stable room. */
private val CarouselItemWidth = 168.dp

private val showCarouselPager: Boolean by lazy { !getPlatform().isMobile }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselPager(
    state: CarouselState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { (CarouselItemWidth * 2).toPx() }
    Row(modifier = modifier, horizontalArrangement = Arrangement.End) {
        IconButton(
            onClick = { scope.launch { state.animateScrollBy(-stepPx) } },
            enabled = state.canScrollBackward,
        ) {
            Icon(AppIcons.KeyboardArrowLeft, contentDescription = "向前翻")
        }
        IconButton(
            onClick = { scope.launch { state.animateScrollBy(stepPx) } },
            enabled = state.canScrollForward,
        ) {
            Icon(AppIcons.KeyboardArrowRight, contentDescription = "向后翻")
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
    /** False for the viewer's own playlists, where the creator line would only repeat their name. */
    showCreator: Boolean = true,
) {
    val artworkAccent = rememberArtworkAccent(
        requestKey = userPlaylistEach.coverImgUrl to specialCard,
        fallback = MaterialTheme.colorScheme.tertiaryContainer,
        override = accentColor,
        label = "playlistCardAccent",
    )
    val extractAccent = artworkAccent.extract
    val accentPalette = artworkAccent.palette
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
            // shapes.small, not large: 40dp corners on a 60dp cover cut it into a circle and
            // cropped whatever the cover had in its corners.
            ExpressiveArtwork(
                model = userPlaylistEach.coverImgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp),
                shape = MaterialTheme.shapes.small,
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
                if (showCreator) {
                    Text(
                        text = userPlaylistEach.creator.nickname,
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentPalette.secondaryOnQuietContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                    // Named, and a full touch target: a bare heart here read as "like", and at
                    // 34dp it was smaller than a finger.
                    Surface(
                        onClick = {
                            if (!specialActionLoading) onSpecialClick()
                        },
                        shape = CircleShape,
                        color = accentPalette.container,
                        contentColor = accentPalette.onContainer,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (specialActionLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .semantics { contentDescription = "正在启动心动模式" },
                                    color = accentPalette.onContainer,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "心动模式",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
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

private const val MaxArtistActions = 3
