package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled

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
 *
 * The scheme itself is generated from the cover that is playing — see [artworkColorScheme]. The
 * app has no brand colour; before anything has played the scheme is grey. Android 12+ wallpaper
 * colours still take over when the user turns them on, which is then a choice between the
 * wallpaper's colour and the cover's rather than between the wallpaper and a fixed green.
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
    val seed by ArtworkTheme.seed.collectAsState()
    // Every surface that tints itself from a cover animates; the scheme has to travel with them
    // or the chrome, the switches and the dialogs hard-cut while the pages cross-fade.
    val reducedMotion = rememberReducedMotionEnabled()
    val animatedSeed by animateColorAsState(
        targetValue = seed ?: NeutralSchemeSeed,
        animationSpec = if (reducedMotion) snap() else spring(),
        label = "artworkSchemeSeed",
    )
    val artworkScheme = remember(animatedSeed, seed == null, dark) {
        artworkColorScheme(seed = animatedSeed.takeIf { seed != null }, dark = dark)
    }
    val fontFamily = LocalPreloadedFontFamily.current ?: FontFamily.Default
    val typography = ExpressiveTypography.withFontFamily(fontFamily)
    MaterialExpressiveTheme(
        colorScheme = dynamicScheme ?: artworkScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = typography,
        content = content,
    )
}
