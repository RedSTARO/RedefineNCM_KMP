package com.leejlredstar.redefinencm.kmp.util

import android.graphics.Bitmap
import android.os.Build
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.Image
import java.util.WeakHashMap
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val PALETTE_SAMPLE_AREA = 128 * 128
private val paletteCacheLock = Any()
private val paletteCache = WeakHashMap<Bitmap, MutableMap<Int, Long?>>()

actual fun themeColorFromCoilImage(image: Image, preferStyle: Int): Long? {
    val sourceBitmap = (image as? BitmapImage)?.bitmap ?: return null
    synchronized(paletteCacheLock) {
        paletteCache[sourceBitmap]?.let { styles ->
            if (styles.containsKey(preferStyle)) return styles[preferStyle]
        }
    }

    val extracted = try {
        val (sampleBitmap, shouldRecycle) = createPaletteSample(sourceBitmap)
        val palette = try {
            Palette.from(sampleBitmap).generate()
        } finally {
            if (shouldRecycle && !sampleBitmap.isRecycled) sampleBitmap.recycle()
        }
        val muted = palette.mutedSwatch?.rgb
        val vibrant = palette.vibrantSwatch?.rgb
        val dominant = palette.dominantSwatch?.rgb
        val rgb = when (preferStyle) {
            1 -> vibrant ?: muted ?: dominant
            2 -> dominant ?: vibrant ?: muted
            else -> muted ?: vibrant ?: dominant
        }
        if (rgb == null) {
            cachePaletteResult(sourceBitmap, preferStyle, null)
            return null
        }
        0xFF000000L or (rgb.toLong() and 0xFFFFFF)
    } catch (_: Exception) {
        null
    }
    cachePaletteResult(sourceBitmap, preferStyle, extracted)
    return extracted
}

private fun cachePaletteResult(bitmap: Bitmap, preferStyle: Int, color: Long?) {
    synchronized(paletteCacheLock) {
        paletteCache.getOrPut(bitmap) { mutableMapOf() }[preferStyle] = color
    }
}

private fun createPaletteSample(source: Bitmap): Pair<Bitmap, Boolean> {
    val softwareSource = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE
    ) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: return source to false
    } else {
        source
    }

    val area = softwareSource.width.toLong() * softwareSource.height.toLong()
    if (area <= PALETTE_SAMPLE_AREA || softwareSource.width <= 0 || softwareSource.height <= 0) {
        return softwareSource to (softwareSource !== source)
    }

    val scale = sqrt(PALETTE_SAMPLE_AREA.toDouble() / area.toDouble())
    val width = (softwareSource.width * scale).roundToInt().coerceAtLeast(1)
    val height = (softwareSource.height * scale).roundToInt().coerceAtLeast(1)
    val sample = Bitmap.createScaledBitmap(softwareSource, width, height, true)
    if (softwareSource !== source && !softwareSource.isRecycled) softwareSource.recycle()
    return sample to (sample !== source)
}
