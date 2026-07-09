package com.leejlredstar.redefinencm.kmp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.lyric.WebViewLyricScreen
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.MiniNowPlayingBar
import com.leejlredstar.redefinencm.kmp.ui.screen.DownloadManagementScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.HomeScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LoginScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.NowPlayingScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.PlaylistDetailScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SettingsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.UserPlaylistScreen
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.BackHandler
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.themeColorFromCoilImage
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.compose.koinInject

private sealed interface TabDest {
    data object Home : TabDest
    data object My : TabDest
    data object Settings : TabDest
}

private sealed interface PushedDest {
    data object Login : PushedDest
    data object NowPlaying : PushedDest
    data object FullLyric : PushedDest
    data object Downloads : PushedDest
    data class Playlist(val id: Long) : PushedDest
}

private data class NavigationItem(
    val label: String,
    val icon: ImageVector,
    val dest: TabDest,
)

private sealed interface RootDest {
    val stackDepth: Int

    data class Tab(val tab: TabDest) : RootDest {
        override val stackDepth: Int = 0
    }

    data class Pushed(val dest: PushedDest, override val stackDepth: Int) : RootDest
}

/**
 * Root composable shared across Android / iOS / Desktop / Web. 3-tab nav (Recommend / My /
 * Settings) with a push stack for Login, NowPlaying, PlaylistDetail. 窄屏用底部 NavigationBar，
 * 宽屏（≥600dp）用侧边 NavigationRail（原版 WindowWidthSizeClass 响应式布局）。
 * Koin must already be started before this is called.
 */
@Composable
fun App() {
    RedefineNCMTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val settings: PlatformSettings = koinInject()
            val mainViewModel: MainViewModel = koinInject()
            val player: PlatformPlayer = koinInject()
            val currentMedia by player.currentMedia.collectAsState()
            val userDetail by mainViewModel.userDetail.collectAsState()
            val chromeAccentSource = currentMedia?.artworkUri ?: userDetail?.profile?.avatarUrl
            val defaultChromeAccent = MaterialTheme.colorScheme.primaryContainer
            var rawChromeAccent by remember(chromeAccentSource, defaultChromeAccent) {
                mutableStateOf(defaultChromeAccent)
            }
            val chromeAccent by animateColorAsState(
                targetValue = rawChromeAccent,
                animationSpec = spring(),
                label = "appChromeAccent",
            )
            val chromePalette = contentAccentPalette(chromeAccent)
            val platform = remember { getPlatform() }

            var currentTab by remember { mutableStateOf<TabDest>(TabDest.Home) }
            val pushedStack = remember {
                mutableStateListOf<PushedDest>().apply {
                    // 原版 SplashActivity：无 cookie 时先进登录页
                    if (settings.getString(SettingKeys.COOKIE, "").isBlank()) add(PushedDest.Login)
                }
            }
            fun push(dest: PushedDest) = pushedStack.add(dest)
            fun back() { if (pushedStack.isNotEmpty()) pushedStack.removeAt(pushedStack.lastIndex) }
            fun openDownloads() {
                pushedStack.clear()
                push(PushedDest.Downloads)
            }
            fun openFullLyric() {
                val existingIndex = pushedStack.indexOfLast { it is PushedDest.FullLyric }
                if (existingIndex >= 0) {
                    while (pushedStack.lastIndex > existingIndex) {
                        pushedStack.removeAt(pushedStack.lastIndex)
                    }
                } else {
                    push(PushedDest.FullLyric)
                }
            }

            BackHandler(enabled = pushedStack.isNotEmpty()) { back() }

            LaunchedEffect(Unit) {
                AppNavigationRequests.openDownloadsRequestId.collect { requestId ->
                    if (AppNavigationRequests.consumeOpenDownloadsRequest(requestId)) {
                        openDownloads()
                    }
                }
            }

            // 启动更新检查提示（原版 SplashActivity Toast）
            val snackbarHostState = remember { SnackbarHostState() }
            val updateMessage by mainViewModel.updateMessage.collectAsState()
            LaunchedEffect(updateMessage) {
                updateMessage?.let {
                    snackbarHostState.showSnackbar(it)
                    mainViewModel.consumeUpdateMessage()
                }
            }

            val showTabs = pushedStack.isEmpty()
            val tabs = remember {
                listOf(
                    NavigationItem("推荐", AppIcons.Home, TabDest.Home),
                    NavigationItem("我的", AppIcons.Person, TabDest.My),
                    NavigationItem("设置", AppIcons.Settings, TabDest.Settings),
                )
            }

            Box(Modifier.fillMaxSize()) {
                ChromeAccentSourceImage(
                    sourceUrl = chromeAccentSource,
                    onAccentColor = { rawChromeAccent = it },
                )
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val useDesktopLayout = platform.isDesktop
                    val isWide = maxWidth >= 600.dp
                    val showMiniPlayer = pushedStack.lastOrNull().let {
                        it !is PushedDest.NowPlaying && it !is PushedDest.FullLyric
                    }
                    val rootDest = pushedStack.lastOrNull()
                        ?.let { RootDest.Pushed(it, pushedStack.size) }
                        ?: RootDest.Tab(currentTab)

                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        floatingActionButtonPosition = FabPosition.End,
                        bottomBar = {
                            AnimatedVisibility(
                                visible = showTabs && !isWide && !useDesktopLayout,
                                enter = bottomNavEnterTransition(),
                                exit = bottomNavExitTransition(),
                            ) {
                                NavigationBar(
                                    containerColor = chromePalette.quietContainer,
                                    tonalElevation = 0.dp,
                                ) {
                                    tabs.forEach { item ->
                                        NavigationBarItem(
                                            selected = currentTab == item.dest,
                                            onClick = { currentTab = item.dest },
                                            icon = { Icon(item.icon, contentDescription = item.label) },
                                            label = { Text(item.label) },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = chromePalette.container,
                                                selectedIconColor = chromePalette.onContainer,
                                                selectedTextColor = chromePalette.onQuietContainer,
                                                unselectedIconColor = chromePalette.onQuietContainer.copy(alpha = 0.64f),
                                                unselectedTextColor = chromePalette.onQuietContainer.copy(alpha = 0.64f),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        floatingActionButton = {
                            AnimatedVisibility(
                                visible = showMiniPlayer && !useDesktopLayout,
                                enter = miniPlayerEnterTransition(),
                                exit = miniPlayerExitTransition(),
                            ) {
                                MiniNowPlayingBar(onExpand = ::openFullLyric)
                            }
                        },
                    ) { innerPadding ->
                        Row(Modifier.fillMaxSize()) {
                            if (useDesktopLayout) {
                                DesktopSidebar(
                                    tabs = tabs,
                                    currentTab = currentTab,
                                    downloadsSelected = rootDest is RootDest.Pushed &&
                                        rootDest.dest is PushedDest.Downloads,
                                    accentPalette = chromePalette,
                                    player = player,
                                    onSelectTab = {
                                        pushedStack.clear()
                                        currentTab = it
                                    },
                                    onOpenDownloads = ::openDownloads,
                                    onOpenNowPlaying = ::openFullLyric,
                                )
                            } else if (isWide) {
                                AnimatedVisibility(
                                    visible = showTabs,
                                    enter = railEnterTransition(),
                                    exit = railExitTransition(),
                                ) {
                                    NavigationRail(
                                        containerColor = chromePalette.quietContainer,
                                    ) {
                                        tabs.forEach { item ->
                                            NavigationRailItem(
                                                selected = currentTab == item.dest,
                                                onClick = { currentTab = item.dest },
                                                icon = { Icon(item.icon, contentDescription = item.label) },
                                                label = { Text(item.label) },
                                                colors = NavigationRailItemDefaults.colors(
                                                    indicatorColor = chromePalette.container,
                                                    selectedIconColor = chromePalette.onContainer,
                                                    selectedTextColor = chromePalette.onQuietContainer,
                                                    unselectedIconColor = chromePalette.onQuietContainer.copy(alpha = 0.64f),
                                                    unselectedTextColor = chromePalette.onQuietContainer.copy(alpha = 0.64f),
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                            Column(Modifier.weight(1f).fillMaxSize()) {
                                AnimatedContent(
                                    targetState = rootDest,
                                    transitionSpec = { pageTransition(initialState, targetState) },
                                    modifier = Modifier.weight(1f).fillMaxSize(),
                                    label = "AppPageTransition",
                                ) { target ->
                                    when (target) {
                                        is RootDest.Pushed -> when (val dest = target.dest) {
                                            is PushedDest.Login -> LoginScreen(onBack = ::back)
                                            is PushedDest.NowPlaying -> NowPlayingScreen(
                                                onBack = ::back,
                                                onOpenFullLyric = ::openFullLyric,
                                            )
                                            is PushedDest.FullLyric -> WebViewLyricScreen(onBack = ::back)
                                            is PushedDest.Downloads -> DownloadManagementScreen(
                                                scaffoldPadding = innerPadding,
                                            )
                                            is PushedDest.Playlist -> PlaylistDetailScreen(
                                                playlistId = dest.id,
                                                onBack = ::back,
                                            )
                                        }
                                        is RootDest.Tab -> when (target.tab) {
                                            is TabDest.Home -> HomeScreen(
                                                scaffoldPadding = innerPadding,
                                                onOpenPlaylist = { push(PushedDest.Playlist(it)) },
                                                onOpenMy = { currentTab = TabDest.My },
                                            )
                                            is TabDest.My -> UserPlaylistScreen(
                                                scaffoldPadding = innerPadding,
                                                onOpenPlaylist = { push(PushedDest.Playlist(it)) },
                                            )
                                            is TabDest.Settings -> SettingsScreen(
                                                scaffoldPadding = innerPadding,
                                                onOpenLogin = { push(PushedDest.Login) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebar(
    tabs: List<NavigationItem>,
    currentTab: TabDest,
    downloadsSelected: Boolean,
    accentPalette: ContentAccentPalette,
    player: PlatformPlayer,
    onSelectTab: (TabDest) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    Surface(
        color = accentPalette.quietContainer,
        modifier = Modifier.width(276.dp).fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                Text(
                    text = "RedefineNCM",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentPalette.onQuietContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Desktop",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentPalette.onQuietContainer.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            tabs.forEach { item ->
                DesktopNavItem(
                    label = item.label,
                    icon = item.icon,
                    selected = currentTab == item.dest,
                    accentPalette = accentPalette,
                    onClick = { onSelectTab(item.dest) },
                )
            }
            DesktopNavItem(
                label = "下载管理",
                icon = AppIcons.Download,
                selected = downloadsSelected,
                accentPalette = accentPalette,
                onClick = onOpenDownloads,
            )
            Spacer(Modifier.weight(1f))
            DesktopNowPlayingStrip(
                player = player,
                accentPalette = accentPalette,
                onOpenNowPlaying = onOpenNowPlaying,
            )
        }
    }
}

@Composable
private fun DesktopNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentPalette: ContentAccentPalette,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) accentPalette.container else Color.Transparent,
        contentColor = if (selected) accentPalette.onContainer else accentPalette.onQuietContainer,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DesktopNowPlayingStrip(
    player: PlatformPlayer,
    accentPalette: ContentAccentPalette,
    onOpenNowPlaying: () -> Unit,
) {
    val media by player.currentMedia.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    val artwork = media?.artworkUri.orEmpty()
    val hasMedia = media != null
    val safePosition = position.coerceAtLeast(0L)
    val safeDuration = duration.coerceAtLeast(0L)
    val progress = if (safeDuration > 0L) {
        (safePosition.toDouble() / safeDuration.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }

    Surface(
        onClick = { if (hasMedia) onOpenNowPlaying() },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = accentPalette.container,
                    contentColor = accentPalette.onContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    if (artwork.isNotBlank()) {
                        AsyncImage(
                            model = artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(AppIcons.GraphicEq, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = media?.title ?: "未播放",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = media?.artist ?: "RedefineNCM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = accentPalette.accent,
                trackColor = accentPalette.onQuietContainer.copy(alpha = 0.20f),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (hasMedia) {
                        "${formatDesktopPlayerDuration(safePosition)} / ${formatDesktopPlayerDuration(safeDuration)}"
                    } else {
                        "0:00 / 0:00"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { player.seekToPrevious() },
                        enabled = hasMedia,
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.KeyboardArrowLeft,
                            contentDescription = "上一首",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { player.togglePlayPause() },
                        enabled = hasMedia,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { player.seekToNext() },
                        enabled = hasMedia,
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.KeyboardArrowRight,
                            contentDescription = "下一首",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatDesktopPlayerDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun ChromeAccentSourceImage(
    sourceUrl: String?,
    onAccentColor: (Color) -> Unit,
) {
    if (sourceUrl.isNullOrBlank()) return
    AsyncImage(
        model = sourceUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(1.dp).alpha(0f),
        onSuccess = { state ->
            themeColorFromCoilImage(state.result.image)?.let { onAccentColor(Color(it)) }
        },
    )
}

private fun pageTransition(initial: RootDest, target: RootDest): ContentTransform =
    when {
        isNowPlayingSheetTransition(initial, target) -> sheetTransition(showingSheet = isNowPlaying(target))
        isFullLyricSheetTransition(initial, target) -> sheetTransition(showingSheet = isFullLyric(target))
        initial is RootDest.Tab && target is RootDest.Tab -> {
            val forward = tabIndex(target.tab) > tabIndex(initial.tab)
            horizontalTransition(forward = forward, fullDistance = false)
        }
        target.stackDepth > initial.stackDepth -> horizontalTransition(forward = true, fullDistance = true)
        target.stackDepth < initial.stackDepth -> horizontalTransition(forward = false, fullDistance = true)
        else -> fadeThroughTransition()
    }

private fun horizontalTransition(forward: Boolean, fullDistance: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    val enterDivisor = if (fullDistance) 2 else 5
    val exitDivisor = if (fullDistance) 5 else 8
    return (
        slideInHorizontally(
            animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
            initialOffsetX = { direction * it / enterDivisor },
        ) + pageFadeIn() + pageScaleIn()
        ) togetherWith (
        slideOutHorizontally(
            animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
            targetOffsetX = { -direction * it / exitDivisor },
        ) + pageFadeOut() + pageScaleOut()
        )
}

private fun sheetTransition(showingSheet: Boolean): ContentTransform =
    if (showingSheet) {
        (
            slideInVertically(
                animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 2 },
            ) + pageFadeIn()
            ) togetherWith (
            fadeOut(animationSpec = tween(180, easing = LinearOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
                )
            )
    } else {
        (
            fadeIn(
                animationSpec = tween(180, delayMillis = 60, easing = LinearOutSlowInEasing),
            ) + scaleIn(
                initialScale = 0.98f,
                animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
            )
            ) togetherWith (
            slideOutVertically(
                animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 2 },
            ) + fadeOut(animationSpec = tween(160, easing = LinearOutSlowInEasing))
            )
    }

private fun fadeThroughTransition(): ContentTransform =
    (pageFadeIn(delayMillis = 90) + pageScaleIn()) togetherWith
        (pageFadeOut() + pageScaleOut())

private fun pageFadeIn(delayMillis: Int = 60): EnterTransition =
    fadeIn(animationSpec = tween(180, delayMillis = delayMillis, easing = LinearOutSlowInEasing))

private fun pageFadeOut(): ExitTransition =
    fadeOut(animationSpec = tween(120, easing = LinearOutSlowInEasing))

private fun pageScaleIn(): EnterTransition =
    scaleIn(
        initialScale = 0.985f,
        animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
    )

private fun pageScaleOut(): ExitTransition =
    scaleOut(
        targetScale = 0.985f,
        animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
    )

private fun bottomNavEnterTransition(): EnterTransition =
    slideInVertically(
        animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
        initialOffsetY = { it },
    ) + fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing))

private fun bottomNavExitTransition(): ExitTransition =
    slideOutVertically(
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        targetOffsetY = { it },
    ) + fadeOut(animationSpec = tween(120, easing = LinearOutSlowInEasing))

private fun railEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
        initialOffsetX = { -it },
    ) + fadeIn(animationSpec = tween(180, easing = LinearOutSlowInEasing))

private fun railExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        targetOffsetX = { -it },
    ) + fadeOut(animationSpec = tween(120, easing = LinearOutSlowInEasing))

private fun miniPlayerEnterTransition(): EnterTransition =
    scaleIn(
        initialScale = 0.88f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
    ) + fadeIn(animationSpec = tween(160, easing = LinearOutSlowInEasing))

private fun miniPlayerExitTransition(): ExitTransition =
    scaleOut(
        targetScale = 0.88f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
    ) + fadeOut(animationSpec = tween(120, easing = LinearOutSlowInEasing))

private fun isNowPlayingSheetTransition(initial: RootDest, target: RootDest): Boolean =
    isNowPlaying(initial) || isNowPlaying(target)

private fun isFullLyricSheetTransition(initial: RootDest, target: RootDest): Boolean =
    isFullLyric(initial) || isFullLyric(target)

private fun isNowPlaying(dest: RootDest): Boolean =
    (dest as? RootDest.Pushed)?.dest is PushedDest.NowPlaying

private fun isFullLyric(dest: RootDest): Boolean =
    (dest as? RootDest.Pushed)?.dest is PushedDest.FullLyric

private fun tabIndex(tab: TabDest): Int =
    when (tab) {
        is TabDest.Home -> 0
        is TabDest.My -> 1
        is TabDest.Settings -> 2
    }

private const val PageTransitionMillis = 320
