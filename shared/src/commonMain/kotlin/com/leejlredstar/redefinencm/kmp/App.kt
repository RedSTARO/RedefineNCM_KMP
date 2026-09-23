package com.leejlredstar.redefinencm.kmp

import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.theme.ArtworkTheme
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
import androidx.compose.foundation.verticalScroll
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.lyric.AmllPlayerScreen
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.rememberNowPlayingUiState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveMotion
import com.leejlredstar.redefinencm.kmp.ui.component.MiniNowPlayingBar
import com.leejlredstar.redefinencm.kmp.ui.component.TransportSheets
import com.leejlredstar.redefinencm.kmp.ui.component.TransportSheetsState
import com.leejlredstar.redefinencm.kmp.ui.component.DesktopPlayerBar
import com.leejlredstar.redefinencm.kmp.ui.component.rememberTransportSheetsState
import com.leejlredstar.redefinencm.kmp.ui.screen.AlbumScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.ArtistScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.DailySongsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.DownloadManagementScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.HomeScreen
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderRegistrations
import com.leejlredstar.redefinencm.kmp.ui.screen.AccountsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LocalLibraryScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LocalPlaylistScreen
import com.leejlredstar.redefinencm.kmp.ui.component.AddToLocalPlaylistDialog
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import com.leejlredstar.redefinencm.kmp.ui.screen.LoginScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.PlaylistDetailScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SearchScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.NowPlayingScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SettingsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SongRecognitionScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.UserPlaylistScreen
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemePreferences
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.BackHandler
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The three tabs: recommendations, search and the library. Settings is a page, opened from the
 * library and from the foot of the desktop sidebar.
 */
private sealed interface TabDest {
    /** Left to right along the bar; the page transition travels the same way. */
    val order: Int

    data object Home : TabDest {
        override val order: Int = 0
    }

    data object Search : TabDest {
        override val order: Int = 1
    }

    data object My : TabDest {
        override val order: Int = 2
    }
}

internal sealed interface PushedDest {
    /** The login page for one provider; NetEase's is the one that opens on first launch. */
    data class Login(val provider: MusicProviderId = MusicProviderId.NETEASE) : PushedDest
    data object NowPlaying : PushedDest
    data object FullLyric : PushedDest
    data object Downloads : PushedDest
    data object SongRecognition : PushedDest
    data object DailySongs : PushedDest
    data object Settings : PushedDest
    /** Every account the app holds, opened from settings. */
    data object Accounts : PushedDest
    /** The local account's playlists and favourites. */
    data object LocalLibrary : PushedDest
    data class LocalPlaylist(val id: String) : PushedDest
    data class Playlist(val id: Long) : PushedDest
    data class Artist(val id: Long) : PushedDest
    data class Album(val id: Long) : PushedDest
}

private val tabDestSaver = Saver<TabDest, String>(
    save = { destination ->
        when (destination) {
            TabDest.Home -> "home"
            TabDest.Search -> "search"
            TabDest.My -> "my"
        }
    },
    restore = { saved ->
        when (saved) {
            "search" -> TabDest.Search
            // Settings was a tab; state saved then comes back to the library it now hangs off.
            "my", "settings" -> TabDest.My
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

internal fun encodePushedDestination(destination: PushedDest): String = when (destination) {
    // NetEase's login keeps the bare form so navigation state saved before providers existed
    // still restores.
    is PushedDest.Login -> if (destination.provider == MusicProviderId.NETEASE) {
        "login"
    } else {
        "login:${destination.provider.key}"
    }
    PushedDest.NowPlaying -> "now-playing"
    PushedDest.FullLyric -> "full-lyric"
    PushedDest.Downloads -> "downloads"
    PushedDest.SongRecognition -> "song-recognition"
    PushedDest.DailySongs -> "daily-songs"
    PushedDest.Settings -> "settings"
    PushedDest.Accounts -> "accounts"
    PushedDest.LocalLibrary -> "local-library"
    is PushedDest.LocalPlaylist -> "local-playlist:${destination.id}"
    is PushedDest.Playlist -> "playlist:${destination.id}"
    is PushedDest.Artist -> "artist:${destination.id}"
    is PushedDest.Album -> "album:${destination.id}"
}

internal fun decodePushedDestination(saved: String): PushedDest? = when (saved) {
    "login" -> PushedDest.Login()
    // Migrate navigation state saved before the legacy KMP player was removed.
    "now-playing" -> PushedDest.NowPlaying
    "full-lyric" -> PushedDest.FullLyric
    "downloads" -> PushedDest.Downloads
    "song-recognition" -> PushedDest.SongRecognition
    "daily-songs" -> PushedDest.DailySongs
    "settings" -> PushedDest.Settings
    "accounts" -> PushedDest.Accounts
    "local-library" -> PushedDest.LocalLibrary
    else -> when {
        saved.startsWith("local-playlist:") ->
            saved.removePrefix("local-playlist:").takeIf(String::isNotBlank)?.let(PushedDest::LocalPlaylist)
        saved.startsWith("login:") ->
            MusicProviderId.fromKey(saved.removePrefix("login:"))?.let(PushedDest::Login)
        saved.startsWith("playlist:") -> saved.removePrefix("playlist:").toLongOrNull()?.let(PushedDest::Playlist)
        saved.startsWith("artist:") -> saved.removePrefix("artist:").toLongOrNull()?.let(PushedDest::Artist)
        saved.startsWith("album:") -> saved.removePrefix("album:").toLongOrNull()?.let(PushedDest::Album)
        else -> null
    }
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
}

internal fun desktopLayoutMode(width: Dp, height: Dp): DesktopLayoutMode = when {
    width < 600.dp || height < 480.dp -> DesktopLayoutMode.Compact
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
    val registrations: ProviderRegistrations = koinInject()
    var startsSignedIn by remember(settings) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(settings) {
        ThemePreferences.loadStored(settings)
        I18n.loadStored(settings)
        startsSignedIn = registrations.anySignedIn(settings)
    }

    // Before the first themed frame, so a stored light or dark choice (or a stored language)
    // never flashes the system's.
    remember(settings) {
        ThemePreferences.load(settings)
        I18n.load(settings)
    }

    RedefineNCMTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            startsSignedIn?.let { signedIn ->
                AppContent(settings = settings, startsSignedIn = signedIn)
            }
        }
    }
}

/**
 * Expressive navigation affordance for the floating toolbar.
 *
 * Uses [ToggleButton] instead of a plain icon button so selection carries Material's own shape
 * morph: the silhouette relaxes between round and squared as the item becomes checked. The morph
 * takes the place of the pill indicator a `NavigationBarItem` draws behind the icon, and the
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
    /** Whether any switched-on provider held an account at launch. */
    startsSignedIn: Boolean,
) {
            val mainViewModel: MainViewModel = koinInject()
            val nowPlayingViewModel: NowPlayingViewModel = koinInject()
            val player: PlatformPlayer = koinInject()
            val desktopSheets = rememberTransportSheetsState()
            val currentMedia by player.currentMedia.collectAsState()
            val chromeAccentSource = currentMedia?.artworkUri
            val defaultChromeAccent = MaterialTheme.colorScheme.primaryContainer
            // Keyed on the artwork only. Keying on the theme's default as well would make a
            // light/dark switch throw away the colour taken from the cover until the next song.
            var rawChromeAccent by remember(chromeAccentSource) { mutableStateOf<Color?>(null) }
            val chromeAccent by animateColorAsState(
                targetValue = rawChromeAccent ?: defaultChromeAccent,
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
                    // 原版 SplashActivity：无 cookie 时先进登录页。With more than one provider
                    // that means no account anywhere: someone signed in to QQ only is not sent to
                    // NetEase's login page at every launch (AGENTS.md D6, 2026-09-23 decision 3).
                    if (!startsSignedIn) add(PushedDest.Login())
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
            fun selectTab(tab: TabDest) {
                clearPushed()
                currentTab = tab
            }
            var searchFocusRequest by remember { mutableStateOf(0) }
            fun openSearch() {
                selectTab(TabDest.Search)
                searchFocusRequest += 1
            }
            LaunchedEffect(Unit) {
                AppNavigationRequests.openSearchRequestId.collect { requestId ->
                    if (AppNavigationRequests.consumeOpenSearchRequest(requestId)) openSearch()
                }
            }
            // Song menus and the player ask for artist and album pages from wherever they are.
            LaunchedEffect(Unit) {
                AppNavigationRequests.openArtistRequest.collect { request ->
                    if (AppNavigationRequests.consumeOpenArtistRequest(request)) {
                        focusOrPushTracked(PushedDest.Artist(request!!.id))
                    }
                }
            }
            LaunchedEffect(Unit) {
                AppNavigationRequests.openAlbumRequest.collect { request ->
                    if (AppNavigationRequests.consumeOpenAlbumRequest(request)) {
                        focusOrPushTracked(PushedDest.Album(request!!.id))
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

            // The local library answers from wherever it was asked (a song menu on any page),
            // so its messages use the app's snackbar, and its "add to a playlist" dialog is
            // hosted here.
            val localLibraryViewModel: LocalLibraryViewModel = koinInject()
            val localLibraryMessage by localLibraryViewModel.message.collectAsState()
            LaunchedEffect(localLibraryMessage) {
                val message = localLibraryMessage ?: return@LaunchedEffect
                desktopSnackbarVisible = true
                try {
                    snackbarHostState.showSnackbar(message)
                } finally {
                    desktopSnackbarVisible = false
                }
                localLibraryViewModel.consumeMessage()
            }
            AddToLocalPlaylistDialog(localLibraryViewModel)

            // Say why a track resolved to nothing instead of leaving the player silent. The
            // reason comes from the provider that failed, once per failure.
            val playbackFailure by nowPlayingViewModel.playbackFailure.collectAsState()
            LaunchedEffect(playbackFailure?.sequence) {
                val failure = playbackFailure ?: return@LaunchedEffect
                val title = player.currentMedia.value
                    ?.takeIf { it.id == failure.mediaId }
                    ?.title
                    ?.takeIf(String::isNotBlank)
                    ?: strings.thisSong
                // The other provider's copy is offered, never taken without asking.
                val offerSwitch = nowPlayingViewModel.canOfferSourceSwitch(failure)
                desktopSnackbarVisible = true
                val result = try {
                    snackbarHostState.showSnackbar(
                        message = strings.playbackFailed(title, failure.message),
                        actionLabel = if (offerSwitch) strings.switchSource else null,
                        duration = if (offerSwitch) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                } finally {
                    desktopSnackbarVisible = false
                }
                if (result == SnackbarResult.ActionPerformed) nowPlayingViewModel.switchSourceForCurrent()
            }
            val sourceSwitchMessage by nowPlayingViewModel.sourceSwitchMessage.collectAsState()
            LaunchedEffect(sourceSwitchMessage) {
                val message = sourceSwitchMessage ?: return@LaunchedEffect
                desktopSnackbarVisible = true
                try {
                    snackbarHostState.showSnackbar(message)
                } finally {
                    desktopSnackbarVisible = false
                }
                nowPlayingViewModel.consumeSourceSwitchMessage()
            }

            // The navigation stays on the pages opened from a tab, so changing tabs never means
            // backing out first. The player, the lyrics and sign-in are full-screen and hide it.
            val showTabs = !hidesNavigation(pushedStack.lastOrNull())
            val tabs = remember(I18n.language) {
                listOf(
                    NavigationItem(strings.forYou, AppIcons.Home, TabDest.Home),
                    NavigationItem(strings.search, AppIcons.Search, TabDest.Search),
                    NavigationItem(strings.me, AppIcons.Person, TabDest.My),
                )
            }

            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val appContentWidth = maxWidth
                    val appContentHeight = maxHeight
                    val desktopMode = desktopLayoutMode(maxWidth, maxHeight)
                    val desktopCompact = platform.isDesktop && desktopMode == DesktopLayoutMode.Compact
                    val showDesktopRail = platform.isDesktop && !desktopCompact
                    val isWide = maxWidth >= 600.dp
                    // With the sidebar there is room for a full playback bar along the bottom; the
                    // corner pill is for the narrow layouts. Neither shows over the player itself.
                    val showDesktopBar = showDesktopRail &&
                        currentMedia != null &&
                        pushedStack.lastOrNull().let { !isPlayerSurface(it) }
                    val showMiniPlayer = !showDesktopBar &&
                        maxWidth >= 160.dp &&
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
                    // contentWindowInsets = 0 and a floating toolbar carries no insets of its own;
                    // without it the pill sits under the system gesture bar on phones.
                    val systemNavInset = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding()
                    val contentBottomInset = systemNavInset + if (bottomNavVisible) {
                        ExpressiveLayout.FloatingNavClearance
                    } else {
                        0.dp
                    }
                    // One bottom clearance for every page, search results included: the
                    // floating toolbar (when shown) plus the mini player that floats above it.
                    // Pages do not add their own trailing spacers on top of this, which would
                    // leave a dead band at the end of the list.
                    val miniPlayerClearance = if (showMiniPlayer) MiniPlayerClearance else 0.dp
                    val screenPadding = PaddingValues(bottom = contentBottomInset + miniPlayerClearance)
                    val toolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
                        exitDirection = FloatingToolbarExitDirection.Bottom,
                    )
                    // The toolbar's scrolled-away state belongs to the page that scrolled it.
                    // Without this reset a page reached by navigation would open with no
                    // navigation bar until the user happened to scroll.
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
                                // Scaffold does not reserve the toolbar's height, so the FAB
                                // has to step over the floating pill itself.
                                Box(
                                    Modifier
                                        .padding(bottom = contentBottomInset)
                                        .graphicsLayer {
                                            // Follow the toolbar down as it scrolls away, so no
                                            // empty band is left where it was.
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
                                        rootDest is RootDest.Pushed && isToolPage(rootDest.dest)
                                    ) {
                                        null
                                    } else {
                                        currentTab
                                    },
                                    downloadsSelected = rootDest is RootDest.Pushed &&
                                        rootDest.dest is PushedDest.Downloads,
                                    accentPalette = chromePalette,
                                    onSelectTab = ::selectTab,
                                    onOpenDownloads = ::openDownloads,
                                    recognitionSelected = rootDest is RootDest.Pushed &&
                                        rootDest.dest is PushedDest.SongRecognition,
                                    onOpenRecognition = {
                                        focusOrPushTracked(PushedDest.SongRecognition)
                                    },
                                    settingsSelected = rootDest is RootDest.Pushed &&
                                        rootDest.dest is PushedDest.Settings,
                                    onOpenSettings = { focusOrPushTracked(PushedDest.Settings) },
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
                                                onClick = { selectTab(item.dest) },
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
                            Box(Modifier.weight(1f).fillMaxSize()) {
                            Column(Modifier.fillMaxSize()) {
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
                                            is PushedDest.Login -> LoginScreen(
                                                onBack = ::back,
                                                provider = dest.provider,
                                            )
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
                                            is PushedDest.Settings -> SettingsScreen(
                                                scaffoldPadding = screenPadding,
                                                onOpenAccounts = { push(PushedDest.Accounts) },
                                                onBack = ::back,
                                            )
                                            is PushedDest.Accounts -> AccountsScreen(
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                                onOpenLogin = { push(PushedDest.Login(it)) },
                                            )
                                            is PushedDest.LocalLibrary -> LocalLibraryScreen(
                                                onBack = ::back,
                                                onOpenPlaylist = { push(PushedDest.LocalPlaylist(it)) },
                                                scaffoldPadding = screenPadding,
                                            )
                                            is PushedDest.LocalPlaylist -> LocalPlaylistScreen(
                                                playlistId = dest.id,
                                                onBack = ::back,
                                                scaffoldPadding = screenPadding,
                                            )
                                            is PushedDest.Artist -> ArtistScreen(
                                                artistId = dest.id,
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                                onOpenAlbum = { focusOrPushTracked(PushedDest.Album(it)) },
                                            )
                                            is PushedDest.Album -> AlbumScreen(
                                                albumId = dest.id,
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                                onOpenArtist = { focusOrPushTracked(PushedDest.Artist(it)) },
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
                                                onOpenSearch = ::openSearch,
                                            )
                                            is TabDest.Search -> SearchScreen(
                                                bottomPadding = screenPadding.calculateBottomPadding() + 16.dp,
                                                focusRequest = searchFocusRequest,
                                                onFocusRequestHandled = { searchFocusRequest = 0 },
                                            )
                                            is TabDest.My -> UserPlaylistScreen(
                                                scaffoldPadding = screenPadding,
                                                onOpenPlaylist = { push(PushedDest.Playlist(it)) },
                                                onOpenLogin = { push(PushedDest.Login()) },
                                                onOpenDownloads = ::openDownloads,
                                                onOpenSettings = { push(PushedDest.Settings) },
                                                onOpenLocalLibrary = { push(PushedDest.LocalLibrary) },
                                            )
                                        }
                                    }
                                    }
                                }
                                if (showDesktopBar) {
                                    DesktopPlayerBar(
                                        player = player,
                                        viewModel = nowPlayingViewModel,
                                        accentPalette = chromePalette,
                                        sheets = desktopSheets,
                                        onAccentColor = { rawChromeAccent = it },
                                        onOpenNowPlaying = ::openNowPlaying,
                                        onOpenLyrics = ::openFullLyric,
                                    )
                                }
                            }
                            if (showDesktopRail) {
                                DesktopTransportSheetsHost(
                                    sheets = desktopSheets,
                                    player = player,
                                    viewModel = nowPlayingViewModel,
                                    accentPalette = chromePalette,
                                )
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
                                            onSelect = { selectTab(item.dest) },
                                        )
                                    }
                                    if (desktopCompact) {
                                        ExpressiveNavToggle(
                                            label = strings.downloads,
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
    onSelectTab: (TabDest) -> Unit,
    onOpenDownloads: () -> Unit,
    recognitionSelected: Boolean,
    onOpenRecognition: () -> Unit,
    settingsSelected: Boolean,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val railExpanded = state.targetValue == WideNavigationRailValue.Expanded
    val modalExpansionActive = state.isAnimating ||
        state.currentValue == WideNavigationRailValue.Expanded ||
        state.targetValue == WideNavigationRailValue.Expanded
    val railColors = WideNavigationRailDefaults.colors(
        containerColor = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modalContainerColor = accentPalette.quietContainer,
        modalScrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        modalContentColor = accentPalette.onQuietContainer,
    )

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
            onToggle = ::toggleRail,
            onSelectTab = { collapseAfter { onSelectTab(it) } },
            onOpenDownloads = { collapseAfter(onOpenDownloads) },
            recognitionSelected = recognitionSelected,
            onOpenRecognition = { collapseAfter(onOpenRecognition) },
            settingsSelected = settingsSelected,
            onOpenSettings = { collapseAfter(onOpenSettings) },
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
    onToggle: () -> Unit,
    onSelectTab: (TabDest) -> Unit,
    onOpenDownloads: () -> Unit,
    recognitionSelected: Boolean,
    onOpenRecognition: () -> Unit,
    settingsSelected: Boolean,
    onOpenSettings: () -> Unit,
) {
    val itemColors = WideNavigationRailItemDefaults.colors(
        selectedIconColor = accentPalette.onContainer,
        selectedTextColor = accentPalette.onContainer,
        selectedIndicatorColor = accentPalette.container,
        unselectedIconColor = accentPalette.secondaryOnQuietContainer,
        unselectedTextColor = accentPalette.secondaryOnQuietContainer,
    )
    // Expanded, an item is a pill as wide as its label; filling the width would centre it, out
    // of line with the app name and the section title above. Collapsed, it centres in the rail.
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
                        stateDescription = if (railExpanded) strings.sidebarExpanded else strings.sidebarCollapsed
                    },
                ) {
                    Icon(
                        imageVector = AppIcons.Menu,
                        contentDescription = if (railExpanded) strings.collapseSidebar else strings.expandSidebar,
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
            // The tools stay reachable with the rail collapsed, so downloads is one click from
            // any page instead of two clicks and a scrim.
            if (expandedContentVisible) {
                Text(
                    text = strings.tools,
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
                label = { Text(strings.downloadManagement) },
                railExpanded = railExpanded,
                modifier = railItemModifier,
                colors = itemColors,
            )
            WideNavigationRailItem(
                selected = recognitionSelected,
                onClick = onOpenRecognition,
                icon = { Icon(AppIcons.Mic, contentDescription = null) },
                label = { Text(strings.songRecognition) },
                railExpanded = railExpanded,
                modifier = railItemModifier,
                colors = itemColors,
            )
        }
        // Settings stand apart from the places, at the foot of the rail.
        WideNavigationRailItem(
            selected = settingsSelected,
            onClick = onOpenSettings,
            icon = { Icon(AppIcons.Settings, contentDescription = null) },
            label = { Text(strings.settings) },
            railExpanded = railExpanded,
            modifier = railItemModifier.padding(vertical = 8.dp),
            colors = itemColors,
        )
    }
}

/**
 * The queue and comment panels the desktop playback bar opens, over the pages and the bar.
 * Separate from the bar so the panels can cover more than the bar's own 80dp.
 */
@Composable
private fun DesktopTransportSheetsHost(
    sheets: TransportSheetsState,
    player: PlatformPlayer,
    viewModel: NowPlayingViewModel,
    accentPalette: ContentAccentPalette,
) {
    val nowPlaying = rememberNowPlayingUiState(player, viewModel)
    TransportSheets(
        state = sheets,
        nowPlaying = nowPlaying,
        accentPalette = accentPalette,
        viewModel = viewModel,
    )
}

private fun pageTransition(
    initial: RootDest,
    target: RootDest,
): ContentTransform =
    when {
        isPlayerSurface(initial) && isPlayerSurface(target) -> fadeThroughTransition()
        isPlayerSurface(initial) || isPlayerSurface(target) ->
            sheetTransition(showingSheet = isPlayerSurface(target))
        initial is RootDest.Tab && target is RootDest.Tab ->
            tabSharedAxis(forward = target.tab.order > initial.tab.order)
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

/** Full-screen pages, which cover the tab navigation; every other page keeps it. */
private fun hidesNavigation(dest: PushedDest?): Boolean =
    isPlayerSurface(dest) || dest is PushedDest.Login

/** The pages the desktop sidebar lists by themselves; while one is open no tab is lit. */
private fun isToolPage(dest: PushedDest): Boolean =
    dest is PushedDest.Downloads || dest is PushedDest.SongRecognition || dest is PushedDest.Settings

/**
 * Tabs travel along the bar: going right brings the next page in from the right and pushes the
 * one leaving out to the left, and going back reverses it.
 *
 * A plain cross-fade would treat the tabs as peers with no order. That is true of the pages but
 * not of the bar, where they sit in a fixed order the reader can see. A fade also makes every
 * switch look the same, so nothing says which way you moved.
 *
 * Shared axis X: a twelfth of the width, not a page-width push, because these are still peers
 * rather than a stack. The fade carries the change; the travel only gives it a direction.
 */
private fun tabSharedAxis(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(
            animationSpec = tween(ExpressiveMotion.MediumMillis, easing = FastOutSlowInEasing),
            initialOffsetX = { direction * it / 12 },
        ) + fadeIn(
            animationSpec = tween(
                ExpressiveMotion.ShortMillis,
                delayMillis = ExpressiveMotion.EnterDelayMillis,
                easing = LinearOutSlowInEasing,
            ),
        )
        ) togetherWith (
        slideOutHorizontally(
            animationSpec = tween(ExpressiveMotion.MediumMillis, easing = FastOutSlowInEasing),
            targetOffsetX = { -direction * it / 12 },
        ) + fadeOut(
            animationSpec = tween(ExpressiveMotion.QuickMillis, easing = LinearOutSlowInEasing),
        )
        )
}

private fun RootDest.stateKey(): String = when (this) {
    is RootDest.Tab -> "tab:" + when (tab) {
        is TabDest.Home -> "home"
        is TabDest.Search -> "search"
        is TabDest.My -> "my"
    }
    is RootDest.Pushed -> pushedStateKey(dest, stackDepth)
}

private fun pushedStateKey(dest: PushedDest, depth: Int): String =
    "push:$depth:${encodePushedDestination(dest)}"

/** FAB-slot mini player (60dp + its 2dp inset) plus the Scaffold's 16dp FAB margin and air. */
private val MiniPlayerClearance = 88.dp

private const val PageTransitionMillis = ExpressiveMotion.LongMillis
