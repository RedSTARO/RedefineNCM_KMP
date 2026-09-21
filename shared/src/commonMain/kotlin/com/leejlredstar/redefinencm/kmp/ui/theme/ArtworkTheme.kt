package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The colour the whole app is drawn from: the accent of the cover that is playing.
 *
 * A holder rather than a parameter for the same reason [ThemePreferences] is one — the theme is
 * applied at each platform's entry point, above everything that knows what is playing, and the
 * scheme has to be the same in the app, the desktop window chrome and its lyric window.
 *
 * Null until a cover has been read, which is when the scheme is grey.
 */
object ArtworkTheme {
    private val _seed = MutableStateFlow<Color?>(null)
    val seed: StateFlow<Color?> = _seed.asStateFlow()

    fun setSeed(color: Color?) {
        _seed.value = color
    }
}
