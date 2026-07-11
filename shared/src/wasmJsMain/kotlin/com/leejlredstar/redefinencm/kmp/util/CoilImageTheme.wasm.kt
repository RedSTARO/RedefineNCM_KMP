package com.leejlredstar.redefinencm.kmp.util

import coil3.BitmapImage
import coil3.Image
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Browser palette extraction with the same muted/vibrant/dominant ordering as JVM. */
actual fun themeColorFromCoilImage(image: Image, preferStyle: Int): Long? {
    return try {
        val bitmap = (image as? BitmapImage)?.bitmap ?: return null
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val stepX = (width / 64).coerceAtLeast(1)
        val stepY = (height / 64).coerceAtLeast(1)
        val counts = HashMap<Int, Int>()
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val color = bitmap.getColor(x, y)
                val alpha = (color ushr 24) and 0xFF
                if (alpha >= 128) {
                    val quantized = (((color shr 19) and 0x1F) shl 10) or
                        (((color shr 11) and 0x1F) shl 5) or
                        ((color shr 3) and 0x1F)
                    counts[quantized] = (counts[quantized] ?: 0) + 1
                }
                x += stepX
            }
            y += stepY
        }
        if (counts.isEmpty()) return null

        val maxPopulation = counts.values.max().toFloat()
        val swatches = counts.entries
            .sortedByDescending { it.value }
            .take(64)
            .map { (quantized, population) ->
                val red = ((quantized shr 10) and 0x1F) * 255 / 31
                val green = ((quantized shr 5) and 0x1F) * 255 / 31
                val blue = (quantized and 0x1F) * 255 / 31
                WebPaletteSwatch(
                    rgb = (red shl 16) or (green shl 8) or blue,
                    population = population,
                    hsl = webRgbToHsl(red, green, blue),
                )
            }

        fun pick(saturationTarget: Float, saturationMin: Float, saturationMax: Float): Int? {
            var best: WebPaletteSwatch? = null
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
        0xFF000000L or (rgb.toLong() and 0xFFFFFFL)
    } catch (_: Throwable) {
        null
    }
}

private data class WebPaletteSwatch(
    val rgb: Int,
    val population: Int,
    val hsl: FloatArray,
)

private fun webRgbToHsl(red: Int, green: Int, blue: Int): FloatArray {
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
