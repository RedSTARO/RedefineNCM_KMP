package com.leejlredstar.redefinencm.kmp

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.leejlredstar.redefinencm.kmp.ui.component.MiniNowPlayingBar
import com.leejlredstar.redefinencm.kmp.util.BackHandler
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import org.koin.compose.koinInject
import com.leejlredstar.redefinencm.kmp.lyric.WebViewLyricScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.HomeScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LoginScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.NowPlayingScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.PlaylistDetailScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SearchScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SettingsScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.UserPlaylistScreen
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme

private sealed interface TabDest {
    data object Home : TabDest
    data object My : TabDest
    data object Settings : TabDest
}

private sealed interface PushedDest {
    data object Search : PushedDest
    data object Login : PushedDest
    data object NowPlaying : PushedDest
    data class Playlist(val id: Long) : PushedDest
}

/**
 * Root composable shared across Android / iOS / Desktop / Web. 3-tab bottom nav (Recommend / My /
 * Settings) with a push stack for Search, Login, NowPlaying, PlaylistDetail. Koin must already be
 * started before this is called.
 */
@Composable
fun App() {
    RedefineNCMTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            var currentTab by remember { mutableStateOf<TabDest>(TabDest.Home) }
            val pushedStack = remember { mutableStateListOf<PushedDest>() }
            fun push(dest: PushedDest) = pushedStack.add(dest)
            fun back() { if (pushedStack.isNotEmpty()) pushedStack.removeAt(pushedStack.lastIndex) }

            BackHandler(enabled = pushedStack.isNotEmpty()) { back() }

            val showTabs = pushedStack.isEmpty()

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showTabs) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab is TabDest.Home,
                                onClick = { currentTab = TabDest.Home },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("推荐") },
                            )
                            NavigationBarItem(
                                selected = currentTab is TabDest.My,
                                onClick = { currentTab = TabDest.My },
                                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                                label = { Text("我的") },
                            )
                            NavigationBarItem(
                                selected = currentTab is TabDest.Settings,
                                onClick = { currentTab = TabDest.Settings },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("设置") },
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (showTabs) {
                        MiniNowPlayingBar(onExpand = { push(PushedDest.NowPlaying) })
                    }
                },
            ) { innerPadding ->
                if (pushedStack.isNotEmpty()) {
                    when (val dest = pushedStack.last()) {
                        is PushedDest.Search -> SearchScreen(onBack = ::back)
                        is PushedDest.Login -> LoginScreen(onBack = ::back)
                        is PushedDest.NowPlaying -> {
                            val settings: PlatformSettings = koinInject()
                            if (settings.getBoolean(SettingKeys.USE_FULL_LYRIC, false)) {
                                WebViewLyricScreen(onBack = ::back)
                            } else {
                                NowPlayingScreen(onBack = ::back)
                            }
                        }
                        is PushedDest.Playlist -> PlaylistDetailScreen(
                            playlistId = dest.id,
                            onBack = ::back,
                            onOpenNowPlaying = { push(PushedDest.NowPlaying) },
                        )
                    }
                } else {
                    when (currentTab) {
                        is TabDest.Home -> HomeScreen(
                            scaffoldPadding = innerPadding,
                            onOpenSearch = { push(PushedDest.Search) },
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
