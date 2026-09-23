package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.serialization.Serializable
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Loudness over one analysed section, sampled every [FRAME_MS] milliseconds.
 *
 * This is the part of the analysis that needs no model, which is why it exists on every device:
 * where the music really starts after a silent lead-in, where it really stops before a long
 * silent tail or a hidden track, and how loud the section is. A crossfade placed by it already
 * avoids the two failures a fixed-length crossfade has — fading the end of a song into seconds
 * of nothing, and bringing the next one in over its own silence.
 */
@Serializable
data class EnergyProfile(
    val startMs: Long,
    /** RMS level of each frame in dBFS, floored at [SILENCE_FLOOR_DB]. */
    val levelsDb: List<Float>,
) {
    val endMs: Long get() = startMs + levelsDb.size * FRAME_MS

    val peakDb: Float get() = levelsDb.maxOrNull() ?: SILENCE_FLOOR_DB

    /** The level music counts as audible at: 35 dB under the section's peak, never below -55. */
    private val audibleThresholdDb: Float get() = maxOf(peakDb - 35f, -55f)

    /** First moment the section is audibly playing music, or null for a silent section. */
    fun firstAudibleMs(): Long? {
        val threshold = audibleThresholdDb
        val smoothed = smoothed()
        val index = smoothed.indexOfFirst { it >= threshold }
        return if (index < 0) null else startMs + index * FRAME_MS
    }

    /** The end of the last audible frame, or null for a silent section. */
    fun lastAudibleEndMs(): Long? {
        val threshold = audibleThresholdDb
        val smoothed = smoothed()
        val index = smoothed.indexOfLast { it >= threshold }
        return if (index < 0) null else startMs + (index + 1) * FRAME_MS
    }

    /** Mean level of the audible frames between [fromMs] and [untilMs], in dBFS. */
    fun meanLevelDb(fromMs: Long = startMs, untilMs: Long = endMs): Float? {
        val threshold = audibleThresholdDb
        val from = ((fromMs - startMs) / FRAME_MS).toInt().coerceIn(0, levelsDb.size)
        val until = ((untilMs - startMs) / FRAME_MS).toInt().coerceIn(from, levelsDb.size)
        val audible = levelsDb.subList(from, until).filter { it >= threshold }
        return if (audible.isEmpty()) null else audible.average().toFloat()
    }

    /**
     * Where a fade-out that is already in the recording begins: walking back from the last
     * audible moment, the first point that is within 3 dB of the loud part before it. Null when
     * the section simply stops.
     */
    fun fadeOutStartMs(): Long? {
        val end = lastAudibleEndMs() ?: return null
        val smoothed = smoothed()
        val endIndex = ((end - startMs) / FRAME_MS).toInt().coerceIn(1, smoothed.size) - 1
        val body = meanLevelDb(startMs, end) ?: return null
        var index = endIndex
        while (index > 0 && smoothed[index] < body - 3f) index -= 1
        val fadeLengthMs = (endIndex - index) * FRAME_MS
        return if (fadeLengthMs >= MIN_FADE_MS) startMs + index * FRAME_MS else null
    }

    private fun smoothed(): FloatArray {
        val out = FloatArray(levelsDb.size)
        val radius = SMOOTHING_FRAMES / 2
        for (i in levelsDb.indices) {
            var sum = 0f
            var count = 0
            for (j in maxOf(0, i - radius)..minOf(levelsDb.lastIndex, i + radius)) {
                sum += levelsDb[j]
                count += 1
            }
            out[i] = sum / count
        }
        return out
    }

    companion object {
        const val FRAME_MS: Long = 50L
        const val SILENCE_FLOOR_DB: Float = -90f
        private const val SMOOTHING_FRAMES = 9
        private const val MIN_FADE_MS = 1_500L

        /** [samples] are mono at [sampleRateHz]; [startMs] is where they sit in the track. */
        fun measure(samples: FloatArray, sampleRateHz: Int, startMs: Long): EnergyProfile {
            val frameSize = (sampleRateHz * FRAME_MS / 1000L).toInt().coerceAtLeast(1)
            val frames = samples.size / frameSize
            val levels = ArrayList<Float>(frames)
            for (f in 0 until frames) {
                var sum = 0.0
                val base = f * frameSize
                for (i in base until base + frameSize) {
                    val s = samples[i].toDouble()
                    sum += s * s
                }
                val rms = sqrt(sum / frameSize)
                val db = if (rms <= 0.0) SILENCE_FLOOR_DB else (20.0 * log10(rms)).toFloat()
                levels += db.coerceAtLeast(SILENCE_FLOOR_DB)
            }
            return EnergyProfile(startMs, levels)
        }
    }
}
