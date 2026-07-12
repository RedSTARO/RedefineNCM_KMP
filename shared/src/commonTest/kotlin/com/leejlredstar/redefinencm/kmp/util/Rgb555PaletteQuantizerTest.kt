package com.leejlredstar.redefinencm.kmp.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Rgb555PaletteQuantizerTest {
    @Test
    fun solidColorIsQuantizedAndUsedByEveryPreference() {
        val pixels = intArrayOf(0xFF336699.toInt())
        val expected = 0xFF31629CL

        assertEquals(expected, extract(pixels, preferStyle = 0))
        assertEquals(expected, extract(pixels, preferStyle = 1))
        assertEquals(expected, extract(pixels, preferStyle = 2))
    }

    @Test
    fun preferencesSelectMutedVibrantAndDominantSwatches() {
        val pixels = IntArray(14) { index ->
            when {
                index < 8 -> 0xFF808080.toInt()
                index < 12 -> 0xFF996666.toInt()
                else -> 0xFFFF0000.toInt()
            }
        }

        assertEquals(0xFF9C6262L, extract(pixels, preferStyle = 0))
        assertEquals(0xFFFF0000L, extract(pixels, preferStyle = 1))
        assertEquals(0xFF838383L, extract(pixels, preferStyle = 2))
    }

    @Test
    fun transparentPixelsAreIgnored() {
        val pixels = intArrayOf(
            0x7FFF0000,
            0x0000FF00,
        )

        assertNull(extract(pixels, preferStyle = 0))
    }

    @Test
    fun dominantTieBreakIsStableAcrossPlatformMapImplementations() {
        val pixels = intArrayOf(
            0xFFFF0000.toInt(),
            0xFF808080.toInt(),
        )

        assertEquals(0xFF838383L, extract(pixels, preferStyle = 2))
    }

    @Test
    fun invalidDimensionsReturnNullWithoutReadingPixels() {
        var readCount = 0

        val result = rgb555ThemeColor(
            width = 0,
            height = 1,
            preferStyle = 0,
        ) { _, _ ->
            readCount += 1
            0xFFFFFFFF.toInt()
        }

        assertNull(result)
        assertEquals(0, readCount)
    }

    private fun extract(pixels: IntArray, preferStyle: Int): Long? =
        rgb555ThemeColor(
            width = pixels.size,
            height = 1,
            preferStyle = preferStyle,
        ) { x, _ -> pixels[x] }
}
