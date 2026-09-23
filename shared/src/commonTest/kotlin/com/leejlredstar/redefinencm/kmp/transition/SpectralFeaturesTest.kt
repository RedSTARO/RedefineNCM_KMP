package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpectralFeaturesTest {

    /**
     * The same two seconds `beat_this.preprocessing.LogMelSpect` (torchaudio 2.11) was given:
     * two sines plus LCG noise, so the expected values below are reproducible without audio.
     */
    private fun referenceSignal(): FloatArray {
        val n = BeatModelFeatures.SAMPLE_RATE_HZ * 2
        var state = 12345L
        return FloatArray(n) { i ->
            state = (state * 1103515245L + 12345L) % 2147483648L
            val noise = state / 2147483648.0 * 2.0 - 1.0
            val sr = BeatModelFeatures.SAMPLE_RATE_HZ.toDouble()
            (0.5 * sin(2 * PI * 440.0 * i / sr) + 0.3 * sin(2 * PI * 1234.5 * i / sr + 0.7) + 0.1 * noise)
                .toFloat()
        }
    }

    @Test
    fun logMelMatchesTorchaudio() {
        val features = SpectralAnalyzer().analyze(referenceSignal())
        val mel = features.mel
        assertEquals(101, mel.frameCount)
        val expected = mapOf(
            (0 to 0) to 5.751915, (0 to 10) to 6.467287, (0 to 64) to 4.904837, (0 to 127) to 5.857434,
            (1 to 0) to 3.361799, (1 to 10) to 3.460120, (1 to 64) to 3.955991, (1 to 127) to 6.186287,
            (50 to 0) to 3.213699, (50 to 10) to 3.071952, (50 to 64) to 4.000207, (50 to 127) to 6.360195,
            (100 to 0) to 6.054818, (100 to 10) to 6.520454, (100 to 64) to 3.973135, (100 to 127) to 5.761363,
        )
        for ((position, value) in expected) {
            val (frame, bin) = position
            val actual = mel.values[frame * BeatModelFeatures.MEL_BINS + bin]
            assertTrue(abs(actual - value) < 2e-3, "frame $frame bin $bin: $actual != $value")
        }
        val sum = mel.values.sumOf { it.toDouble() }
        assertTrue(abs(sum - 60470.970599889755) / 60470.97 < 1e-5, "sum $sum")
    }

    @Test
    fun chromaFindsTheSineTonic() {
        val sr = BeatModelFeatures.SAMPLE_RATE_HZ.toDouble()
        // A3, C#4 and E4: an A major triad.
        val signal = FloatArray(BeatModelFeatures.SAMPLE_RATE_HZ * 3) { i ->
            (sin(2 * PI * 220.0 * i / sr) + sin(2 * PI * 277.18 * i / sr) + sin(2 * PI * 329.63 * i / sr))
                .toFloat() / 3f
        }
        val chroma = SpectralAnalyzer().analyze(signal).chroma
        val strongest = chroma.indices.sortedByDescending { chroma[it] }.take(3).toSet()
        assertEquals(setOf(9, 1, 4), strongest)
    }

    @Test
    fun slaneyMelRoundTrips() {
        for (hz in listOf(30.0, 500.0, 1000.0, 4321.0, 11000.0)) {
            assertTrue(abs(slaneyMelToHz(hzToSlaneyMel(hz)) - hz) < 1e-6)
        }
    }
}
