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
 * Material 3 Expressive shape scale, pushed past the baseline expressive values.
 *
 * The scale is deliberately steep: `extraSmall` stays legible for dense chips while
 * `extraLarge` is round enough that panels read as capsules rather than cards. That
 * contrast is what makes the connected-list language (large outer / tight inner corners,
 * see ui/component/Expressive.kt) land as a deliberate shape statement instead of
 * uniformly rounded boxes.
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(40.dp),
    extraLarge = RoundedCornerShape(52.dp),
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
 * accent colors are extracted from Coil images and applied locally through [ContentAccentPalette],
 * not through the global scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedefineNCMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val fontFamily = LocalPreloadedFontFamily.current ?: platformFontFamily()
    val typography = ExpressiveTypography.withFontFamily(fontFamily)
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = typography,
        content = content,
    )
}
