package com.leejlredstar.redefinencm.kmp.util

/**
 * Platform-specific image color extraction.
 * Used for deriving theme colors from album art.
 *
 * - Android: Uses Palette API
 * - iOS: Uses UIImage color analysis
 * - Desktop: Uses AWT BufferedImage color sampling
 * - Web: Uses Canvas API
 */
expect class ImageColorExtractor {
    /**
     * Extract a theme color from image bytes.
     * @param imageBytes Raw image data (JPEG/PNG)
     * @param preferStyle 0=muted, 1=vibrant, 2=dominant
     * @return ARGB color as Long (0xAARRGGBB)
     */
    fun extractThemeColor(imageBytes: ByteArray, preferStyle: Int = 0): Long
}

/**
 * Compute a contrasting text color (black or white) for a given background.
 */
fun contrastingTextColor(backgroundColor: Long): Long {
    // Extract RGB, compute relative luminance
    val r = ((backgroundColor shr 16) and 0xFF) / 255.0
    val g = ((backgroundColor shr 8) and 0xFF) / 255.0
    val b = (backgroundColor and 0xFF) / 255.0
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return if (luminance > 0.5) 0xFF000000 else 0xFFFFFFFF
}
