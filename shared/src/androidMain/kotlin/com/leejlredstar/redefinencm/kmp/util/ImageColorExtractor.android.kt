package com.leejlredstar.redefinencm.kmp.util

import android.graphics.BitmapFactory

/**
 * Android actual: averages a downsampled grid of pixels from the album art via BitmapFactory.
 * (Uses plain pixel sampling rather than androidx.palette to avoid an extra dependency;
 * swap in Palette later if muted/vibrant swatch selection is wanted.) `preferStyle` is ignored.
 */
actual class ImageColorExtractor {
    actual fun extractThemeColor(imageBytes: ByteArray, preferStyle: Int): Long {
        return try {
            val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return DEFAULT
            val w = bmp.width
            val h = bmp.height
            if (w <= 0 || h <= 0) return DEFAULT
            val stepX = (w / 32).coerceAtLeast(1)
            val stepY = (h / 32).coerceAtLeast(1)
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0L
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    val p = bmp.getPixel(x, y)
                    rSum += ((p shr 16) and 0xFF).toLong()
                    gSum += ((p shr 8) and 0xFF).toLong()
                    bSum += (p and 0xFF).toLong()
                    count++
                    x += stepX
                }
                y += stepY
            }
            bmp.recycle()
            if (count == 0L) return DEFAULT
            (0xFF000000L) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
        } catch (e: Exception) {
            DEFAULT
        }
    }

    companion object {
        private const val DEFAULT = 0xFF6750A4L // M3 primary
    }
}
