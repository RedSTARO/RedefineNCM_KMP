package com.leejlredstar.redefinencm.kmp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.leejlredstar.redefinencm.kmp.ui.screen.HomeScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.LoginScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.NowPlayingScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.PlaylistDetailScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SearchScreen
import com.leejlredstar.redefinencm.kmp.ui.screen.SettingsScreen
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme

/**
 * Simple dependency-free navigation destinations + back stack. (Voyager / Navigation-Compose can
 * replace this later; a hand-rolled stack keeps the migration dependency-light for now.)
 */
private sealed interface Nav {
    data object Home : Nav
    data object Search : Nav
    data object Login : Nav
    data object NowPlaying : Nav
    data object Settings : Nav
    data class Playlist(val id: Long) : Nav
}

/**
 * Root composable, shared across Android / iOS / Desktop / Web. Wraps the app in the M3 Expressive
 * theme and hosts navigation. **Entry is [HomeScreen]** (主页); Login/Search/Playlist/NowPlaying are
 * pushed onto a back stack. Koin must already be started (see `di.initKoin`, called from each
 * platform entry point) — screens resolve their ViewModels via `koinInject()`.
 */
@Composable
fun App() {
    RedefineNCMTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            val backStack = remember { mutableStateListOf<Nav>(Nav.Home) }
            fun navigate(dest: Nav) = backStack.add(dest)
            fun back() {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            }

            when (val screen = backStack.last()) {
                is Nav.Home -> HomeScreen(
                    onOpenLogin = { navigate(Nav.Login) },
                    onOpenSearch = { navigate(Nav.Search) },
                    onOpenNowPlaying = { navigate(Nav.NowPlaying) },
                    onOpenPlaylist = { navigate(Nav.Playlist(it)) },
                    onOpenSettings = { navigate(Nav.Settings) },
                )
                is Nav.Search -> SearchScreen(
                    onBack = ::back,
                    onOpenNowPlaying = { navigate(Nav.NowPlaying) },
                )
                is Nav.Login -> LoginScreen(onBack = ::back)
                is Nav.NowPlaying -> NowPlayingScreen(onBack = ::back)
                is Nav.Settings -> SettingsScreen(onBack = ::back)
                is Nav.Playlist -> PlaylistDetailScreen(
                    playlistId = screen.id,
                    onBack = ::back,
                    onOpenNowPlaying = { navigate(Nav.NowPlaying) },
                )
            }
        }
    }
}
