package com.leejlredstar.redefinencm.kmp.util

import coil3.BitmapImage
import coil3.Image

/**
 * Skia actual：Palette 式取色 —— 网格采样 → RGB555 量化直方图 → 按 androidx.palette
 * 的目标参数（vibrant: sat→1.0，muted: sat→0.3，luma→0.5）挑选 swatch，
 * 与 Android actual 的 muted → vibrant → dominant 回退链保持一致语义。
 *
 * Desktop、iOS 和 Web 共用 Coil 的 Skia bitmap，因此这里只有一份实现。
 *
 * 捕获 `Throwable` 而非 `Exception`：Kotlin/Native 与 wasm 上部分解码失败以 `Error`
 * 形式抛出。取色失败只意味着回退到默认强调色，不值得让它冒泡成崩溃。
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
    } catch (_: Throwable) {
        null
    }
}
