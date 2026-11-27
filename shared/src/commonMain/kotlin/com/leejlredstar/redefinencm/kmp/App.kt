package com.leejlredstar.redefinencm.kmp

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import com.leejlredstar.redefinencm.kmp.notification.PageOverlayLyricSurface
import com.leejlredstar.redefinencm.kmp.notification.lyricSurface
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.theme.ArtworkTheme
import com.leejlredstar.redefinencm.kmp.util.StartupNotices
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.lyric.AmllPlayerScreen
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.adaptive.AndroidNavigationLayout
import com.leejlredstar.redefinencm.kmp.ui.adaptive.AndroidNavigationTarget
import com.leejlredstar.redefinencm.kmp.ui.adaptive.CompactAppBarHeightThreshold
import com.leejlredstar.redefinencm.kmp.ui.adaptive.FoldSafeBrowseContent
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalBrowseObstructions
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalCompactAppBar
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalSoftKeyboardVisible
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalWindowFold
import com.leejlredstar.redefinencm.kmp.ui.adaptive.androidBrowseObstructions
import com.leejlredstar.redefinencm.kmp.ui.adaptive.androidMiniPlayerFabSlot
import com.leejlredstar.redefinencm.kmp.ui.adaptive.androidNavigationLayout
import com.leejlredstar.redefinencm.kmp.ui.adaptive.compactNavReflow
import com.leejlredstar.redefinencm.kmp.ui.adaptive.rememberAndroidNavigationMotion
import com.leejlredstar.redefinencm.kmp.ui.component.LocalAndroidReflowWindow
import com.leejlredstar.redefinencm.kmp.ui.component.ReflowEntry
import com.leejlredstar.redefinencm.kmp.ui.component.ReflowProgress
import com.leejlredstar.redefinencm.kmp.ui.component.reflowPlacement
import com.leejlredstar.redefinencm.kmp.ui.component.playbackSafeContent
import com.leejlredstar.redefinencm.kmp.ui.component.rememberNowPlayingUiState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveMotion
import com.leejlredstar.redefinencm.kmp.ui.component.MiniNowPlayingBar
import com.leejlredstar.redefinencm.kmp.ui.component.TransportSheets
import com.leejlredstar.redefinencm.kmp.ui.component.TransportSheetsState
import com.leejlredstar.redefinencm.kmp.ui.component.DesktopPlayerBar
import com.leejlredstar.redefinencm.kmp.ui.component.DesktopPlayerBarHeight
import com.leejlredstar.redefinencm.kmp.ui.component.rememberTransportSheetsState
import com.leejlredstar.redefinencm.kmp.ui.screen.AlbumScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.ArtistScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.DailySongsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.DownloadManagementScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.HomeScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LicensesScreen
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderRegistrations
import com.leejlredstar.redefinencm.kmp.ui.screen.AccountsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LocalLibraryScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LocalPlaylistScreen
import com.leejlredstar.redefinencm.kmp.ui.component.AddToLocalPlaylistDialog
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import com.leejlredstar.redefinencm.kmp.data.local.LocalAccount
import com.leejlredstar.redefinencm.kmp.ui.screen.LoginScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.PlaylistDetailScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SearchScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.NowPlayingScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SettingsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SongRecognitionScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.UserPlaylistScreen
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.LocalSystemBarBackgrounds
import com.leejlredstar.redefinencm.kmp.ui.theme.PlaybackPresentationTheme
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme
import com.leejlredstar.redefinencm.kmp.ui.theme.SystemBarBackgrounds
import com.leejlredstar.redefinencm.kmp.ui.theme.SystemBarIcons
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
private enum class TabDest { Home, Search, My }

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
    /** The third-party notices and license texts, opened from settings. */
    data object Licenses : PushedDest
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

// listSaver omits an empty list, but an empty restored stack must not run the startup login again.
internal val pushedStackSaver = Saver<SnapshotStateList<PushedDest>, ArrayList<String>>(
    save = { stack -> stack.mapTo(ArrayList(stack.size), ::encodePushedDestination) },
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
    PushedDest.Licenses -> "licenses"
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
    "licenses" -> PushedDest.Licenses
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

/** How many pages of the local library lie on top of [stack], counted down from the top. */
internal fun localLibraryPagesOnTop(stack: List<PushedDest>): Int =
    stack.asReversed()
        .takeWhile { it is PushedDest.LocalLibrary || it is PushedDest.LocalPlaylist }
        .size

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
        val barBackgrounds = remember { SystemBarBackgrounds() }
        SystemBarIcons(
            darkBackground = barBackgrounds.darkest ?: (MaterialTheme.colorScheme.surface.luminance() < 0.5f),
        )
        CompositionLocalProvider(LocalSystemBarBackgrounds provides barBackgrounds) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                startsSignedIn?.let { signedIn ->
                    AppContent(settings = settings, startsSignedIn = signedIn)
                }
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
 * labels stay visible when they fit; selection supplies the label on narrower touch layouts.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveNavToggle(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    showLabel: Boolean,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // A ToggleButton says "checkbox, not checked" to a screen reader, and picking the current
    // tab sounded like unchecking it. The button keeps its look and morph; what is announced is
    // a tab, selected or not, named by its label.
    Box(
        Modifier.navigationTabSemantics(label, selected, onSelect),
    ) {
        ToggleButton(
            checked = selected,
            onCheckedChange = { onSelect() },
            shapes = ToggleButtonDefaults.shapes(),
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = Color.Transparent,
                contentColor = scheme.onSurfaceVariant,
                checkedContainerColor = scheme.secondaryContainer,
                checkedContentColor = scheme.onSecondaryContainer,
            ),
        ) {
            Icon(icon, contentDescription = null)
            if (showLabel) {
                Text(
                    text = label,
                    style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun Modifier.navigationTabSemantics(label: String, selected: Boolean, onSelect: () -> Unit): Modifier =
    clearAndSetSemantics {
        role = Role.Tab
        this.selected = selected
        contentDescription = label
        onClick {
            onSelect()
            true
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
            val reducedMotion = rememberReducedMotionEnabled()
            val scheme = MaterialTheme.colorScheme
            val motion = MaterialTheme.motionScheme
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val currentMedia by player.currentMedia.collectAsState()
            val chromeAccentSource = currentMedia?.artworkUri
            val defaultChromeAccent = MaterialTheme.colorScheme.primaryContainer
            // Keyed on the artwork only. Keying on the theme's default as well would make a
            // light/dark switch throw away the colour taken from the cover until the next song.
            var rawChromeAccent by remember(chromeAccentSource) { mutableStateOf<Color?>(null) }
            val chromeAccent by animateColorAsState(
                targetValue = rawChromeAccent ?: defaultChromeAccent,
                animationSpec = if (reducedMotion) snap() else motion.defaultEffectsSpec(),
                label = "appChromeAccent",
            )
            val chromePalette = contentAccentPalette(chromeAccent)
            // The cover's colour is the whole app's, not only the navigation chrome's: the scheme
            // is generated from it, so settings, dialogs and switches follow the song as well.
            val hasMedia = currentMedia != null
            LaunchedEffect(rawChromeAccent, hasMedia) {
                ArtworkTheme.follow(rawChromeAccent, hasMedia)
            }
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
            // A second tap that lands while the first page is still sliding in would stack the
            // same page twice, and Back would have to be pressed twice to leave it.
            fun push(dest: PushedDest) {
                if (pushedStack.lastOrNull() != dest) pushedStack.add(dest)
            }
            // Null until the local account's stored switch has been read. Only a value read from
            // settings may drop pages; the default would empty a restored stack at every launch.
            val localAccount: LocalAccount = koinInject()
            val localAccountEnabled by remember(localAccount) { localAccount.enabledUpdates() }
                .collectAsState(initial = null)
            // Pages of the local library left beneath the page that switched the local account
            // off are passed over instead of returned to. They are not removed when the switch
            // flips: the pages above them would move to another depth, which resets their state
            // and replays the page transition on screen.
            fun dropSwitchedOffLocalPages() {
                if (localAccountEnabled != false) return
                repeat(localLibraryPagesOnTop(pushedStack)) {
                    forgetPushed(fromDepth = pushedStack.size)
                    pushedStack.removeAt(pushedStack.lastIndex)
                }
            }
            fun back() {
                if (pushedStack.isEmpty()) return
                forgetPushed(fromDepth = pushedStack.size)
                pushedStack.removeAt(pushedStack.lastIndex)
                dropSwitchedOffLocalPages()
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
            // The lyric page stands on Now Playing wherever it is opened from, so back from it
            // returns to Now Playing, as from every other way in.
            fun openFullLyricOverNowPlaying() {
                openNowPlaying()
                openFullLyric()
            }

            BackHandler(enabled = pushedStack.isNotEmpty()) { back() }
            // A saved stack restored with a local page on top while the account is switched off.
            LaunchedEffect(localAccountEnabled) { dropSwitchedOffLocalPages() }

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
            var searchFocusRequest by rememberSaveable { mutableStateOf(0) }
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

            val favoriteMessage by nowPlayingViewModel.favoriteMessage.collectAsState()
            LaunchedEffect(favoriteMessage) {
                val message = favoriteMessage ?: return@LaunchedEffect
                desktopSnackbarVisible = true
                try { snackbarHostState.showSnackbar(message) }
                finally { desktopSnackbarVisible = false }
                nowPlayingViewModel.consumeFavoriteMessage()
            }

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

            // What storage did on its own while starting (a damaged settings file reset), once.
            val startupNotices by StartupNotices.notices.collectAsState()
            val startupNotice = startupNotices.firstOrNull()
            LaunchedEffect(startupNotice) {
                val notice = startupNotice ?: return@LaunchedEffect
                desktopSnackbarVisible = true
                try {
                    snackbarHostState.showSnackbar(notice.message, duration = SnackbarDuration.Long)
                } finally {
                    desktopSnackbarVisible = false
                }
                StartupNotices.consume(notice)
            }

            val activeDestination = pushedStack.lastOrNull()
                ?.let { RootDest.Pushed(it, pushedStack.size) }
                ?: RootDest.Tab(currentTab)
            val playerActive = isPlayerSurface(activeDestination)
            LaunchedEffect(playerActive) {
                (lyricSurface as? PageOverlayLyricSurface)?.setPlayerPageShown(playerActive)
            }
            val windowPlayer = (activeDestination as? RootDest.Pushed)
                ?.takeIf { platform.isAndroid && playerActive }
            val rootDest = if (windowPlayer == null) activeDestination else {
                val index = pushedStack.indexOfLast { !isPlayerSurface(it) }
                if (index == -1) RootDest.Tab(currentTab) else RootDest.Pushed(pushedStack[index], index + 1)
            }

            // An Android player sheet must not inherit the outgoing rail's remaining width,
            // a compact dock's bottom padding, or the browser's hinge-constrained pane.
            // Animate full-window siblings so their layout is final even in the first frame.
            AnimatedContent(
                targetState = windowPlayer,
                modifier = Modifier.fillMaxSize().clipToBounds(),
                transitionSpec = {
                    windowPlayerTransition(initialState != null, targetState != null, reducedMotion)
                },
                label = "WindowPlayer",
            ) { layer ->
                if (layer != null) {
                    Box(Modifier.fillMaxSize()) {
                        saveableStateHolder.SaveableStateProvider(layer.stateKey()) {
                            PlaybackRoute(
                                destination = layer.dest,
                                onBack = ::back,
                                onOpenLyrics = ::openFullLyric,
                                initialAccent = rawChromeAccent,
                                onAccentColor = { rawChromeAccent = it },
                            )
                        }
                        if (layer == windowPlayer) {
                            SnackbarHost(
                                snackbarHostState,
                                Modifier.align(Alignment.BottomCenter)
                                    .windowInsetsPadding(WindowInsets.playbackSafeContent.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                    ))
                                    .padding(bottom = PlayerPageToolbarClearance),
                            )
                        }
                    }
                } else {
            // The navigation stays on pages opened from a tab. Full-screen pages hide it.
            val topPage = (rootDest as? RootDest.Pushed)?.dest
            val showTabs = !hidesNavigation(topPage)
            val tabs = remember(I18n.language) {
                listOf(
                    NavigationItem(strings.forYou, AppIcons.Home, TabDest.Home),
                    NavigationItem(strings.search, AppIcons.Search, TabDest.Search),
                    NavigationItem(strings.me, AppIcons.Person, TabDest.My),
                )
            }

            val onPlayerPage = isPlayerSurface(topPage)
            val keyboardVisible = LocalSoftKeyboardVisible.current
            val androidBrowseObstructions = if (platform.isAndroid) WindowInsets.androidBrowseObstructions else null
            val browseObstructions = androidBrowseObstructions.takeUnless { onPlayerPage }
            val browseSides = browseObstructions?.only(WindowInsetsSides.Horizontal) ?: WindowInsets(0, 0, 0, 0)
            val browseSidePadding = if (browseObstructions != null) Modifier.windowInsetsPadding(browseSides) else Modifier
            val navigationMotion = rememberAndroidNavigationMotion(platform.isAndroid)
            val motionWindow = LocalAndroidReflowWindow.current
            val motionFold = LocalWindowFold.current?.takeIf { it.isOccluding }
            FoldSafeBrowseContent(
                modifier = Modifier.fillMaxSize(),
                enabled = !onPlayerPage,
                obstructions = browseObstructions,
                contentModifier = if (platform.isAndroid) Modifier.imePadding() else Modifier,
            ) { windowWidth, windowHeight, obstructedTop, pane ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val compactAppBar = platform.isAndroid && windowHeight < CompactAppBarHeightThreshold
                    val desktopMode = desktopLayoutMode(maxWidth, maxHeight)
                    val desktopCompact = platform.isDesktop && desktopMode == DesktopLayoutMode.Compact
                    val showDesktopRail = platform.isDesktop && !desktopCompact
                    val androidNav = if (platform.isAndroid) androidNavigationLayout(windowWidth, windowHeight) else null
                    val showMobileRail = !platform.isDesktop &&
                        (androidNav == AndroidNavigationLayout.Rail || (androidNav == null && maxWidth >= 600.dp))
                    val compactDockVisible = showTabs && !keyboardVisible && androidNav == AndroidNavigationLayout.Compact
                    val dockPlayerLabel = currentMedia?.takeIf { compactDockVisible }?.let { strings.openPlayerFor(it.title) }
                    // The compact dock owns the player entry; only roomier layouts get a FAB.
                    val showDesktopBar = showDesktopRail && currentMedia != null && !onPlayerPage
                    val miniPlayerFits = windowWidth >= 160.dp && maxHeight - obstructedTop >= 200.dp
                    val androidFabSlot = androidNav?.let { androidMiniPlayerFabSlot(it, keyboardVisible, miniPlayerFits) }
                    val showMiniPlayer = !showDesktopBar && currentMedia != null && !onPlayerPage &&
                        if (platform.isAndroid) showTabs && androidFabSlot != null else !keyboardVisible && miniPlayerFits
                    navigationMotion?.Observe(
                        target = if (androidNav != null && pane != null && windowWidth > 0.dp && windowHeight > 0.dp) {
                            AndroidNavigationTarget(androidNav, DpSize(windowWidth, windowHeight), pane, motionFold, motionWindow)
                        } else null,
                        active = showTabs,
                    )
                    val desktopRailExpanded = platform.isDesktop &&
                        showDesktopRail &&
                        desktopRailState.targetValue == WideNavigationRailValue.Expanded
                    LaunchedEffect(desktopCompact) {
                        if (desktopCompact) desktopRailState.collapse()
                    }

                    val bottomNavVisible = showTabs && !keyboardVisible && when {
                        platform.isDesktop -> desktopCompact
                        else -> !showMobileRail && androidNav != AndroidNavigationLayout.Compact
                    }
                    // A floating toolbar needs explicit clearance; a compact dock is measured
                    // by Scaffold and taken out of the viewport instead. Don't add nav-bar
                    // clearance on top of an IME inset that already covers the same edge.
                    val bottomInsets = if (platform.isAndroid) {
                        WindowInsets.navigationBars.union(WindowInsets.displayCutout).exclude(WindowInsets.ime)
                    } else {
                        WindowInsets.navigationBars
                    }
                    val systemNavInset = bottomInsets.asPaddingValues().calculateBottomPadding()
                    val contentBottomInset = if (compactDockVisible) 0.dp else systemNavInset +
                        if (bottomNavVisible) ExpressiveLayout.FloatingNavClearance else 0.dp
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

                    // Scaffold lifts a snackbar over the mini player, but nothing else along the
                    // bottom is a Scaffold bar: without the mini player a snackbar sat on the
                    // window's edge, over the tab bar, the desktop player bar, the player page's
                    // toolbar or the system navigation bar.
                    val miniPlayerShown = showMiniPlayer && !desktopRailExpanded
                    val snackbarClearance = when {
                        miniPlayerShown -> 0.dp
                        showDesktopBar -> systemNavInset + DesktopPlayerBarHeight
                        onPlayerPage -> systemNavInset + PlayerPageToolbarClearance
                        else -> contentBottomInset
                    }
                    Scaffold(
                        containerColor = scheme.surface,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            if (compactDockVisible) {
                                CompactNavigationDock(
                                    tabs = tabs,
                                    selectedTab = currentTab,
                                    onSelect = ::selectTab,
                                    insets = bottomInsets.only(WindowInsetsSides.Bottom).union(browseSides),
                                    playerLabel = dockPlayerLabel,
                                    onOpenPlayer = ::openNowPlaying,
                                    motion = navigationMotion,
                                )
                            }
                        },
                        snackbarHost = {
                            if (windowPlayer == null) {
                                SnackbarHost(snackbarHostState, browseSidePadding.padding(bottom = snackbarClearance))
                            }
                        },
                        floatingActionButtonPosition = FabPosition.End,
                        floatingActionButton = {
                            // Drop an outgoing FAB as soon as its Android geometry changes. In
                            // particular, an exit must not leave a second player entry over the dock.
                            // With the same slot, route/media changes still use AnimatedVisibility.
                            key(androidFabSlot) {
                                AnimatedVisibility(
                                    visible = miniPlayerShown,
                                    enter = if (reducedMotion) EnterTransition.None else miniPlayerEnterTransition(),
                                    exit = if (reducedMotion) ExitTransition.None else miniPlayerExitTransition(),
                                ) {
                                    // Scaffold does not reserve the toolbar's height, so the FAB
                                    // has to step over the floating pill itself.
                                    Box(
                                        browseSidePadding
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
                                        PlaybackPresentationTheme {
                                            MiniNowPlayingBar(
                                                onExpand = ::openNowPlaying,
                                                onAccentColor = { rawChromeAccent = it },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    ) { innerPadding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(bottom = if (compactDockVisible) innerPadding.calculateBottomPadding() else 0.dp)
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
                            } else if (showMobileRail) {
                                AnimatedVisibility(
                                    visible = showTabs,
                                    enter = if (reducedMotion) EnterTransition.None else railEnterTransition(motion, rtl),
                                    exit = if (reducedMotion) ExitTransition.None else railExitTransition(motion, rtl),
                                ) {
                                    MobileNavigationRail(
                                        tabs = tabs,
                                        selectedTab = currentTab,
                                        onSelect = ::selectTab,
                                        insets = if (platform.isAndroid) {
                                            WindowInsets.statusBars.only(WindowInsetsSides.Top)
                                                .union(browseObstructions?.only(WindowInsetsSides.Top + WindowInsetsSides.Start)
                                                    ?: WindowInsets(0, 0, 0, 0))
                                                .union(bottomInsets.only(WindowInsetsSides.Bottom))
                                        } else {
                                            NavigationRailDefaults.windowInsets
                                        },
                                        scrollable = platform.isAndroid,
                                        motion = navigationMotion,
                                    )
                                }
                            }
                            val railStartInsets = if (showMobileRail && showTabs && browseObstructions != null) {
                                // The rail already includes this inset in its measured width. Its
                                // sibling page must not leave a second cutout-width gap.
                                Modifier.consumeWindowInsets(browseSides.only(WindowInsetsSides.Start))
                            } else Modifier
                            Box(Modifier.weight(1f).fillMaxSize().then(railStartInsets)) {
                            Column(Modifier.fillMaxSize()) {
                                AppPageTransition(
                                    destination = rootDest,
                                    modifier = Modifier.weight(1f),
                                    motion = motion,
                                    reducedMotion = reducedMotion,
                                    rtl = rtl,
                                ) { target ->
                                    saveableStateHolder.SaveableStateProvider(target.stateKey()) {
                                    CompositionLocalProvider(
                                        LocalCompactAppBar provides compactAppBar,
                                        LocalBrowseObstructions provides androidBrowseObstructions,
                                    ) {
                                    when (target) {
                                        is RootDest.Pushed -> when (val dest = target.dest) {
                                            is PushedDest.Login -> LoginScreen(
                                                onBack = ::back,
                                                provider = dest.provider,
                                                isActive = target == activeDestination,
                                            )
                                            PushedDest.NowPlaying, PushedDest.FullLyric -> PlaybackRoute(
                                                destination = dest,
                                                onBack = ::back,
                                                onOpenLyrics = ::openFullLyric,
                                                initialAccent = rawChromeAccent,
                                                onAccentColor = { rawChromeAccent = it },
                                            )
                                            is PushedDest.Downloads -> DownloadManagementScreen(
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                            )
                                            is PushedDest.SongRecognition -> SongRecognitionScreen(
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                                isActive = target == activeDestination,
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
                                                onOpenLicenses = { push(PushedDest.Licenses) },
                                                onBack = ::back,
                                            )
                                            is PushedDest.Licenses -> LicensesScreen(
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                            )
                                            is PushedDest.Accounts -> AccountsScreen(
                                                scaffoldPadding = screenPadding,
                                                onBack = ::back,
                                                onOpenLogin = { push(PushedDest.Login(it)) },
                                                isActive = target == activeDestination,
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
                                            TabDest.Home -> HomeScreen(
                                                scaffoldPadding = screenPadding,
                                                onOpenPlaylist = { push(PushedDest.Playlist(it)) },
                                                onOpenMy = { currentTab = TabDest.My },
                                                onOpenRecognition = { push(PushedDest.SongRecognition) },
                                                onOpenDailySongs = { push(PushedDest.DailySongs) },
                                                onOpenSearch = ::openSearch,
                                                onOpenLogin = { push(PushedDest.Login()) },
                                            )
                                            TabDest.Search -> SearchScreen(
                                                bottomPadding = screenPadding.calculateBottomPadding() + 16.dp,
                                                focusRequest = searchFocusRequest,
                                                onFocusRequestHandled = { searchFocusRequest = 0 },
                                                isActive = target == activeDestination,
                                            )
                                            TabDest.My -> UserPlaylistScreen(
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
                                }
                                if (showDesktopBar) {
                                    PlaybackPresentationTheme {
                                        DesktopPlayerBar(
                                            player = player,
                                            viewModel = nowPlayingViewModel,
                                            accentPalette = chromePalette,
                                            sheets = desktopSheets,
                                            onAccentColor = { rawChromeAccent = it },
                                            onOpenNowPlaying = ::openNowPlaying,
                                            onOpenLyrics = ::openFullLyricOverNowPlaying,
                                        )
                                    }
                                }
                            }
                            if (showDesktopRail) {
                                PlaybackPresentationTheme {
                                    DesktopTransportSheetsHost(
                                        sheets = desktopSheets,
                                        player = player,
                                        viewModel = nowPlayingViewModel,
                                        accentPalette = chromePalette,
                                    )
                                }
                            }
                            }
                        }
                        if (bottomNavVisible) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsPadding(bottomInsets.union(browseSides))
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val effectiveToolbarWidth = windowWidth / LocalDensity.current.fontScale
                                val showAllLabels = effectiveToolbarWidth >= 480.dp && !desktopCompact
                                val downloadsActive = desktopCompact && rootDest is RootDest.Pushed && rootDest.dest is PushedDest.Downloads
                                HorizontalFloatingToolbar(
                                    expanded = true,
                                    // One group of tabs: "tab, 2 of 3" rather than loose buttons.
                                    modifier = Modifier
                                         .reflowPlacement(navigationMotion, ReflowEntry.TowardEndFromCenter)
                                         .selectableGroup(),
                                    scrollBehavior = toolbarScrollBehavior,
                                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                                        toolbarContainerColor = scheme.surfaceContainer,
                                        toolbarContentColor = scheme.onSurfaceVariant,
                                    ),
                                ) {
                                    tabs.forEach { item ->
                                        ExpressiveNavToggle(
                                            label = item.label,
                                            icon = item.icon,
                                            selected = !downloadsActive && currentTab == item.dest,
                                            showLabel = showAllLabels || (effectiveToolbarWidth >= 280.dp && !downloadsActive && currentTab == item.dest),
                                            onSelect = { selectTab(item.dest) },
                                        )
                                    }
                                    if (desktopCompact) {
                                        ExpressiveNavToggle(
                                            label = strings.downloads,
                                            icon = AppIcons.Download,
                                            selected = downloadsActive,
                                            showLabel = effectiveToolbarWidth >= 280.dp && downloadsActive,
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
            }
}

@Composable
private fun PlaybackRoute(
    destination: PushedDest,
    onBack: () -> Unit,
    onOpenLyrics: () -> Unit,
    initialAccent: Color?,
    onAccentColor: (Color) -> Unit,
) {
    PlaybackPresentationTheme {
        when (destination) {
            PushedDest.NowPlaying -> NowPlayingScreen(
                onBack = onBack,
                onOpenLyrics = onOpenLyrics,
                initialAccent = initialAccent,
                onAccentColor = onAccentColor,
            )
            PushedDest.FullLyric -> AmllPlayerScreen(onBack = onBack)
            else -> error("Not a playback destination")
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompactNavigationDock(
    tabs: List<NavigationItem>,
    selectedTab: TabDest,
    onSelect: (TabDest) -> Unit,
    insets: WindowInsets,
    playerLabel: String?,
    onOpenPlayer: () -> Unit,
    motion: ReflowProgress?,
) {
    // Only the item strip settles; the dock still paints the surface under the system insets.
    val scheme = MaterialTheme.colorScheme
    val itemCount = tabs.size + if (playerLabel == null) 0 else 1
    Surface(modifier = Modifier.fillMaxWidth(), color = scheme.surfaceContainer) {
        // Only the background needs an exact width. The Box drops that minimum width so
        // ShortNavigationBar can divide the bounded space rather than giving every item the full width.
        Box(Modifier.windowInsetsPadding(insets)) {
            ShortNavigationBar(
                modifier = Modifier.compactNavReflow(motion, itemCount),
                containerColor = Color.Transparent,
                contentColor = scheme.onSurfaceVariant,
                windowInsets = WindowInsets(0, 0, 0, 0),
                arrangement = ShortNavigationBarArrangement.EqualWeight,
            ) {
                tabs.forEach { item ->
                    val selected = selectedTab == item.dest
                    val select = { onSelect(item.dest) }
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = select,
                        modifier = Modifier.navigationTabSemantics(item.label, selected, select),
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    )
                }
                if (playerLabel != null) {
                    ShortNavigationBarItem(
                        selected = false,
                        onClick = onOpenPlayer,
                        modifier = Modifier.clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = playerLabel
                            onClick {
                                onOpenPlayer()
                                true
                            }
                        },
                        icon = { Icon(AppIcons.MusicNote, contentDescription = null) },
                        label = { Text(strings.playerShortLabel, maxLines = 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileNavigationRail(
    tabs: List<NavigationItem>,
    selectedTab: TabDest,
    onSelect: (TabDest) -> Unit,
    insets: WindowInsets,
    scrollable: Boolean,
    motion: ReflowProgress? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val itemColors = NavigationRailItemDefaults.colors(
        indicatorColor = scheme.secondaryContainer,
        selectedIconColor = scheme.onSecondaryContainer,
        selectedTextColor = scheme.secondary,
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant,
    )
    NavigationRail(containerColor = scheme.surfaceContainer, windowInsets = insets) {
        val items: @Composable () -> Unit = {
            tabs.forEach { item ->
                NavigationRailItem(
                    selected = selectedTab == item.dest,
                    onClick = { onSelect(item.dest) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(item.label) },
                    colors = itemColors,
                )
            }
        }
        if (scrollable) {
            Column(
                modifier = Modifier.reflowPlacement(motion, ReflowEntry.TowardBottomFromTop)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = { items() },
            )
        } else {
            items()
        }
    }
}

@Composable
private fun DesktopExpandableSidebar(
    state: WideNavigationRailState,
    tabs: List<NavigationItem>,
    selectedTab: TabDest?,
    downloadsSelected: Boolean,
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
    val scheme = MaterialTheme.colorScheme
    val railColors = WideNavigationRailDefaults.colors(
        containerColor = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        modalContainerColor = scheme.surfaceContainer,
        modalScrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        modalContentColor = scheme.onSurface,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DesktopSidebarContent(
    railExpanded: Boolean,
    expandedContentVisible: Boolean,
    tabs: List<NavigationItem>,
    selectedTab: TabDest?,
    downloadsSelected: Boolean,
    onToggle: () -> Unit,
    onSelectTab: (TabDest) -> Unit,
    onOpenDownloads: () -> Unit,
    recognitionSelected: Boolean,
    onOpenRecognition: () -> Unit,
    settingsSelected: Boolean,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val itemColors = WideNavigationRailItemDefaults.colors(
        selectedIconColor = scheme.onSecondaryContainer,
        selectedTextColor = scheme.onSecondaryContainer,
        selectedIndicatorColor = scheme.secondaryContainer,
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant,
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
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
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
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = scheme.onSurface,
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
                    color = scheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppPageTransition(
    destination: RootDest,
    modifier: Modifier,
    motion: MotionScheme,
    reducedMotion: Boolean,
    rtl: Boolean,
    content: @Composable (RootDest) -> Unit,
) {
    // Home's shortcut and Search's editor stay in their own panes. Only the pages transition.
    AnimatedContent(
        targetState = destination,
        transitionSpec = { pageTransition(initialState, targetState, motion, reducedMotion, rtl) },
        modifier = modifier.fillMaxSize().clipToBounds(),
        label = "AppPageTransition",
    ) { target ->
        content(target)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun pageTransition(
    initial: RootDest,
    target: RootDest,
    motion: MotionScheme,
    reducedMotion: Boolean,
    rtl: Boolean,
): ContentTransform {
    if (reducedMotion) return EnterTransition.None togetherWith ExitTransition.None
    val fade = fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
    return when {
        isPlayerSurface(initial) && isPlayerSurface(target) -> playbackFadeThrough()
        isPlayerSurface(target) -> playbackSheetTransition(showing = true)
        isPlayerSurface(initial) -> playbackSheetTransition(showing = false)
        target.stackDepth != initial.stackDepth -> {
            val direction = if ((target.stackDepth > initial.stackDepth) != rtl) 1 else -1
            (slideInHorizontally(motion.defaultSpatialSpec(), initialOffsetX = { direction * it / 8 }) +
                fadeIn(motion.defaultEffectsSpec())) togetherWith
                (slideOutHorizontally(motion.defaultSpatialSpec(), targetOffsetX = { -direction * it / 12 }) +
                    fadeOut(motion.fastEffectsSpec()))
        }
        else -> fade
    }.apply {
        // An outgoing full player stays above the revealed page while sliding down.
        targetContentZIndex = if (isPlayerSurface(initial) && !isPlayerSurface(target)) -1f else 1f
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun railEnterTransition(motion: MotionScheme, rtl: Boolean): EnterTransition =
    slideInHorizontally(motion.defaultSpatialSpec(), initialOffsetX = { if (rtl) it else -it }) +
        fadeIn(motion.fastEffectsSpec())

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun railExitTransition(motion: MotionScheme, rtl: Boolean): ExitTransition =
    slideOutHorizontally(motion.defaultSpatialSpec(), targetOffsetX = { if (rtl) it else -it }) +
        fadeOut(motion.fastEffectsSpec())

/** Android keeps the outgoing browse shell still underneath a single sliding layer. */
private fun AnimatedContentTransitionScope<RootDest.Pushed?>.windowPlayerTransition(
    wasPlayer: Boolean,
    isPlayer: Boolean,
    reducedMotion: Boolean,
): ContentTransform {
    if (reducedMotion) {
        return ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
    }
    val transform = when {
        wasPlayer && isPlayer -> playbackFadeThrough()
        isPlayer -> slideInVertically(tween(ExpressiveMotion.LongMillis, easing = FastOutSlowInEasing)) { it } togetherWith
            ExitTransition.KeepUntilTransitionsFinished
        else -> EnterTransition.None togetherWith
            slideOutVertically(tween(ExpressiveMotion.LongMillis, easing = FastOutSlowInEasing)) { it }
    }
    return (transform using null).apply {
        targetContentZIndex = if (isPlayer) 1f else -1f
    }
}

// Only playback uses this choreography. The browse/search transitions keep their own springs.
private fun playbackSheetTransition(showing: Boolean): ContentTransform = if (showing) {
    (slideInVertically(tween(ExpressiveMotion.LongMillis, easing = FastOutSlowInEasing)) { it } +
        playbackFadeIn()) togetherWith
        (fadeOut(tween(ExpressiveMotion.ShortMillis, easing = LinearOutSlowInEasing)) +
            scaleOut(tween(ExpressiveMotion.LongMillis, easing = FastOutSlowInEasing), targetScale = 0.98f))
} else {
    (playbackFadeIn() + scaleIn(tween(ExpressiveMotion.LongMillis, easing = FastOutSlowInEasing), initialScale = 0.98f)) togetherWith
        (slideOutVertically(tween(ExpressiveMotion.LongMillis, easing = FastOutSlowInEasing)) { it } +
            fadeOut(tween(ExpressiveMotion.QuickMillis, easing = LinearOutSlowInEasing)))
}

private fun playbackFadeThrough(): ContentTransform =
    (playbackFadeIn(ExpressiveMotion.StaggerDelayMillis) +
        scaleIn(tween(ExpressiveMotion.LongMillis, easing = FastOutSlowInEasing), initialScale = 0.985f)) togetherWith
        (playbackFadeOut() + scaleOut(tween(ExpressiveMotion.LongMillis, easing = FastOutSlowInEasing), targetScale = 0.985f))

private fun playbackFadeIn(delayMillis: Int = ExpressiveMotion.EnterDelayMillis): EnterTransition =
    fadeIn(tween(ExpressiveMotion.ShortMillis, delayMillis, LinearOutSlowInEasing))

private fun playbackFadeOut(): ExitTransition =
    fadeOut(tween(ExpressiveMotion.FastMillis, easing = LinearOutSlowInEasing))

private fun miniPlayerEnterTransition(): EnterTransition =
    scaleIn(tween(ExpressiveMotion.MediumMillis, easing = FastOutSlowInEasing), initialScale = 0.88f) +
        fadeIn(tween(ExpressiveMotion.QuickMillis, easing = LinearOutSlowInEasing))

private fun miniPlayerExitTransition(): ExitTransition =
    scaleOut(tween(ExpressiveMotion.ShortMillis, easing = FastOutSlowInEasing), targetScale = 0.88f) + playbackFadeOut()

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

private fun RootDest.stateKey(): String = when (this) {
    is RootDest.Tab -> "tab:" + when (tab) {
        TabDest.Home -> "home"
        TabDest.Search -> "search"
        TabDest.My -> "my"
    }
    is RootDest.Pushed -> pushedStateKey(dest, stackDepth)
}

private fun pushedStateKey(dest: PushedDest, depth: Int): String =
    "push:$depth:${encodePushedDestination(dest)}"

/** FAB-slot mini player (60dp + its 2dp inset) plus the Scaffold's 16dp FAB margin and air. */
private val MiniPlayerClearance = 88.dp

/** The player pages' own bottom controls: Now Playing's toolbar and the lyric page's island. */
private val PlayerPageToolbarClearance = 96.dp
