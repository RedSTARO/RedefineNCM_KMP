package com.leejlredstar.redefinencm.kmp.util

import coil3.BitmapImage
import coil3.Image
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * JVM actual：Palette 式取色 —— 网格采样 → RGB555 量化直方图 → 按 androidx.palette
 * 的目标参数（vibrant: sat→1.0，muted: sat→0.3，luma→0.5）挑选 swatch，
 * 与 Android actual 的 muted → vibrant → dominant 回退链保持一致语义。
 */
actual fun themeColorFromCoilImage(image: Image, preferStyle: Int): Long? {
    return try {
        val bmp = (image as? BitmapImage)?.bitmap ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null

        // 网格采样 + RGB555 量化
        val stepX = (w / 64).coerceAtLeast(1)
        val stepY = (h / 64).coerceAtLeast(1)
        val counts = HashMap<Int, Int>()
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = bmp.getColor(x, y)
                val alpha = (c ushr 24) and 0xFF
                if (alpha >= 128) {
                    val quant = ((c shr 19) and 0x1F shl 10) or
                        ((c shr 11) and 0x1F shl 5) or
                        ((c shr 3) and 0x1F)
                    counts[quant] = (counts[quant] ?: 0) + 1
                }
                x += stepX
            }
            y += stepY
        }
        if (counts.isEmpty()) return null

        data class Swatch(val rgb: Int, val population: Int, val hsl: FloatArray)
        val maxPop = counts.values.max().toFloat()
        val swatches = counts.entries
            .sortedByDescending { it.value }
            .take(64)
            .map { (quant, pop) ->
                val r = ((quant shr 10) and 0x1F) * 255 / 31
                val g = ((quant shr 5) and 0x1F) * 255 / 31
                val b = (quant and 0x1F) * 255 / 31
                Swatch((r shl 16) or (g shl 8) or b, pop, rgbToHsl(r, g, b))
            }

        // androidx.palette 的 Target 参数：权重 sat 0.24 / luma 0.52 / pop 0.24
        fun pick(satTarget: Float, satMin: Float, satMax: Float): Int? {
            var best: Swatch? = null
            var bestScore = 0f
            for (s in swatches) {
                val sat = s.hsl[1]
                val luma = s.hsl[2]
                if (sat < satMin || sat > satMax || luma < 0.3f || luma > 0.7f) continue
                val score = 0.24f * (1f - abs(sat - satTarget)) +
                    0.52f * (1f - abs(luma - 0.5f)) +
                    0.24f * (s.population / maxPop)
                if (best == null || score > bestScore) {
                    best = s
                    bestScore = score
                }
            }
            return best?.rgb
        }

        val vibrant = pick(satTarget = 1f, satMin = 0.35f, satMax = 1f)
        val muted = pick(satTarget = 0.3f, satMin = 0.05f, satMax = 0.4f)
        val dominant = swatches.first().rgb

        val rgb = when (preferStyle) {
            1 -> vibrant ?: muted ?: dominant
            2 -> dominant
            else -> muted ?: vibrant ?: dominant
        }
        0xFF000000L or (rgb.toLong() and 0xFFFFFF)
    } catch (e: Exception) {
        null
    }
}

private fun rgbToHsl(r: Int, g: Int, b: Int): FloatArray {
    val rf = r / 255f
    val gf = g / 255f
    val bf = b / 255f
    val maxC = max(rf, max(gf, bf))
    val minC = min(rf, min(gf, bf))
    val delta = maxC - minC
    val l = (maxC + minC) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))
    val h = when {
        delta == 0f -> 0f
        maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
        maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
        else -> 60f * (((rf - gf) / delta) + 4f)
    }
    return floatArrayOf(if (h < 0) h + 360f else h, s.coerceIn(0f, 1f), l)
}
