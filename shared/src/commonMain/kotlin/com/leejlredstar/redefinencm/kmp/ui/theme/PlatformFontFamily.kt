package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/** Lets Web reuse the exact family instance that its resolver preloaded before showing the app. */
internal val LocalPreloadedFontFamily = staticCompositionLocalOf<FontFamily?> { null }

/** Platform font fallback; Web must bundle CJK glyphs because CanvasKit cannot use system fonts. */
@Composable
internal expect fun platformFontFamily(): FontFamily
