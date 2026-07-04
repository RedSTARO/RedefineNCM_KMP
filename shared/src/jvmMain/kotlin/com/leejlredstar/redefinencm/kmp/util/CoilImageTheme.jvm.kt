package com.leejlredstar.redefinencm.kmp.util

import coil3.BitmapImage
import coil3.Image

/** JVM actual：Skia bitmap 网格采样平均色（Palette 不可用时的近似方案）。 */
actual fun themeColorFromCoilImage(image: Image, preferStyle: Int): Long? {
    return try {
        val bmp = (image as? BitmapImage)?.bitmap ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
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
                val c = bmp.getColor(x, y)
                rSum += ((c shr 16) and 0xFF).toLong()
                gSum += ((c shr 8) and 0xFF).toLong()
                bSum += (c and 0xFF).toLong()
                count++
                x += stepX
            }
            y += stepY
        }
        if (count == 0L) return null
        0xFF000000L or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
    } catch (e: Exception) {
        null
    }
}
