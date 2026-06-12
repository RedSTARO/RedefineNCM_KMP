package com.leejlredstar.redefinencm.kmp.util

/**
 * Web (WASM) actual — STUB (returns the M3 primary fallback). Dormant until the wasmJs target is
 * declared (decision D2). A real implementation should draw the image onto an HTML canvas and
 * read pixels via `getImageData()`.
 */
actual class ImageColorExtractor {
    actual fun extractThemeColor(imageBytes: ByteArray, preferStyle: Int): Long {
        return 0xFF6750A4L // TODO: Canvas getImageData() pixel sampling
    }
}
