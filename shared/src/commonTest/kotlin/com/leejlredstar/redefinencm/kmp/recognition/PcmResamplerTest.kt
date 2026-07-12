package com.leejlredstar.redefinencm.kmp.recognition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PcmResamplerTest {
    @Test
    fun cropsNativeEightKilohertzInputWithoutChangingSamples() {
        val source = FloatArray(24_100) { index -> index / 24_100f }

        val prepared = prepareRecognitionSamples(CapturedPcm(source, 8_000))

        assertContentEquals(source.copyOf(24_000), prepared)
    }

    @Test
    fun rejectsShortOrNonFiniteCapture() {
        assertFailsWith<IllegalArgumentException> {
            prepareRecognitionSamples(CapturedPcm(FloatArray(23_999), 8_000))
        }
        val withNaN = FloatArray(24_000).apply { this[12_345] = Float.NaN }
        assertFailsWith<IllegalArgumentException> {
            prepareRecognitionSamples(CapturedPcm(withNaN, 8_000))
        }
    }

    @Test
    fun downsampledToneKeepsFrequencyAndExactTargetLength() {
        val sourceRate = 48_000
        val frequency = 440.0
        val source = FloatArray(sourceRate * 3) { index ->
            sin(2.0 * PI * frequency * index / sourceRate).toFloat()
        }

        val prepared = prepareRecognitionSamples(CapturedPcm(source, sourceRate))

        assertEquals(24_000, prepared.size)
        var absoluteError = 0.0
        for (index in 100 until prepared.size - 100) {
            val expected = sin(2.0 * PI * frequency * index / 8_000.0)
            absoluteError += abs(prepared[index] - expected)
        }
        val meanAbsoluteError = absoluteError / (prepared.size - 200)
        assertTrue(meanAbsoluteError < 0.01, "mean absolute error was $meanAbsoluteError")
    }
}
