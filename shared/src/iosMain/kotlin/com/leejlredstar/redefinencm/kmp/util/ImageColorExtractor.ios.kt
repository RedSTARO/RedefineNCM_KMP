package com.leejlredstar.redefinencm.kmp.util

/**
 * iOS actual — STUB (returns the M3 primary fallback).
 *
 * A real implementation should decode `imageBytes` into a `UIImage`/`CGImage` and sample pixels
 * (e.g., draw into a 1x1 `CGBitmapContext` to get the average color, or sample a downscaled
 * grid). That uses CoreGraphics cinterop and can only be verified with an iOS build, so it is
 * left as a documented TODO rather than shipped unverified.
 */
actual class ImageColorExtractor {
    actual fun extractThemeColor(imageBytes: ByteArray, preferStyle: Int): Long {
        return 0xFF6750A4L // TODO: CoreGraphics pixel sampling
    }
}
