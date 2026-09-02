package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/**
 * The font family a platform preloaded before showing the app, or null to use the system default.
 *
 * Only Web provides one: CanvasKit cannot reach system fonts, so the browser entry point resolves
 * and preloads a bundled CJK family and hands the exact same instance to the theme. Android,
 * Desktop and iOS resolve CJK glyphs from the OS, so they leave this null rather than each
 * declaring an actual that returns [FontFamily.Default].
 */
internal val LocalPreloadedFontFamily = staticCompositionLocalOf<FontFamily?> { null }
