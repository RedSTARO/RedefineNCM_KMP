package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.material3.ColorScheme
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
    return remember(source, scheme) { buildContentAccentPalette(source, scheme) }
}

internal fun buildContentAccentPalette(
    source: Color,
    scheme: ColorScheme,
): ContentAccentPalette {
    val isDark = scheme.surface.luminance() < 0.5f
    // Everything below is derived the way Material derives a scheme: hold the source's hue,
    // choose a chroma, place a tone. The previous implementation lerp'd the raw source toward
    // the surface in sRGB, which is what produced the muddy washes — blending a saturated yellow
    // toward a dark surface travels through olive, because the straight numeric path between two
    // sRGB colours is not the path the eye expects.
    //
    // The chroma ceilings matter more than the tones. A Material surface tint is *barely*
    // tinted; letting a fully saturated album colour through at full chroma is what made whole
    // pages look dyed.
    val hue = source.copy(alpha = 1f)
    val surfaceTone = scheme.surface.toOklch().lightness

    fun tint(toneDelta: Float, maxChroma: Float): Color =
        hue.withTone(
            lightness = (surfaceTone + if (isDark) toneDelta else -toneDelta).coerceIn(0f, 1f),
            chroma = hue.toOklch().chroma.coerceAtMost(maxChroma),
        )

    val pageStart = tint(toneDelta = 0.075f, maxChroma = SurfaceTintChroma)
    val pageMiddle = tint(toneDelta = 0.030f, maxChroma = SurfaceTintChroma * 0.6f)
    val pageEnd = scheme.surface
    val container = tint(toneDelta = 0.170f, maxChroma = ContainerChroma)
    val quietContainer = tint(toneDelta = 0.105f, maxChroma = ContainerChroma * 0.7f)

    // The accent is the one role allowed real colour, but still bounded: an album cover can be
    // far more saturated than anything Material would put on a control, and it has to stay
    // legible against its own container.
    val accent = hue.withTone(
        lightness = if (isDark) AccentToneDark else AccentToneLight,
        chroma = hue.toOklch().chroma.coerceIn(AccentChromaMin, AccentChromaMax),
    )

    return ContentAccentPalette(
        accent = accent,
        onAccent = contentColorFor(accent, scheme.surface),
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

/** Ceiling for page-background tinting. Material surface tints are subtle by design. */
private const val SurfaceTintChroma = 0.030f

/** Ceiling for tonal containers — visibly coloured, still a surface rather than a swatch. */
private const val ContainerChroma = 0.055f

private const val AccentChromaMin = 0.055f
private const val AccentChromaMax = 0.135f
private const val AccentToneDark = 0.78f
private const val AccentToneLight = 0.52f

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
    // Vibrant, not muted. The palette now bounds chroma itself, so the extractor's job is to
    // find the colour that actually represents the cover rather than to pre-soften it; picking a
    // muted swatch first and then tinting with it gave washed-out, near-interchangeable pages.
    preferStyle: Int = 1,
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
