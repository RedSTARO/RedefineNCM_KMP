package com.leejlredstar.redefinencm.kmp

import com.leejlredstar.redefinencm.kmp.util.getStringAsync
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailItemDefaults
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.lyric.AmllPlayerScreen
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.rememberNowPlayingUiState
import com.leejlredstar.redefinencm.kmp.ui.component.PlaybackSeekBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveMotion
import com.leejlredstar.redefinencm.kmp.ui.component.MiniNowPlayingBar
import com.leejlredstar.redefinencm.kmp.ui.component.TransportSheets
import com.leejlredstar.redefinencm.kmp.ui.component.formatPlaybackDuration
import com.leejlredstar.redefinencm.kmp.ui.component.rememberSeekDragState
import com.leejlredstar.redefinencm.kmp.ui.component.rememberTransportSheetsState
import com.leejlredstar.redefinencm.kmp.ui.screen.DailySongsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.DownloadManagementScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.HomeScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LoginScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.PlaylistDetailScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.NowPlayingScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SettingsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SongRecognitionScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.UserPlaylistScreen
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.util.BackHandler
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.leejlredstar.redefinencm.kmp.ui.component.OutputVolumeLevel
import com.leejlredstar.redefinencm.kmp.ui.component.outputVolumeLevel

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
    data object SongRecognition : PushedDest
    data object DailySongs : PushedDest
    data class Playlist(val id: Long) : PushedDest
}

private val tabDestSaver = Saver<TabDest, String>(
    save = { destination ->
        when (destination) {
            TabDest.Home -> "home"
            TabDest.My -> "my"
            TabDest.Settings -> "settings"
        }
    },
    restore = { saved ->
        when (saved) {
            "my" -> TabDest.My
            "settings" -> TabDest.Settings
            else -> TabDest.Home
        }
    },
)

private val pushedStackSaver = listSaver<SnapshotStateList<PushedDest>, String>(
    save = { stack -> stack.map(::encodePushedDestination) },
    restore = { saved ->
        mutableStateListOf<PushedDest>().apply {
            saved.mapNotNull(::decodePushedDestination).forEach { destination ->
                // Player surfaces are focus-or-push destinations; a saved stack never needs
                // the same one twice in a row.
                if (destination != lastOrNull()) add(destination)
            }
        }
    },
)

private fun encodePushedDestination(destination: PushedDest): String = when (destination) {
    PushedDest.Login -> "login"
    PushedDest.NowPlaying -> "now-playing"
    PushedDest.FullLyric -> "full-lyric"
    PushedDest.Downloads -> "downloads"
    PushedDest.SongRecognition -> "song-recognition"
    PushedDest.DailySongs -> "daily-songs"
    is PushedDest.Playlist -> "playlist:${destination.id}"
}

private fun decodePushedDestination(saved: String): PushedDest? = when (saved) {
    "login" -> PushedDest.Login
    // Migrate navigation state saved before the legacy KMP player was removed.
    "now-playing" -> PushedDest.NowPlaying
    "full-lyric" -> PushedDest.FullLyric
    "downloads" -> PushedDest.Downloads
    "song-recognition" -> PushedDest.SongRecognition
    "daily-songs" -> PushedDest.DailySongs
    else -> saved.removePrefix("playlist:")
        .takeIf { saved.startsWith("playlist:") }
        ?.toLongOrNull()
        ?.let(PushedDest::Playlist)
}

internal fun <T> MutableList<T>.focusOrPush(destination: T) {
    val existingIndex = lastIndexOf(destination)
    if (existingIndex >= 0) {
        while (lastIndex > existingIndex) removeAt(lastIndex)
    } else {
        add(destination)
    }
}

private data class NavigationItem(
    val label: String,
    val icon: ImageVector,
    val dest: TabDest,
)

internal enum class DesktopLayoutMode {
    Compact,
    Rail,
    RailWithPlayer,
}

internal fun desktopLayoutMode(width: Dp, height: Dp): DesktopLayoutMode = when {
    width < 600.dp || height < 480.dp -> DesktopLayoutMode.Compact
    width >= 900.dp && height >= 900.dp -> DesktopLayoutMode.RailWithPlayer
    else -> DesktopLayoutMode.Rail
}

private sealed interface RootDest {
    val stackDepth: Int

    data class Tab(val tab: TabDest) : RootDest {
        override val stackDepth: Int = 0
    }

    data class Pushed(val dest: PushedDest, override val stackDepth: Int) : RootDest
}

/**
 * Root composable shared across Android / iOS / Desktop / Web. 3-tab nav (Recommend / My /
 * Settings) with a push stack for Login, FullLyric, Downloads, and PlaylistDetail. 窄屏用底部 NavigationBar，
 * 非 Desktop 宽屏（≥600dp）用 NavigationRail；Desktop 紧凑窗口用底栏，中大窗口使用
 * 常驻折叠、覆盖展开的模态宽侧栏。
 * Koin must already be started before this is called.
 */
@Composable
fun App() {
    val settings: PlatformSettings = koinInject()
    var initialCookie by remember(settings) { mutableStateOf<String?>(null) }
    LaunchedEffect(settings) {
        initialCookie = settings.getStringAsync(SettingKeys.COOKIE, "")
    }

    RedefineNCMTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            initialCookie?.let { cookie ->
                AppContent(settings = settings, initialCookie = cookie)
            }
        }
    }
}

/**
 * Expressive navigation affordance for the floating toolbar.
 *
 * Uses [ToggleButton] instead of a plain icon button so selection carries Material's own shape
 * morph — the silhouette relaxes between round and squared as the item becomes checked. That
 * morph replaces the pill indicator a `NavigationBarItem` used to draw behind the icon, and the
 * label is revealed only on the selected item so the toolbar stays compact on narrow windows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveNavToggle(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    palette: ContentAccentPalette,
    onSelect: () -> Unit,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onSelect() },
        shapes = ToggleButtonDefaults.shapes(),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = palette.secondaryOnQuietContainer,
            checkedContainerColor = palette.container,
            checkedContentColor = palette.onContainer,
        ),
    ) {
        Icon(icon, contentDescription = if (selected) null else label)
        AnimatedVisibility(visible = selected) {
            Text(text = label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppContent(
    settings: PlatformSettings,
    initialCookie: String,
) {
            val mainViewModel: MainViewModel = koinInject()
            val player: PlatformPlayer = koinInject()
            val currentMedia by player.currentMedia.collectAsState()
            val chromeAccentSource = currentMedia?.artworkUri
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
            val desktopRailState = rememberWideNavigationRailState()
            var currentTab by rememberSaveable(stateSaver = tabDestSaver) {
                mutableStateOf<TabDest>(TabDest.Home)
            }
            val pushedStack = rememberSaveable(saver = pushedStackSaver) {
                mutableStateListOf<PushedDest>().apply {
                    // 原版 SplashActivity：无 cookie 时先进登录页
                    if (initialCookie.isBlank()) add(PushedDest.Login)
                }
            }
            // Every destination keeps its saved state (scroll offsets, carousel positions, a
            // search left open) while it is off screen, so switching tabs returns to where the
            // user was instead of to the top. A popped page's state is dropped with it.
            val saveableStateHolder = rememberSaveableStateHolder()
            fun forgetPushed(fromDepth: Int) {
                for (depth in pushedStack.size downTo fromDepth) {
                    saveableStateHolder.removeState(pushedStateKey(pushedStack[depth - 1], depth))
                }
            }
            fun push(dest: PushedDest) = pushedStack.add(dest)
            fun back() {
                if (pushedStack.isEmpty()) return
                forgetPushed(fromDepth = pushedStack.size)
                pushedStack.removeAt(pushedStack.lastIndex)
            }
            fun clearPushed() {
                if (pushedStack.isEmpty()) return
                forgetPushed(fromDepth = 1)
                pushedStack.clear()
            }
            fun focusOrPushTracked(dest: PushedDest) {
                val existingIndex = pushedStack.lastIndexOf(dest)
                if (existingIndex >= 0 && existingIndex < pushedStack.lastIndex) {
                    forgetPushed(fromDepth = existingIndex + 2)
                }
                pushedStack.focusOrPush(dest)
            }
            fun openDownloads() {
                focusOrPushTracked(PushedDest.Downloads)
            }
            fun openNowPlaying() {
                focusOrPushTracked(PushedDest.NowPlaying)
            }
            fun openFullLyric() {
                focusOrPushTracked(PushedDest.FullLyric)
            }

            BackHandler(enabled = pushedStack.isNotEmpty()) { back() }

            LaunchedEffect(Unit) {
                AppNavigationRequests.openDownloadsRequestId.collect { requestId ->
                    if (AppNavigationRequests.consumeOpenDownloadsRequest(requestId)) {
                        openDownloads()
                    }
                }
            }
            var searchRequest by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                AppNavigationRequests.openSearchRequestId.collect { requestId ->
                    if (AppNavigationRequests.consumeOpenSearchRequest(requestId)) {
                        clearPushed()
                        currentTab = TabDest.Home
                        searchRequest += 1
                    }
                }
            }
            LaunchedEffect(Unit) {
                AppNavigationRequests.openNowPlayingRequestId.collect { requestId ->
                    if (AppNavigationRequests.consumeOpenNowPlayingRequest(requestId)) {
                        openNowPlaying()
                    }
                }
            }

            // 启动更新检查提示（原版 SplashActivity Toast）
            val snackbarHostState = remember { SnackbarHostState() }
            var desktopSnackbarVisible by remember { mutableStateOf(false) }
            val updateMessage by mainViewModel.updateMessage.collectAsState()
            LaunchedEffect(updateMessage) {
                val message = updateMessage ?: return@LaunchedEffect
                desktopSnackbarVisible = true
                try {
                    snackbarHostState.showSnackbar(message)
                } finally {
                    desktopSnackbarVisible = false
                }
                // 若新的消息取消了本协程，执行不到这里，不会误消费新值。
                mainViewModel.consumeUpdateMessage()
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
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val appContentWidth = maxWidth
                    val appContentHeight = maxHeight
                    val desktopMode = desktopLayoutMode(maxWidth, maxHeight)
                    val desktopCompact = platform.isDesktop && desktopMode == DesktopLayoutMode.Compact
                    val showDesktopRail = platform.isDesktop && !desktopCompact
                    val showDesktopFullPlayer = desktopMode == DesktopLayoutMode.RailWithPlayer
                    val isWide = maxWidth >= 600.dp
                    val showMiniPlayer = maxWidth >= 160.dp &&
                        maxHeight >= 200.dp &&
                        currentMedia != null &&
                        pushedStack.lastOrNull().let { !isPlayerSurface(it) }
                    val rootDest = pushedStack.lastOrNull()
                        ?.let { RootDest.Pushed(it, pushedStack.size) }
                        ?: RootDest.Tab(currentTab)
                    val desktopRailExpanded = platform.isDesktop &&
                        showDesktopRail &&
                        desktopRailState.targetValue == WideNavigationRailValue.Expanded
                    LaunchedEffect(desktopCompact) {
                        if (desktopCompact) desktopRailState.collapse()
                    }

                    val bottomNavVisible =
                        showTabs && if (platform.isDesktop) desktopCompact else !isWide
                    // The toolbar floats over the content instead of occupying a Scaffold
                    // bottomBar, so Scaffold reserves nothing for it and screens are handed this
                    // clearance directly. navigationBars is added because the Scaffold runs with
                    // contentWindowInsets = 0 and a floating toolbar carries no insets of its own
                    // — without it the pill sits under the system gesture bar on phones.
                    val systemNavInset = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                    val contentBottomInset = systemNavInset + if (bottomNavVisible) {
                        ExpressiveLayout.FloatingNavClearance
                    } else {
                        0.dp
                    }
                    // One bottom clearance for every page: the floating toolbar (when shown) plus
                    // the mini player that floats above it. Pages used to add their own trailing
                    // spacers on top of this, which left a dead band at the end of some lists and
                    // none at all under search results.
                    val miniPlayerClearance = if (showMiniPlayer) MiniPlayerClearance else 0.dp
                    val screenPadding = PaddingValues(bottom = contentBottomInset + miniPlayerClearance)
                    val toolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
                        exitDirection = FloatingToolbarExitDirection.Bottom,
                    )
                    // The toolbar's scrolled-away state belongs to the page that scrolled it.
                    // Without this reset a page reached by navigation opened with no navigation
                    // bar until the user happened to scroll.
                    LaunchedEffect(rootDest) {
                        toolbarScrollBehavior.state.offset = 0f
                    }

                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        floatingActionButtonPosition = FabPosition.End,
                        floatingActionButton = {
                            AnimatedVisibility(
                                visible = showMiniPlayer && !desktopRailExpanded,
                                enter = miniPlayerEnterTransition(),
                                exit = miniPlayerExitTransition(),
                            ) {
                                // Scaffold no longer reserves the toolbar's height, so the FAB
                                // has to step over the floating pill itself.
                                Box(
                                    Modifier
                                        .padding(bottom = contentBottomInset)
                                        .graphicsLayer {
                                            // Follow the toolbar down as it scrolls away, so no
                                            // empty band is left where it used to be.
                                            if (bottomNavVisible) {
                                                val state = toolbarScrollBehavior.state
                                                val limit = state.offsetLimit
                                                val hidden = if (limit != 0f) {
                                                    (state.offset / limit).coerceIn(0f, 1f)
                                                } else {
                                                    0f
                                                }
                                                translationY =
                                                    hidden * ExpressiveLayout.FloatingNavClearance.toPx()
                                            }
                                        },
                                ) {
                                    MiniNowPlayingBar(
                                        onExpand = ::openNowPlaying,
                                        onAccentColor = { rawChromeAccent = it },
                                    )
                                }
                            }
                        },
                    ) { innerPadding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(toolbarScrollBehavior),
                        ) {
                        Row(Modifier.fillMaxSize()) {
                            if (showDesktopRail) {
                                DesktopExpandableSidebar(
                                    state = desktopRailState,
                                    tabs = tabs,
                                    selectedTab = if (
                                        rootDest is RootDest.Pushed &&
                                        rootDest.dest is PushedDest.Downloads
                                    ) {
                                        null
                                    } else {
                                        currentTab
                                    },
                                    downloadsSelected = rootDest is RootDest.Pushed &&
                                        rootDest.dest is PushedDest.Downloads,
                                    accentPalette = chromePalette,
                                    player = player,
                                    showFullPlayer = showDesktopFullPlayer,
                                    onSelectTab = {
                                        clearPushed()
                                        currentTab = it
                                    },
                                    onOpenDownloads = ::openDownloads,
                                    recognitionSelected = rootDest is RootDest.Pushed &&
                                        rootDest.dest is PushedDest.SongRecognition,
                                    onOpenRecognition = {
                                        focusOrPushTracked(PushedDest.SongRecognition)
                                    },
                                    onChromeAccent = { rawChromeAccent = it },
                                    onOpenNowPlaying = ::openNowPlaying,
                                )
                            } else if (!platform.isDesktop && isWide) {
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
                                                icon = { Icon(item.icon, contentDescription = null) },
                                                label = { Text(item.label) },
                                                colors = NavigationRailItemDefaults.colors(
                                                    indicatorColor = chromePalette.container,
                                                    selectedIconColor = chromePalette.onContainer,
                                                    selectedTextColor = chromePalette.onQuietContainer,
                                                    unselectedIconColor = chromePalette.secondaryOnQuietContainer,
                                                    unselectedTextColor = chromePalette.secondaryOnQuietContainer,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                            Column(Modifier.weight(1f).fillMaxSize()) {
                                AnimatedContent(
                                    targetState = rootDest,
                                    transitionSpec = {
                                        pageTransition(
                                            initial = initialState,
                                            target = targetState,
                                        )
                                    },
                                    modifier = Modifier.weight(1f).fillMaxSize(),
                                    label = "AppPageTransition",
                                ) { target ->
                                    saveableStateHolder.SaveableStateProvider(target.stateKey()) {
                                    when (target) {
                                        is RootDest.Pushed -> when (val dest = target.dest) {
                                            is PushedDest.Login -> LoginScreen(onBack = ::back)
                                            is PushedDest.NowPlaying -> NowPlayingScreen(
                                                onBack = ::back,
                                                onOpenLyrics = ::openFullLyric,
                                            )
                                            is PushedDest.FullLyric -> AmllPlayerScreen(
                                                onBack = ::back,
                                            )
                                            is PushedDest.Downloads -> DownloadManagementScreen(
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                            )
                                            is PushedDest.SongRecognition -> SongRecognitionScreen(
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                                // Playing a match goes to the player, not back
                                                // to wherever recognition was opened from.
                                                onOpenPlayer = {
                                                    back()
                                                    openNowPlaying()
                                                },
                                            )
                                            is PushedDest.DailySongs -> DailySongsScreen(
                                                onBack = ::back,
                                                scaffoldPadding = screenPadding,
                                            )
                                            is PushedDest.Playlist -> PlaylistDetailScreen(
                                                playlistId = dest.id,
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                                onOpenDownloads = ::openDownloads,
                                            )
                                        }
                                        is RootDest.Tab -> when (target.tab) {
                                            is TabDest.Home -> HomeScreen(
                                                scaffoldPadding = screenPadding,
                                                onOpenPlaylist = { push(PushedDest.Playlist(it)) },
                                                onOpenMy = { currentTab = TabDest.My },
                                                onOpenRecognition = { push(PushedDest.SongRecognition) },
                                                onOpenDailySongs = { push(PushedDest.DailySongs) },
                                                searchRequest = searchRequest,
                                            )
                                            is TabDest.My -> UserPlaylistScreen(
                                                scaffoldPadding = screenPadding,
                                                onOpenPlaylist = { push(PushedDest.Playlist(it)) },
                                                onOpenLogin = { push(PushedDest.Login) },
                                                onOpenDownloads = ::openDownloads,
                                            )
                                            is TabDest.Settings -> SettingsScreen(
                                                scaffoldPadding = screenPadding,
                                                onOpenLogin = { push(PushedDest.Login) },
                                            )
                                        }
                                    }
                                    }
                                }
                            }
                        }
                        if (bottomNavVisible) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                HorizontalFloatingToolbar(
                                    expanded = true,
                                    scrollBehavior = toolbarScrollBehavior,
                                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                                        toolbarContainerColor = chromePalette.quietContainer,
                                        toolbarContentColor = chromePalette.onQuietContainer,
                                    ),
                                ) {
                                    tabs.forEach { item ->
                                        ExpressiveNavToggle(
                                            label = item.label,
                                            icon = item.icon,
                                            selected = currentTab == item.dest,
                                            palette = chromePalette,
                                            onSelect = { currentTab = item.dest },
                                        )
                                    }
                                    if (desktopCompact) {
                                        ExpressiveNavToggle(
                                            label = "下载",
                                            icon = AppIcons.Download,
                                            selected = rootDest is RootDest.Pushed &&
                                                rootDest.dest is PushedDest.Downloads,
                                            palette = chromePalette,
                                            onSelect = ::openDownloads,
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

@Composable
private fun DesktopExpandableSidebar(
    state: WideNavigationRailState,
    tabs: List<NavigationItem>,
    selectedTab: TabDest?,
    downloadsSelected: Boolean,
    accentPalette: ContentAccentPalette,
    player: PlatformPlayer,
    showFullPlayer: Boolean,
    onSelectTab: (TabDest) -> Unit,
    onOpenDownloads: () -> Unit,
    recognitionSelected: Boolean,
    onOpenRecognition: () -> Unit,
    onChromeAccent: (Color) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val railExpanded = state.targetValue == WideNavigationRailValue.Expanded
    val expansionSettled = railExpanded &&
        !state.isAnimating &&
        state.currentValue == state.targetValue
    val modalExpansionActive = state.isAnimating ||
        state.currentValue == WideNavigationRailValue.Expanded ||
        state.targetValue == WideNavigationRailValue.Expanded
    var showExpandedPlayerContent by remember { mutableStateOf(false) }
    val railColors = WideNavigationRailDefaults.colors(
        containerColor = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modalContainerColor = accentPalette.quietContainer,
        modalScrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        modalContentColor = accentPalette.onQuietContainer,
    )

    LaunchedEffect(showFullPlayer, expansionSettled, modalExpansionActive) {
        when {
            !showFullPlayer || !modalExpansionActive -> showExpandedPlayerContent = false
            expansionSettled -> showExpandedPlayerContent = true
        }
    }

    fun collapseAfter(action: () -> Unit) {
        action()
        scope.launch { state.collapse() }
    }
    fun toggleRail() {
        scope.launch {
            val target = if (railExpanded) {
                WideNavigationRailValue.Collapsed
            } else {
                WideNavigationRailValue.Expanded
            }
            if (target == WideNavigationRailValue.Expanded) {
                state.expand()
            } else {
                state.collapse()
            }
        }
    }

    ModalWideNavigationRail(
        state = state,
        colors = railColors,
    ) {
        DesktopSidebarContent(
            railExpanded = railExpanded,
            expandedContentVisible = modalExpansionActive,
            tabs = tabs,
            selectedTab = selectedTab,
            downloadsSelected = downloadsSelected,
            accentPalette = accentPalette,
            player = player,
            showFullPlayer = showFullPlayer,
            showExpandedPlayerContent = showExpandedPlayerContent,
            onToggle = ::toggleRail,
            onSelectTab = { collapseAfter { onSelectTab(it) } },
            onOpenDownloads = { collapseAfter(onOpenDownloads) },
            recognitionSelected = recognitionSelected,
            onOpenRecognition = { collapseAfter(onOpenRecognition) },
            onChromeAccent = onChromeAccent,
            onOpenNowPlaying = { collapseAfter(onOpenNowPlaying) },
        )
    }

}

@Composable
private fun DesktopSidebarContent(
    railExpanded: Boolean,
    expandedContentVisible: Boolean,
    tabs: List<NavigationItem>,
    selectedTab: TabDest?,
    downloadsSelected: Boolean,
    accentPalette: ContentAccentPalette,
    player: PlatformPlayer,
    showFullPlayer: Boolean,
    showExpandedPlayerContent: Boolean,
    onToggle: () -> Unit,
    onSelectTab: (TabDest) -> Unit,
    onOpenDownloads: () -> Unit,
    recognitionSelected: Boolean,
    onOpenRecognition: () -> Unit,
    onChromeAccent: (Color) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val itemColors = WideNavigationRailItemDefaults.colors(
        selectedIconColor = accentPalette.onContainer,
        selectedTextColor = accentPalette.onContainer,
        selectedIndicatorColor = accentPalette.container,
        unselectedIconColor = accentPalette.secondaryOnQuietContainer,
        unselectedTextColor = accentPalette.secondaryOnQuietContainer,
    )
    // Expanded, an item is a pill as wide as its label; filling the width centred it, out of
    // line with the app name and the section title above. Collapsed, it centres in the rail.
    val railItemModifier = if (expandedContentVisible) Modifier else Modifier.fillMaxWidth()

    Column(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                // 24dp centres the menu button in the collapsed rail and puts its icon over the
                // item icons of the expanded one.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = onToggle,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accentPalette.container,
                        contentColor = accentPalette.onContainer,
                    ),
                    modifier = Modifier.semantics {
                        stateDescription = if (railExpanded) "侧栏已展开" else "侧栏已收起"
                    },
                ) {
                    Icon(
                        imageVector = AppIcons.Menu,
                        contentDescription = if (railExpanded) "收起侧栏" else "展开侧栏",
                    )
                }
                if (expandedContentVisible) {
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "RedefineNCM",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentPalette.onQuietContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            tabs.forEach { item ->
                WideNavigationRailItem(
                    selected = selectedTab == item.dest,
                    onClick = { onSelectTab(item.dest) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(item.label) },
                    railExpanded = railExpanded,
                    modifier = railItemModifier,
                    colors = itemColors,
                )
            }
            // The tools stay reachable with the rail collapsed. They used to exist only in the
            // expanded rail, so downloads took two clicks and a scrim from any page.
            if (expandedContentVisible) {
                Text(
                    text = "工具",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentPalette.secondaryOnQuietContainer,
                    // In line with the item icons: an expanded item starts its pill 20dp in and
                    // its icon 16dp further.
                    modifier = Modifier.padding(start = 36.dp, top = 8.dp),
                )
            }
            WideNavigationRailItem(
                selected = downloadsSelected,
                onClick = onOpenDownloads,
                icon = { Icon(AppIcons.Download, contentDescription = null) },
                label = { Text("下载管理") },
                railExpanded = railExpanded,
                modifier = railItemModifier,
                colors = itemColors,
            )
            WideNavigationRailItem(
                selected = recognitionSelected,
                onClick = onOpenRecognition,
                icon = { Icon(AppIcons.Mic, contentDescription = null) },
                label = { Text("听歌识曲") },
                railExpanded = railExpanded,
                modifier = railItemModifier,
                colors = itemColors,
            )
        }
        if (showFullPlayer && expandedContentVisible) {
            Box(
                modifier = Modifier.width(320.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (showExpandedPlayerContent) {
                    DesktopNowPlayingStrip(
                        player = player,
                        accentPalette = accentPalette,
                        onAccentColor = onChromeAccent,
                        onOpenNowPlaying = onOpenNowPlaying,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopNowPlayingStrip(
    player: PlatformPlayer,
    accentPalette: ContentAccentPalette,
    onAccentColor: (Color) -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: NowPlayingViewModel = koinInject(),
) {
    val nowPlaying = rememberNowPlayingUiState(player, viewModel)
    val volume by player.volume.collectAsState()
    val media = nowPlaying.media
    val isPlaying = nowPlaying.isPlaying
    val playList = nowPlaying.playList
    val currentIndex = nowPlaying.currentIndex
    val shuffleEnabled = nowPlaying.shuffleEnabled
    val comments = nowPlaying.comments
    val commentsLoading = nowPlaying.commentsLoading
    val commentsLoadError = nowPlaying.commentsLoadError
    val commentsFromCache = nowPlaying.commentsFromCache
    val artwork = media?.artworkUri.orEmpty()
    val extractAccent = rememberThemeColorExtractor(artwork) { onAccentColor(it) }
    val hasMedia = nowPlaying.hasMedia
    val isFavorite = nowPlaying.isFavorite
    val safePosition = nowPlaying.safePosition
    val totalDuration = nowPlaying.totalDuration
    val progress = nowPlaying.progress
    val sheets = rememberTransportSheetsState()
    val seek = rememberSeekDragState(media?.id)
    val displayedProgress = seek.progressFor(progress)
    val displayedPosition = seek.positionFor(safePosition, totalDuration)

    Box(Modifier.fillMaxWidth()) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = accentPalette.container.copy(alpha = 0.92f),
            contentColor = accentPalette.onContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    onClick = onOpenNowPlaying,
                    enabled = hasMedia,
                    shape = MaterialTheme.shapes.large,
                    color = Color.Transparent,
                    contentColor = accentPalette.onContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = accentPalette.quietContainer,
                        contentColor = accentPalette.onQuietContainer,
                        modifier = Modifier.size(72.dp),
                    ) {
                        if (artwork.isNotBlank()) {
                            AsyncImage(
                                model = artwork,
                                contentDescription = "当前歌曲封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onSuccess = { state -> extractAccent(state.result.image) },
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(AppIcons.MusicNote, contentDescription = null, modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                        Column(Modifier.weight(1f)) {
                        Text(
                            text = media?.title?.takeIf { it.isNotBlank() } ?: "未播放",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = media?.artist?.takeIf { it.isNotBlank() } ?: "RedefineNCM",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentPalette.secondaryOnContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (hasMedia) {
                                "${formatPlaybackDuration(displayedPosition)} / ${formatPlaybackDuration(totalDuration)}"
                            } else {
                                "0:00 / 0:00"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = accentPalette.secondaryOnContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        }
                    }
                }

                PlaybackSeekBar(
                    state = seek,
                    progress = progress,
                    totalDuration = totalDuration,
                    enabled = hasMedia && totalDuration > 0L,
                    accentPalette = accentPalette,
                    onSeek = viewModel::onPositionSeekClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = when (outputVolumeLevel(volume)) {
                            OutputVolumeLevel.MUTED -> AppIcons.VolumeOff
                            OutputVolumeLevel.LOW -> AppIcons.VolumeDown
                            OutputVolumeLevel.HIGH -> AppIcons.VolumeUp
                        },
                        contentDescription = "音量",
                        tint = accentPalette.secondaryOnContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Slider(
                        value = volume.coerceIn(0f, 1f),
                        onValueChange = { player.setVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = accentPalette.onContainer,
                            activeTrackColor = accentPalette.onContainer,
                            inactiveTrackColor = accentPalette.onContainer.copy(alpha = 0.22f),
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconToggleButton(
                        checked = shuffleEnabled,
                        onCheckedChange = { viewModel.onShuffleClick(!shuffleEnabled) },
                        enabled = hasMedia,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = accentPalette.quietContainer,
                            contentColor = accentPalette.onQuietContainer,
                            checkedContainerColor = accentPalette.accent,
                            checkedContentColor = accentPalette.onAccent,
                            disabledContainerColor = accentPalette.quietContainer.copy(alpha = 0.44f),
                            disabledContentColor = accentPalette.onQuietContainer.copy(alpha = 0.38f),
                        ),
                    ) {
                        Icon(
                            imageVector = if (shuffleEnabled) AppIcons.ShuffleOn else AppIcons.Shuffle,
                            contentDescription = "随机播放",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { viewModel.onPervClick() },
                        enabled = hasMedia,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.size(48.dp),
                        colors = desktopSecondaryButtonColors(accentPalette),
                    ) {
                        Icon(
                            imageVector = AppIcons.SkipPrevious,
                            contentDescription = "上一首",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    FilledIconButton(
                        onClick = { viewModel.onPauseClick() },
                        enabled = hasMedia,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = accentPalette.accent,
                            contentColor = accentPalette.onAccent,
                            disabledContainerColor = accentPalette.quietContainer.copy(alpha = 0.44f),
                            disabledContentColor = accentPalette.onQuietContainer.copy(alpha = 0.38f),
                        ),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { viewModel.onNextClick() },
                        enabled = hasMedia,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.size(48.dp),
                        colors = desktopSecondaryButtonColors(accentPalette),
                    ) {
                        Icon(
                            imageVector = AppIcons.SkipNext,
                            contentDescription = "下一首",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            viewModel.onPlaylistClick()
                            sheets.openQueue()
                        },
                        enabled = hasMedia,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.size(48.dp),
                        colors = desktopSecondaryButtonColors(accentPalette),
                    ) {
                        Icon(
                            imageVector = AppIcons.QueueMusic,
                            contentDescription = "播放队列",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { viewModel.onFavClick() },
                        enabled = hasMedia,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        colors = if (isFavorite) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = accentPalette.accent,
                                contentColor = accentPalette.onAccent,
                                disabledContainerColor = accentPalette.quietContainer.copy(alpha = 0.44f),
                                disabledContentColor = accentPalette.onQuietContainer.copy(alpha = 0.38f),
                            )
                        } else {
                            desktopSecondaryButtonColors(accentPalette)
                        },
                    ) {
                        Icon(
                            imageVector = if (isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                            contentDescription = if (isFavorite) "已喜欢" else "喜欢",
                        )
                    }
                    FilledTonalIconButton(
                        onClick = sheets::openComments,
                        enabled = hasMedia,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        colors = desktopSecondaryButtonColors(accentPalette),
                    ) {
                        Icon(AppIcons.Comment, contentDescription = "评论")
                    }
                }
            }
        }

        TransportSheets(
            state = sheets,
            nowPlaying = nowPlaying,
            accentPalette = accentPalette,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun desktopSecondaryButtonColors(accentPalette: ContentAccentPalette) =
    IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        disabledContainerColor = accentPalette.quietContainer.copy(alpha = 0.44f),
        disabledContentColor = accentPalette.onQuietContainer.copy(alpha = 0.38f),
    )

private fun pageTransition(
    initial: RootDest,
    target: RootDest,
): ContentTransform =
    when {
        isPlayerSurface(initial) && isPlayerSurface(target) -> fadeThroughTransition()
        isPlayerSurface(initial) || isPlayerSurface(target) ->
            sheetTransition(showingSheet = isPlayerSurface(target))
        // Tabs are peers, not a sequence: a short fade-through, no travel and no scale.
        initial is RootDest.Tab && target is RootDest.Tab -> tabFadeThrough()
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
                initialOffsetY = { it },
            ) + pageFadeIn()
            ) togetherWith (
            fadeOut(animationSpec = tween(ExpressiveMotion.ShortMillis, easing = LinearOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
                )
            )
    } else {
        (
            fadeIn(
                animationSpec = tween(
                    ExpressiveMotion.ShortMillis,
                    delayMillis = ExpressiveMotion.EnterDelayMillis,
                    easing = LinearOutSlowInEasing,
                ),
            ) + scaleIn(
                initialScale = 0.98f,
                animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
            )
            ) togetherWith (
            slideOutVertically(
                animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ) + fadeOut(
                animationSpec = tween(ExpressiveMotion.QuickMillis, easing = LinearOutSlowInEasing),
            )
            )
    }

private fun fadeThroughTransition(): ContentTransform =
        (pageFadeIn(delayMillis = ExpressiveMotion.StaggerDelayMillis) + pageScaleIn()) togetherWith
        (pageFadeOut() + pageScaleOut())

private fun pageFadeIn(delayMillis: Int = ExpressiveMotion.EnterDelayMillis): EnterTransition =
    fadeIn(
        animationSpec = tween(
            ExpressiveMotion.ShortMillis,
            delayMillis = delayMillis,
            easing = LinearOutSlowInEasing,
        ),
    )

private fun pageFadeOut(): ExitTransition =
    fadeOut(animationSpec = tween(ExpressiveMotion.FastMillis, easing = LinearOutSlowInEasing))

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
    ) + fadeIn(animationSpec = tween(ExpressiveMotion.ShortMillis, easing = LinearOutSlowInEasing))

private fun bottomNavExitTransition(): ExitTransition =
    slideOutVertically(
        animationSpec = tween(ExpressiveMotion.StandardMillis, easing = FastOutSlowInEasing),
        targetOffsetY = { it },
    ) + fadeOut(animationSpec = tween(ExpressiveMotion.FastMillis, easing = LinearOutSlowInEasing))

private fun railEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(PageTransitionMillis, easing = FastOutSlowInEasing),
        initialOffsetX = { -it },
    ) + fadeIn(animationSpec = tween(ExpressiveMotion.ShortMillis, easing = LinearOutSlowInEasing))

private fun railExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(ExpressiveMotion.StandardMillis, easing = FastOutSlowInEasing),
        targetOffsetX = { -it },
    ) + fadeOut(animationSpec = tween(ExpressiveMotion.FastMillis, easing = LinearOutSlowInEasing))

private fun miniPlayerEnterTransition(): EnterTransition =
    scaleIn(
        initialScale = 0.88f,
        animationSpec = tween(ExpressiveMotion.MediumMillis, easing = FastOutSlowInEasing),
    ) + fadeIn(animationSpec = tween(ExpressiveMotion.QuickMillis, easing = LinearOutSlowInEasing))

private fun miniPlayerExitTransition(): ExitTransition =
    scaleOut(
        targetScale = 0.88f,
        animationSpec = tween(ExpressiveMotion.ShortMillis, easing = FastOutSlowInEasing),
    ) + fadeOut(animationSpec = tween(ExpressiveMotion.FastMillis, easing = LinearOutSlowInEasing))

private fun isPlayerSurface(dest: RootDest): Boolean =
    isPlayerSurface((dest as? RootDest.Pushed)?.dest)

/** The Now Playing page and the lyric page it opens; both hide the mini player. */
private fun isPlayerSurface(dest: PushedDest?): Boolean =
    dest is PushedDest.NowPlaying || dest is PushedDest.FullLyric

private fun tabFadeThrough(): ContentTransform =
    fadeIn(
        animationSpec = tween(
            ExpressiveMotion.QuickMillis,
            delayMillis = ExpressiveMotion.EnterDelayMillis,
            easing = LinearOutSlowInEasing,
        ),
    ) togetherWith fadeOut(
        animationSpec = tween(ExpressiveMotion.EnterDelayMillis + 30, easing = LinearOutSlowInEasing),
    )

private fun RootDest.stateKey(): String = when (this) {
    is RootDest.Tab -> "tab:" + when (tab) {
        is TabDest.Home -> "home"
        is TabDest.My -> "my"
        is TabDest.Settings -> "settings"
    }
    is RootDest.Pushed -> pushedStateKey(dest, stackDepth)
}

private fun pushedStateKey(dest: PushedDest, depth: Int): String =
    "push:$depth:${encodePushedDestination(dest)}"

/** FAB-slot mini player (60dp + its 2dp inset) plus the Scaffold's 16dp FAB margin and air. */
private val MiniPlayerClearance = 88.dp

private const val PageTransitionMillis = ExpressiveMotion.LongMillis
