package com.leejlredstar.redefinencm.kmp.util

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * Desktop (JVM) actual: averages a downsampled grid of pixels from the album art via AWT.
 * `preferStyle` is currently ignored (average only); a muted/vibrant/dominant split can be
 * added later. Returns the M3 primary as a fallback on any decode failure.
 */
actual class ImageColorExtractor {
    actual fun extractThemeColor(imageBytes: ByteArray, preferStyle: Int): Long {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(imageBytes)) ?: return DEFAULT
            val w = image.width
            val h = image.height
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
                    val argb = image.getRGB(x, y)
                    rSum += ((argb shr 16) and 0xFF).toLong()
                    gSum += ((argb shr 8) and 0xFF).toLong()
                    bSum += (argb and 0xFF).toLong()
                    count++
                    x += stepX
                }
                y += stepY
            }
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
