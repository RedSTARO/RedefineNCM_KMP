package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import coil3.Image
import com.leejlredstar.redefinencm.kmp.util.themeColorFromCoilImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ContentAccentPalette(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val secondaryOnContainer: Color,
    val quietContainer: Color,
    val onQuietContainer: Color,
    val secondaryOnQuietContainer: Color,
    val pageStart: Color,
    val onPageStart: Color,
    val secondaryOnPageStart: Color,
    val pageMiddle: Color,
    val onPageMiddle: Color,
    val secondaryOnPageMiddle: Color,
    val pageEnd: Color,
    val onPageEnd: Color,
    val secondaryOnPageEnd: Color,
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
    val pageStart = if (isDark) lerp(accent, scheme.surface, 0.32f) else lerp(accent, scheme.surface, 0.48f)
    val pageMiddle = if (isDark) lerp(accent, scheme.surface, 0.62f) else lerp(accent, scheme.surface, 0.76f)
    val pageEnd = scheme.surface
    return ContentAccentPalette(
        accent = buttonAccent,
        onAccent = contentColorFor(buttonAccent, scheme.surface),
        container = container,
        onContainer = contentColorFor(container, scheme.surface),
        secondaryOnContainer = secondaryContentColorFor(container, scheme.surface),
        quietContainer = quietContainer,
        onQuietContainer = contentColorFor(quietContainer, scheme.surface),
        secondaryOnQuietContainer = secondaryContentColorFor(quietContainer, scheme.surface),
        pageStart = pageStart,
        onPageStart = contentColorFor(pageStart, scheme.surface),
        secondaryOnPageStart = secondaryContentColorFor(pageStart, scheme.surface),
        pageMiddle = pageMiddle,
        onPageMiddle = contentColorFor(pageMiddle, scheme.surface),
        secondaryOnPageMiddle = secondaryContentColorFor(pageMiddle, scheme.surface),
        pageEnd = pageEnd,
        onPageEnd = contentColorFor(pageEnd, scheme.surface),
        secondaryOnPageEnd = secondaryContentColorFor(pageEnd, scheme.surface),
    )
}

private val DarkContentColor = Color(0xFF101010)
private val LightContentColor = Color.White

/** Chooses the candidate with the higher WCAG contrast against the rendered background. */
fun contentColorFor(
    background: Color,
    backdrop: Color = Color.White,
): Color {
    val darkContrast = contrastRatio(DarkContentColor, background, backdrop)
    val lightContrast = contrastRatio(LightContentColor, background, backdrop)
    return if (darkContrast >= lightContrast) DarkContentColor else LightContentColor
}

/** Returns an opaque, visually quieter foreground that still meets WCAG AA for body text. */
internal fun secondaryContentColorFor(
    background: Color,
    backdrop: Color = Color.White,
    minimumContrast: Float = 4.5f,
): Color {
    val renderedBackdrop = compositeOver(backdrop, Color.White)
    val renderedBackground = compositeOver(background, renderedBackdrop)
    val primary = contentColorFor(renderedBackground, renderedBackdrop)
    var passingMix = 0f
    var failingMix = 1f
    repeat(16) {
        val mix = (passingMix + failingMix) / 2f
        val candidate = lerp(primary, renderedBackground, mix).copy(alpha = 1f)
        if (contrastRatio(candidate, renderedBackground, renderedBackdrop) >= minimumContrast) {
            passingMix = mix
        } else {
            failingMix = mix
        }
    }
    return lerp(primary, renderedBackground, passingMix).copy(alpha = 1f)
}

/**
 * WCAG contrast ratio after alpha-compositing both colors onto an opaque [backdrop].
 *
 * Kept internal so common tests can protect the palette's accessibility decisions without
 * exposing a low-level color utility as public application API.
 */
internal fun contrastRatio(
    foreground: Color,
    background: Color,
    backdrop: Color = Color.White,
): Float {
    val opaqueBackdrop = compositeOver(backdrop, Color.White)
    val renderedBackground = compositeOver(background, opaqueBackdrop)
    val renderedForeground = compositeOver(foreground, renderedBackground)
    val foregroundLuminance = renderedForeground.luminance()
    val backgroundLuminance = renderedBackground.luminance()
    val lighter = maxOf(foregroundLuminance, backgroundLuminance)
    val darker = minOf(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun compositeOver(foreground: Color, background: Color): Color {
    val foregroundAlpha = foreground.alpha.coerceIn(0f, 1f)
    val backgroundAlpha = background.alpha.coerceIn(0f, 1f)
    val resultAlpha = foregroundAlpha + backgroundAlpha * (1f - foregroundAlpha)
    if (resultAlpha <= 0f) return Color.Transparent

    fun compositeChannel(foregroundChannel: Float, backgroundChannel: Float): Float =
        (
            foregroundChannel * foregroundAlpha +
                backgroundChannel * backgroundAlpha * (1f - foregroundAlpha)
            ) / resultAlpha

    return Color(
        red = compositeChannel(foreground.red, background.red),
        green = compositeChannel(foreground.green, background.green),
        blue = compositeChannel(foreground.blue, background.blue),
        alpha = resultAlpha,
    )
}

/**
 * Creates an image-success callback that performs platform palette extraction away from the
 * UI thread, then publishes the resulting Compose color back on the composition scope.
 */
@Composable
fun rememberThemeColorExtractor(
    requestKey: Any?,
    preferStyle: Int = 0,
    onAccentColor: (Color) -> Unit,
): (Image) -> Unit {
    val scope = rememberCoroutineScope()
    val latestCallback = rememberUpdatedState(onAccentColor)
    val cacheKey = remember(requestKey, preferStyle) {
        requestKey?.let { ImageAccentCacheKey(it.toString(), preferStyle) }
    }
    val extraction = remember(requestKey, preferStyle) { ThemeColorExtractionState() }
    LaunchedEffect(cacheKey) {
        cacheKey?.let(ImageAccentCache::get)?.let { cached ->
            latestCallback.value(cached)
        }
    }
    DisposableEffect(extraction) {
        onDispose {
            extraction.generation += 1
            extraction.job?.cancel()
            extraction.job = null
        }
    }
    return remember(scope, extraction, preferStyle, cacheKey) {
        extract@{ image ->
            cacheKey?.let(ImageAccentCache::get)?.let { cached ->
                latestCallback.value(cached)
                return@extract
            }
            val generation = ++extraction.generation
            extraction.job?.cancel()
            extraction.job = scope.launch {
                val color = withContext(Dispatchers.Default) {
                    themeColorFromCoilImage(image, preferStyle)?.let { Color(it) }
                }
                if (generation == extraction.generation) {
                    color?.let { extracted ->
                        cacheKey?.let { ImageAccentCache.put(it, extracted) }
                        latestCallback.value(extracted)
                    }
                }
            }
        }
    }
}

private class ThemeColorExtractionState(
    var generation: Long = 0L,
    var job: Job? = null,
)

private data class ImageAccentCacheKey(
    val requestKey: String,
    val preferStyle: Int,
)

/** URI/model keyed UI-thread LRU so one artwork is not quantized independently by every surface. */
private object ImageAccentCache {
    private const val MaxEntries = 96
    private val entries = LinkedHashMap<ImageAccentCacheKey, Color>()

    fun get(key: ImageAccentCacheKey): Color? = entries.remove(key)?.also { cached ->
        entries[key] = cached
    }

    fun put(key: ImageAccentCacheKey, color: Color) {
        entries.remove(key)
        entries[key] = color
        while (entries.size > MaxEntries) {
            entries.keys.firstOrNull()?.let(entries::remove) ?: break
        }
    }
}

private fun normalizeAccent(source: Color, isDark: Boolean): Color {
    val opaque = source.copy(alpha = 1f)
    return when {
        opaque.luminance() < 0.05f -> lerp(opaque, Color.White, if (isDark) 0.20f else 0.34f)
        opaque.luminance() > 0.92f -> lerp(opaque, Color.Black, if (isDark) 0.46f else 0.18f)
        else -> opaque
    }
}
