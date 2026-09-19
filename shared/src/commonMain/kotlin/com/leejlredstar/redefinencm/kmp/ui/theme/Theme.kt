package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
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
 * Light or dark follows [ThemePreferences] (the system's setting unless the user picked one).
 * Android 12+ wallpaper colours come through [rememberDynamicColorScheme] when the user turns
 * them on; everywhere else the static [LightColors] / [DarkColors] schemes are used. Album-art
 * accent colors are extracted from Coil images and applied locally through [ContentAccentPalette],
 * not through the global scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RedefineNCMTheme(
    /** Forces light or dark; null follows the user's choice in [ThemePreferences]. */
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val mode by ThemePreferences.mode.collectAsState()
    val useDynamicColor by ThemePreferences.dynamicColor.collectAsState()
    val dark = darkTheme ?: when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val dynamicScheme = if (useDynamicColor) rememberDynamicColorScheme(dark) else null
    val fontFamily = LocalPreloadedFontFamily.current ?: FontFamily.Default
    val typography = ExpressiveTypography.withFontFamily(fontFamily)
    MaterialExpressiveTheme(
        colorScheme = dynamicScheme ?: if (dark) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = typography,
        content = content,
    )
}
