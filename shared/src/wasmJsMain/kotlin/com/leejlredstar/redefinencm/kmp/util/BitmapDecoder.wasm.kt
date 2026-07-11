package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

@OptIn(ExperimentalResourceApi::class)
actual fun decodePngToImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { bytes.decodeToImageBitmap() }.getOrNull()
