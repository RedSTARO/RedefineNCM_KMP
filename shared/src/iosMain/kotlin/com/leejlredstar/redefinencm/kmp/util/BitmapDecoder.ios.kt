package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

actual fun decodePngToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() } catch (_: Exception) { null }
}
