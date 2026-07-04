package com.leejlredstar.redefinencm.kmp.util

import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.Image

actual fun themeColorFromCoilImage(image: Image, preferStyle: Int): Long? {
    return try {
        val hardwareBitmap = (image as? BitmapImage)?.bitmap ?: return null
        // Palette 不能直接读 HARDWARE bitmap，先拷贝为软件位图（与原版 ImageParser 相同）
        val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val palette = Palette.from(softwareBitmap).generate()
        val muted = palette.mutedSwatch?.rgb
        val vibrant = palette.vibrantSwatch?.rgb
        val dominant = palette.dominantSwatch?.rgb
        val rgb = when (preferStyle) {
            1 -> vibrant ?: muted ?: dominant
            2 -> dominant ?: vibrant ?: muted
            else -> muted ?: vibrant ?: dominant
        } ?: return null
        0xFF000000L or (rgb.toLong() and 0xFFFFFF)
    } catch (e: Exception) {
        null
    }
}
