package com.leejlredstar.redefinencm.kmp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Perceptual colour maths used to derive theme roles from a source colour.
 *
 * Material derives its roles by holding a source **hue**, choosing a **chroma**, and reading off
 * a **tone** — never by blending two sRGB colours together. Blending in sRGB is what produces
 * muddy intermediates: mixing a saturated yellow toward a dark surface passes through olive,
 * because sRGB is not perceptually uniform and the shortest numeric path is not the shortest
 * visual one.
 *
 * Oklch gives the same three controls (lightness, chroma, hue) with well-behaved interpolation,
 * in about sixty lines of pure maths, so it works on every target with no extra dependency.
 * It is not CAM16/HCT — the absolute numbers differ — but the property this app needs from it,
 * "keep the hue, bound the chroma, put the tone where I ask", holds.
 */
internal data class Oklch(
    /** Perceptual lightness, 0f (black) to 1f (white). */
    val lightness: Float,
    /** Chroma, 0f (grey) upward. sRGB rarely exceeds ~0.37. */
    val chroma: Float,
    /** Hue angle in radians. Meaningless when [chroma] is 0. */
    val hue: Float,
)

private fun srgbToLinear(channel: Float): Float =
    if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(channel: Float): Float =
    if (channel <= 0.0031308f) channel * 12.92f else 1.055f * channel.pow(1f / 2.4f) - 0.055f

internal fun Color.toOklch(): Oklch {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)

    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

    val okL = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
    val okA = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val okB = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s

    return Oklch(
        lightness = okL,
        chroma = sqrt(okA * okA + okB * okB),
        hue = atan2(okB, okA),
    )
}

/** Linear-RGB triple for an Oklch value, before any gamut clamping. */
private fun Oklch.toLinearRgb(): Triple<Float, Float, Float> {
    val okA = chroma * cos(hue)
    val okB = chroma * sin(hue)

    val l = (lightness + 0.3963377774f * okA + 0.2158037573f * okB).let { it * it * it }
    val m = (lightness - 0.1055613458f * okA - 0.0638541728f * okB).let { it * it * it }
    val s = (lightness - 0.0894841775f * okA - 1.2914855480f * okB).let { it * it * it }

    return Triple(
        4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
        -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
        -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
    )
}

private fun Triple<Float, Float, Float>.isInGamut(): Boolean {
    val tolerance = 0.0001f
    return first >= -tolerance && first <= 1f + tolerance &&
        second >= -tolerance && second <= 1f + tolerance &&
        third >= -tolerance && third <= 1f + tolerance
}

/**
 * Reduces chroma until the colour fits inside sRGB at its requested lightness.
 *
 * Most lightness/hue pairs cannot hold every chroma: a saturated yellow simply does not exist at
 * a dark tone. Clamping the RGB channels instead would silently move the colour's lightness back
 * toward where the channels happened to land, so asking for tone 0.35 could return something
 * lighter than the input. Giving up chroma keeps the tone — and therefore the contrast — exact,
 * which is the property the palette depends on.
 */
private fun Oklch.fitToGamut(): Oklch {
    if (toLinearRgb().isInGamut()) return this
    var low = 0f
    var high = chroma
    repeat(20) {
        val mid = (low + high) / 2f
        if (copy(chroma = mid).toLinearRgb().isInGamut()) low = mid else high = mid
    }
    return copy(chroma = low)
}

/** Converts back to sRGB, reducing chroma first if the colour cannot be represented. */
internal fun Oklch.toColor(alpha: Float = 1f): Color {
    val (r, g, b) = fitToGamut().toLinearRgb()
    return Color(
        red = linearToSrgb(r).coerceIn(0f, 1f),
        green = linearToSrgb(g).coerceIn(0f, 1f),
        blue = linearToSrgb(b).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

/** Rebuilds this colour at [lightness], optionally re-bounding chroma. Hue is preserved. */
internal fun Color.withTone(lightness: Float, chroma: Float? = null): Color {
    val source = toOklch()
    return Oklch(
        lightness = lightness.coerceIn(0f, 1f),
        chroma = (chroma ?: source.chroma).coerceAtLeast(0f),
        hue = source.hue,
    ).toColor()
}

/** Bounds chroma into `[min, max]`, keeping hue and lightness. */
internal fun Color.clampChroma(min: Float = 0f, max: Float): Color {
    val source = toOklch()
    return source.copy(chroma = source.chroma.coerceIn(min, max)).toColor()
}
