package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.ui.graphics.Color

// Complete Material 3 colour schemes used as the non-dynamic fallback.
// Dynamic colour is preferred on Android 12+ / iOS / supported desktop.
//
// Chroma is pushed well past baseline M3 for the expressive look, but the push is applied
// where it is safe to apply: container roles carry the vivid values because they pair with
// dark on-colours, while primary/secondary/tertiary hold their tone so the white-on-colour
// contrast of button and FAB labels is preserved. Tertiary is a saturated coral that acts as
// the app's true accent against the teal core, giving the palette an actual complement rather
// than a single-hue wash.

val LightColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF00705E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF5D8),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF7A6C00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE94A),
    onSecondaryContainer = Color(0xFF201C00),
    tertiary = Color(0xFFB03A1E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD3C4),
    onTertiaryContainer = Color(0xFF380D02),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF5FBF7),
    onBackground = Color(0xFF161D19),
    surface = Color(0xFFF5FBF7),
    onSurface = Color(0xFF161D19),
    surfaceVariant = Color(0xFFD6E8DF),
    onSurfaceVariant = Color(0xFF3B4B43),
    outline = Color(0xFF6B7C73),
    outlineVariant = Color(0xFFBACCC2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF7F1),
    surfaceContainer = Color(0xFFE8F2EB),
    surfaceContainerHigh = Color(0xFFE0EDE4),
    surfaceContainerHighest = Color(0xFFD8E7DD),
)

val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF5FEBCE),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005F50),
    onPrimaryContainer = Color(0xFF6FF5D8),
    secondary = Color(0xFFEFD84A),
    onSecondary = Color(0xFF383100),
    secondaryContainer = Color(0xFF574C00),
    onSecondaryContainer = Color(0xFFFFE94A),
    tertiary = Color(0xFFFFAF92),
    onTertiary = Color(0xFF55200F),
    tertiaryContainer = Color(0xFF7D3A22),
    onTertiaryContainer = Color(0xFFFFD3C4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF0C120F),
    onBackground = Color(0xFFD8E7DD),
    surface = Color(0xFF0C120F),
    onSurface = Color(0xFFD8E7DD),
    surfaceVariant = Color(0xFF3B4B43),
    onSurfaceVariant = Color(0xFFBACCC2),
    outline = Color(0xFF849691),
    outlineVariant = Color(0xFF3B4B43),
    surfaceContainerLowest = Color(0xFF070C09),
    surfaceContainerLow = Color(0xFF161D19),
    surfaceContainer = Color(0xFF1B231E),
    surfaceContainerHigh = Color(0xFF262F2A),
    surfaceContainerHighest = Color(0xFF313B35),
)
