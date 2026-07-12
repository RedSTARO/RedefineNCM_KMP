package com.leejlredstar.redefinencm.kmp.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Platform-neutral Palette-style extractor used by the Skia-backed Coil targets.
 *
 * Pixels are sampled on an approximately 64 x 64 grid, quantized to RGB555, then scored
 * against the same saturation/luminance targets used by the previous JVM and Web actuals.
 * The pixel reader returns an ARGB Int.
 */
internal fun rgb555ThemeColor(
    width: Int,
    height: Int,
    preferStyle: Int,
    argbAt: (x: Int, y: Int) -> Int,
): Long? {
    if (width <= 0 || height <= 0) return null

    val stepX = (width / 64).coerceAtLeast(1)
    val stepY = (height / 64).coerceAtLeast(1)
    val counts = HashMap<Int, Int>()
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val argb = argbAt(x, y)
            val alpha = (argb ushr 24) and 0xFF
            if (alpha >= 128) {
                val quantized = (((argb ushr 19) and 0x1F) shl 10) or
                    (((argb ushr 11) and 0x1F) shl 5) or
                    ((argb ushr 3) and 0x1F)
                counts[quantized] = (counts[quantized] ?: 0) + 1
            }
            x += stepX
        }
        y += stepY
    }
    if (counts.isEmpty()) return null

    val maxPopulation = counts.values.maxOrNull()?.toFloat() ?: return null
    val swatches = counts.entries
        .sortedWith(
            compareByDescending<Map.Entry<Int, Int>> { it.value }
                .thenBy { it.key },
        )
        .take(64)
        .map { (quantized, population) ->
            val red = ((quantized ushr 10) and 0x1F) * 255 / 31
            val green = ((quantized ushr 5) and 0x1F) * 255 / 31
            val blue = (quantized and 0x1F) * 255 / 31
            Rgb555Swatch(
                rgb = (red shl 16) or (green shl 8) or blue,
                population = population,
                hsl = rgbToHsl(red, green, blue),
            )
        }

    fun pick(saturationTarget: Float, saturationMin: Float, saturationMax: Float): Int? {
        var best: Rgb555Swatch? = null
        var bestScore = 0f
        for (swatch in swatches) {
            val saturation = swatch.hsl[1]
            val luminance = swatch.hsl[2]
            if (
                saturation !in saturationMin..saturationMax ||
                luminance !in 0.3f..0.7f
            ) continue
            val score = 0.24f * (1f - abs(saturation - saturationTarget)) +
                0.52f * (1f - abs(luminance - 0.5f)) +
                0.24f * (swatch.population / maxPopulation)
            if (best == null || score > bestScore) {
                best = swatch
                bestScore = score
            }
        }
        return best?.rgb
    }

    val vibrant = pick(saturationTarget = 1f, saturationMin = 0.35f, saturationMax = 1f)
    val muted = pick(saturationTarget = 0.3f, saturationMin = 0.05f, saturationMax = 0.4f)
    val dominant = swatches.first().rgb
    val rgb = when (preferStyle) {
        1 -> vibrant ?: muted ?: dominant
        2 -> dominant
        else -> muted ?: vibrant ?: dominant
    }
    return 0xFF000000L or (rgb.toLong() and 0xFFFFFFL)
}

private data class Rgb555Swatch(
    val rgb: Int,
    val population: Int,
    val hsl: FloatArray,
)

private fun rgbToHsl(red: Int, green: Int, blue: Int): FloatArray {
    val redFloat = red / 255f
    val greenFloat = green / 255f
    val blueFloat = blue / 255f
    val maxColor = max(redFloat, max(greenFloat, blueFloat))
    val minColor = min(redFloat, min(greenFloat, blueFloat))
    val delta = maxColor - minColor
    val luminance = (maxColor + minColor) / 2f
    val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * luminance - 1f))
    val hue = when {
        delta == 0f -> 0f
        maxColor == redFloat -> 60f * (((greenFloat - blueFloat) / delta) % 6f)
        maxColor == greenFloat -> 60f * (((blueFloat - redFloat) / delta) + 2f)
        else -> 60f * (((redFloat - greenFloat) / delta) + 4f)
    }
    return floatArrayOf(
        if (hue < 0f) hue + 360f else hue,
        saturation.coerceIn(0f, 1f),
        luminance,
    )
}
