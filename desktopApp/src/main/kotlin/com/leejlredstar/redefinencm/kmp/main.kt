package com.leejlredstar.redefinencm.kmp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.notification.FloatingLyricData
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.smtc.DesktopMediaControls
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import org.koin.core.context.GlobalContext
import java.awt.Dimension

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // AMLL 歌词页现在跑在系统 WebView（Windows=WebView2）里，见 LyricScreen.jvm.kt。
    // 历史教训（勿回退）：JavaFX WebKit 需要 prism.maxvram 调大才不白屏，且无 GPU 合成，
    // 字体/布局/动画均残缺；prism.order=sw 会打满 CPU 饿死网络协程。
    initKoin()
    val settings = GlobalContext.get().get<PlatformSettings>()
    LyricNotificationController.setOptionalSurfaceEnabled(
        settings.getBoolean(SettingKeys.ENABLE_EXTRA_LYRIC_SURFACE, false),
    )
    application {
        val mainWindowState = rememberWindowState(
            size = DpSize(1280.dp, 820.dp),
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = mainWindowState,
            title = "RedefineNCM",
            decoration = WindowDecoration.Undecorated(),
            resizable = true,
        ) {
            val player = remember { GlobalContext.get().get<PlatformPlayer>() }
            val mediaControls = remember(player) { DesktopMediaControls(player) }
            DisposableEffect(window, mediaControls) {
                mediaControls.start(window)
                onDispose { mediaControls.stop() }
            }

            val density = LocalDensity.current
            LaunchedEffect(window, density) {
                window.minimumSize = Dimension(
                    with(density) { 720.dp.roundToPx() },
                    with(density) { 520.dp.roundToPx() },
                )
            }

            val toggleMaximize = {
                mainWindowState.placement = if (
                    mainWindowState.placement == WindowPlacement.Maximized
                ) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Maximized
                }
            }
            RedefineNCMTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Win10WindowChrome(
                            isMaximized = mainWindowState.placement == WindowPlacement.Maximized,
                            onMinimize = { mainWindowState.isMinimized = true },
                            onToggleMaximize = toggleMaximize,
                            onClose = ::exitApplication,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            App()
                        }
                    }
                }
            }
        }

        // Desktop floating-lyrics window (goal #2: the desktop equivalent of the Android
        // notification / iOS Live Activity). It is a second, frameless, always-on-top window
        // driven entirely by the shared LyricNotificationController (JVM actual): the playback
        // pipeline calls updateLyric(...) + show(), and this window mirrors that state.
        FloatingLyricWindow()
    }
}

@Composable
private fun ApplicationScope.FloatingLyricWindow() {
    val visible by LyricNotificationController.isWindowVisible.collectAsState()
    if (!visible) return

    val data by LyricNotificationController.floatingLyricData.collectAsState()
    val player = remember { GlobalContext.get().get<PlatformPlayer>() }
    var alwaysOnTop by remember { mutableStateOf(true) }
    val windowState = rememberWindowState(
        size = DpSize(760.dp, 224.dp),
        position = WindowPosition(Alignment.BottomCenter),
    )

    Window(
        onCloseRequest = { LyricNotificationController.hide() },
        state = windowState,
        title = "桌面歌词",
        undecorated = true,   // frameless
        transparent = true,   // translucent (requires undecorated)
        alwaysOnTop = alwaysOnTop,
        resizable = true,
    ) {
        val density = LocalDensity.current
        LaunchedEffect(window, density) {
            window.minimumSize = Dimension(
                with(density) { 620.dp.roundToPx() },
                with(density) { 208.dp.roundToPx() },
            )
        }
        RedefineNCMTheme {
            WindowDraggableArea {
                FloatingLyricContent(
                    data = data,
                    onPrevious = player::seekToPrevious,
                    onTogglePlayPause = player::togglePlayPause,
                    onNext = player::seekToNext,
                    alwaysOnTop = alwaysOnTop,
                    onToggleAlwaysOnTop = { alwaysOnTop = !alwaysOnTop },
                    onClose = { LyricNotificationController.hide() },
                )
            }
        }
    }
}

@Composable
private fun FloatingLyricContent(
    data: FloatingLyricData?,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    alwaysOnTop: Boolean,
    onToggleAlwaysOnTop: () -> Unit,
    onClose: () -> Unit,
) {
    var artworkAccent by remember(data?.artworkUri) { mutableStateOf<Color?>(null) }
    val extractAccent = rememberThemeColorExtractor(data?.artworkUri) { artworkAccent = it }
    val palette = contentAccentPalette(artworkAccent ?: MaterialTheme.colorScheme.primary)
    val expressiveSpring = spring<Color>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val animatedContainer by animateColorAsState(
        targetValue = palette.container,
        animationSpec = expressiveSpring,
        label = "floating lyric container",
    )
    val animatedQuietContainer by animateColorAsState(
        targetValue = palette.quietContainer,
        animationSpec = expressiveSpring,
        label = "floating lyric quiet container",
    )
    val animatedContent by animateColorAsState(
        targetValue = palette.onQuietContainer,
        animationSpec = expressiveSpring,
        label = "floating lyric content",
    )
    val animatedSecondaryContent by animateColorAsState(
        targetValue = palette.secondaryOnQuietContainer,
        animationSpec = expressiveSpring,
        label = "floating lyric secondary content",
    )
    val animatedAccent by animateColorAsState(
        targetValue = palette.accent,
        animationSpec = expressiveSpring,
        label = "floating lyric accent",
    )
    val animatedOnAccent by animateColorAsState(
        targetValue = palette.onAccent,
        animationSpec = expressiveSpring,
        label = "floating lyric on accent",
    )
    val progress = data?.let { lyric ->
        if (lyric.durationMs > 0L) {
            (lyric.positionMs.toFloat() / lyric.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    } ?: 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .semantics { paneTitle = "桌面歌词" },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                animatedContainer.copy(alpha = 0.96f),
                                animatedQuietContainer.copy(alpha = 0.92f),
                            ),
                        ),
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingArtwork(
                    artworkUri = data?.artworkUri.orEmpty(),
                    title = data?.title.orEmpty(),
                    containerColor = animatedQuietContainer,
                    contentColor = animatedContent,
                    onArtworkLoaded = extractAccent,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (data?.isPlaying == true) AppIcons.GraphicEq else AppIcons.Pause,
                            contentDescription = null,
                            tint = animatedSecondaryContent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = data?.title?.ifBlank { "RedefineNCM" } ?: "RedefineNCM",
                            style = MaterialTheme.typography.titleMedium,
                            color = animatedContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        data?.artist?.takeIf { it.isNotBlank() }?.let { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.labelMedium,
                                color = animatedSecondaryContent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 160.dp),
                            )
                        }
                        FilledTonalIconButton(
                            onClick = onClose,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = animatedContent.copy(alpha = 0.12f),
                                contentColor = animatedContent,
                            ),
                        ) {
                            Icon(
                                imageVector = AppIcons.Clear,
                                contentDescription = "关闭桌面歌词",
                            )
                        }
                    }

                    Crossfade(
                        targetState = data?.currentLyric?.ifBlank { data.title }.orEmpty(),
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        label = "current lyric",
                    ) { lyric ->
                        Text(
                            text = lyric.ifBlank { "暂无歌词" },
                            style = MaterialTheme.typography.headlineSmall,
                            color = animatedContent,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Text(
                        text = data?.nextLyric?.ifBlank { "下一句歌词将在这里显示" }
                            ?: "下一句歌词将在这里显示",
                        style = MaterialTheme.typography.bodyMedium,
                        color = animatedSecondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(CircleShape),
                            color = animatedAccent,
                            trackColor = animatedContent.copy(alpha = 0.18f),
                        )
                        FilterChip(
                            selected = alwaysOnTop,
                            onClick = onToggleAlwaysOnTop,
                            label = { Text("置顶") },
                            leadingIcon = if (alwaysOnTop) {
                                {
                                    Icon(
                                        imageVector = AppIcons.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.height(48.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = animatedContent.copy(alpha = 0.08f),
                                labelColor = animatedContent,
                                iconColor = animatedContent,
                                selectedContainerColor = animatedAccent,
                                selectedLabelColor = animatedOnAccent,
                                selectedLeadingIconColor = animatedOnAccent,
                            ),
                        )
                        PlaybackIconButton(
                            icon = AppIcons.KeyboardArrowLeft,
                            description = "上一首",
                            containerColor = animatedContent.copy(alpha = 0.10f),
                            contentColor = animatedContent,
                            onClick = onPrevious,
                        )
                        PlaybackIconButton(
                            icon = if (data?.isPlaying == true) AppIcons.Pause else AppIcons.PlayArrow,
                            description = if (data?.isPlaying == true) "暂停" else "播放",
                            containerColor = animatedAccent,
                            contentColor = animatedOnAccent,
                            onClick = onTogglePlayPause,
                        )
                        PlaybackIconButton(
                            icon = AppIcons.KeyboardArrowRight,
                            description = "下一首",
                            containerColor = animatedContent.copy(alpha = 0.10f),
                            contentColor = animatedContent,
                            onClick = onNext,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingArtwork(
    artworkUri: String,
    title: String,
    containerColor: Color,
    contentColor: Color,
    onArtworkLoaded: (coil3.Image) -> Unit,
) {
    Surface(
        modifier = Modifier
            .height(136.dp)
            .aspectRatio(1f),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        if (artworkUri.isNotBlank()) {
            AsyncImage(
                model = artworkUri,
                contentDescription = title.takeIf { it.isNotBlank() }?.let { "$it 的封面" } ?: "歌曲封面",
                contentScale = ContentScale.Crop,
                onSuccess = { state -> onArtworkLoaded(state.result.image) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = AppIcons.GraphicEq,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = contentColor.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun PlaybackIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
        )
    }
}
