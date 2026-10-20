package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Draws the status and navigation bar icons light on a dark background and dark on a light one.
 * The app draws edge to edge, and Android otherwise picks the icons from the system's own light or
 * dark setting, which neither the app's theme choice nor a dark full-screen page follows: in light
 * mode the lyric page's clock and battery were dark on near-black. iOS sets the status bar's
 * style for the same reason; the desktop and the browser have no bars, and do nothing.
 */
@Composable
expect fun SystemBarIcons(darkBackground: Boolean)

/**
 * The backgrounds pages draw under the bars themselves, newest last. The app root reads the
 * newest; with none, the bars follow the theme's surface.
 */
@Stable
class SystemBarBackgrounds {
    private val declared = mutableStateListOf<Pair<Any, Boolean>>()

    /** Whether the newest declared background is dark, or null when no page declares one. */
    val darkest: Boolean? get() = declared.lastOrNull()?.second

    internal fun declare(owner: Any, dark: Boolean) {
        val index = declared.indexOfFirst { it.first === owner }
        if (index < 0) declared.add(owner to dark) else if (declared[index].second != dark) declared[index] = owner to dark
    }

    internal fun withdraw(owner: Any) {
        declared.removeAll { it.first === owner }
    }
}

val LocalSystemBarBackgrounds = staticCompositionLocalOf { SystemBarBackgrounds() }

/** For a page that draws its own background under the bars, while it is shown. */
@Composable
fun DeclareSystemBarBackground(dark: Boolean) {
    val backgrounds = LocalSystemBarBackgrounds.current
    val owner = remember { Any() }
    SideEffect { backgrounds.declare(owner, dark) }
    DisposableEffect(backgrounds, owner) {
        onDispose { backgrounds.withdraw(owner) }
    }
}
