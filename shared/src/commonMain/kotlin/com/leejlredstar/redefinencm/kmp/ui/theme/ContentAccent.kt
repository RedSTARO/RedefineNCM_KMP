package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

@Immutable
data class ContentAccentPalette(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val quietContainer: Color,
    val onQuietContainer: Color,
    val pageStart: Color,
    val pageMiddle: Color,
    val pageEnd: Color,
)

@Composable
fun contentAccentPalette(source: Color): ContentAccentPalette {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    val accent = normalizeAccent(source, isDark)
    val buttonAccent = when {
        accent.luminance() > 0.62f -> lerp(accent, Color.Black, 0.30f)
        accent.luminance() < 0.16f -> lerp(accent, Color.White, 0.18f)
        else -> accent
    }
    val container = if (isDark) {
        lerp(accent, scheme.surfaceContainerHigh, 0.46f)
    } else {
        lerp(accent, scheme.surfaceContainerHigh, 0.58f)
    }
    val quietContainer = if (isDark) {
        lerp(accent, scheme.surfaceContainerHigh, 0.72f)
    } else {
        lerp(accent, scheme.surfaceContainerHigh, 0.78f)
    }
    return ContentAccentPalette(
        accent = buttonAccent,
        onAccent = contentColorFor(buttonAccent),
        container = container,
        onContainer = contentColorFor(container),
        quietContainer = quietContainer,
        onQuietContainer = contentColorFor(quietContainer),
        pageStart = if (isDark) lerp(accent, scheme.surface, 0.32f) else lerp(accent, scheme.surface, 0.48f),
        pageMiddle = if (isDark) lerp(accent, scheme.surface, 0.62f) else lerp(accent, scheme.surface, 0.76f),
        pageEnd = scheme.surface,
    )
}

fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.54f) Color(0xFF101010) else Color.White

private fun normalizeAccent(source: Color, isDark: Boolean): Color {
    val opaque = source.copy(alpha = 1f)
    return when {
        opaque.luminance() < 0.05f -> lerp(opaque, Color.White, if (isDark) 0.20f else 0.34f)
        opaque.luminance() > 0.92f -> lerp(opaque, Color.Black, if (isDark) 0.46f else 0.18f)
        else -> opaque
    }
}
