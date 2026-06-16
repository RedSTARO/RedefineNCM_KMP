package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodePngToImageBitmap(bytes: ByteArray): ImageBitmap?
