package com.leejlredstar.redefinencm.kmp.util

import coil3.BitmapImage
import coil3.Image

/**
 * JVM actual：Palette 式取色 —— 网格采样 → RGB555 量化直方图 → 按 androidx.palette
 * 的目标参数（vibrant: sat→1.0，muted: sat→0.3，luma→0.5）挑选 swatch，
 * 与 Android actual 的 muted → vibrant → dominant 回退链保持一致语义。
 */
actual fun themeColorFromCoilImage(image: Image, preferStyle: Int): Long? {
    return try {
        val bitmap = (image as? BitmapImage)?.bitmap ?: return null
        rgb555ThemeColor(
            width = bitmap.width,
            height = bitmap.height,
            preferStyle = preferStyle,
            argbAt = { x, y -> bitmap.getColor(x, y) },
        )
    } catch (e: Exception) {
        null
    }
}
