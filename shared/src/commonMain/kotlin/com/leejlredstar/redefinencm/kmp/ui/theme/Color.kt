package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.ColorScheme

/**
 * The scheme with no cover behind it.
 *
 * These were two complete Material schemes written out as literals in a teal green — the app's
 * brand colour. There is no brand colour now: the scheme is generated from the cover that is
 * playing (see [artworkColorScheme]), and these are what it generates before anything has, which
 * is grey. They are still named `LightColors` / `DarkColors` because that is what a fixed scheme
 * is called wherever one is pinned deliberately — the lyric overlay, which is dark whatever the
 * app's theme is — and what the palette tests measure against.
 */
val LightColors: ColorScheme = neutralColorScheme(dark = false)

val DarkColors: ColorScheme = neutralColorScheme(dark = true)
