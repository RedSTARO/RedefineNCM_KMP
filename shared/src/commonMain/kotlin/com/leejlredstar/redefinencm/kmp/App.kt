package com.leejlredstar.redefinencm.kmp

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.lyric.WebViewLyricScreen
import com.leejlredstar.redefinencm.kmp.ui.component.MiniNowPlayingBar
import com.leejlredstar.redefinencm.kmp.ui.screen.HomeScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LoginScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.NowPlayingScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.PlaylistDetailScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SettingsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.UserPlaylistScreen
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme
import com.leejlredstar.redefinencm.kmp.util.BackHandler
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
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
    data class Playlist(val id: Long) : PushedDest
}

private data class NavigationItem(
    val label: String,
    val icon: ImageVector,
    val dest: TabDest,
)

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

            var currentTab by remember { mutableStateOf<TabDest>(TabDest.Home) }
            val pushedStack = remember {
                mutableStateListOf<PushedDest>().apply {
                    // 原版 SplashActivity：无 cookie 时先进登录页
                    if (settings.getString(SettingKeys.COOKIE, "").isBlank()) add(PushedDest.Login)
                }
            }
            fun push(dest: PushedDest) = pushedStack.add(dest)
            fun back() { if (pushedStack.isNotEmpty()) pushedStack.removeAt(pushedStack.lastIndex) }
            fun openNowPlaying() {
                val existingIndex = pushedStack.indexOfLast { it is PushedDest.NowPlaying }
                if (existingIndex >= 0) {
                    while (pushedStack.lastIndex > existingIndex) {
                        pushedStack.removeAt(pushedStack.lastIndex)
                    }
                } else {
                    push(PushedDest.NowPlaying)
                }
            }

            BackHandler(enabled = pushedStack.isNotEmpty()) { back() }

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

            BoxWithConstraints {
                val isWide = maxWidth >= 600.dp
                val showMiniPlayer = pushedStack.lastOrNull() !is PushedDest.NowPlaying

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showTabs && !isWide) {
                            NavigationBar {
                                tabs.forEach { item ->
                                    NavigationBarItem(
                                        selected = currentTab == item.dest,
                                        onClick = { currentTab = item.dest },
                                        icon = { Icon(item.icon, contentDescription = item.label) },
                                        label = { Text(item.label) },
                                    )
                                }
                            }
                        }
                    },
                    floatingActionButton = {
                        if (showMiniPlayer) {
                            MiniNowPlayingBar(onExpand = ::openNowPlaying)
                        }
                    },
                ) { innerPadding ->
                    if (pushedStack.isNotEmpty()) {
                        when (val dest = pushedStack.last()) {
                            is PushedDest.Login -> LoginScreen(onBack = ::back)
                            is PushedDest.NowPlaying -> NowPlayingScreen(
                                onBack = ::back,
                                onOpenFullLyric = { push(PushedDest.FullLyric) },
                            )
                            is PushedDest.FullLyric -> WebViewLyricScreen(onBack = ::back)
                            is PushedDest.Playlist -> PlaylistDetailScreen(
                                playlistId = dest.id,
                                onBack = ::back,
                            )
                        }
                    } else {
                        Row {
                            if (isWide) {
                                NavigationRail(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    tabs.forEach { item ->
                                        NavigationRailItem(
                                            selected = currentTab == item.dest,
                                            onClick = { currentTab = item.dest },
                                            icon = { Icon(item.icon, contentDescription = item.label) },
                                            label = { Text(item.label) },
                                        )
                                    }
                                }
                            }
                            when (currentTab) {
                                is TabDest.Home -> HomeScreen(
                                    scaffoldPadding = innerPadding,
                                    onOpenPlaylist = { push(PushedDest.Playlist(it)) },
                                )
                                is TabDest.My -> UserPlaylistScreen(
                                    scaffoldPadding = innerPadding,
                                    onOpenLogin = { push(PushedDest.Login) },
                                    onOpenPlaylist = { push(PushedDest.Playlist(it)) },
                                )
                                is TabDest.Settings -> SettingsScreen(scaffoldPadding = innerPadding)
                            }
                        }
                    }
                }
            }
        }
    }
}
