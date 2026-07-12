package com.leejlredstar.redefinencm.kmp.util

import coil3.BitmapImage
import coil3.Image

/** iOS 使用 Coil 的 Skia bitmap，并与 JVM/Web 共用 RGB555 Palette 式量化器。 */
actual fun themeColorFromCoilImage(image: Image, preferStyle: Int): Long? {
    return try {
        val bitmap = (image as? BitmapImage)?.bitmap ?: return null
        rgb555ThemeColor(
            width = bitmap.width,
            height = bitmap.height,
            preferStyle = preferStyle,
            argbAt = { x, y -> bitmap.getColor(x, y) },
        )
    } catch (_: Throwable) {
        null
    }
}
