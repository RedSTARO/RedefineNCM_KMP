package com.leejlredstar.redefinencm.kmp.util

import coil3.BitmapImage
import coil3.Image

/** Browser palette extraction with the same muted/vibrant/dominant ordering as JVM. */
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
