package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.ColorScheme

/**
 * The scheme with no cover behind it.
 *
 * The app has no brand colour: the scheme is generated from the cover that is playing (see
 * [artworkColorScheme]), and these are what it generates before anything has played, which is
 * grey. They are named `LightColors` / `DarkColors` because that is what a fixed scheme is
 * called wherever one is pinned deliberately (the lyric overlay, which is dark whatever the
 * app's theme is) and what the palette tests measure against.
 */
val LightColors: ColorScheme = neutralColorScheme(dark = false)

val DarkColors: ColorScheme = neutralColorScheme(dark = true)
