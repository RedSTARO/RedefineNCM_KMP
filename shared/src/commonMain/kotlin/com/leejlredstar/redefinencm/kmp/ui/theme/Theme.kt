package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape scale: rounder, larger corner radii than baseline M3.
 * Drives the connected-list shape language used across screens (see ui/component/Expressive.kt).
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * RedefineNCM Material 3 Expressive theme — used across all platforms
 * (Android, iOS, Desktop, Web).
 *
 * Uses the real [MaterialExpressiveTheme] (not plain `MaterialTheme`), so every Material
 * component inherits the expressive [MotionScheme] — spirited, physics-based animation
 * specs — in addition to the expressive color/shape/type scales defined here.
 *
 * Dynamic color (Android 12+ wallpaper extraction, or album-art–derived schemes) is NOT
 * wired here yet: it needs an expect/actual color-scheme provider, since
 * `dynamicColorScheme` is Android/Context-only and unavailable in commonMain. Until then,
 * the static [LightColors] / [DarkColors] schemes are used on every platform. Album-art
 * accent colors are applied locally per screen via `util/ImageColorExtractor`, not through
 * the global scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedefineNCMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content,
    )
}
