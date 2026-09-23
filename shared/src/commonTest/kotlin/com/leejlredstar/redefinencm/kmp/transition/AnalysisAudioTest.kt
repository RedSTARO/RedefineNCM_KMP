package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisAudioTest {

    private fun stereoSine(rate: Int, seconds: Double, hz: Double): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * 2) { i -> (0.5 * sin(2 * PI * hz * (i / 2) / rate)).toFloat() }
    }

    @Test
    fun resamplingKeepsTheToneAndTheDuration() {
        val input = stereoSine(44_100, 2.0, 1_000.0)
        val resampler = MonoResampler(44_100, 22_050, 2)
        // Fed in uneven blocks, the way a decoder hands them over.
        val out = ArrayList<Float>()
        var at = 0
        var block = 1_111
        while (at < input.size / 2) {
            val frames = minOf(block, input.size / 2 - at)
            resampler.process(input.copyOfRange(at * 2, (at + frames) * 2), frames).forEach { out += it }
            at += frames
            block = if (block == 1_111) 377 else 1_111
        }
        assertTrue(abs(out.size - 44_100) < 40, "length ${out.size}")
        // Amplitude of a 1 kHz sine survives the low-pass (RMS of 0.5 amplitude is 0.354).
        val middle = out.subList(2_000, 40_000)
        val rms = sqrt(middle.sumOf { (it * it).toDouble() } / middle.size)
        assertTrue(abs(rms - 0.3536) < 0.01, "rms $rms")
        // A tone above the new Nyquist frequency is removed rather than folded back.
        val alias = MonoResampler(44_100, 22_050, 2).process(stereoSine(44_100, 1.0, 15_000.0), 44_100)
        val aliasRms = sqrt(alias.drop(1_000).sumOf { (it * it).toDouble() } / (alias.size - 1_000))
        assertTrue(aliasRms < 0.01, "alias rms $aliasRms")
    }

    @Test
    fun seekErrorIsFoundByMatchingSamples() {
        val rate = BeatModelFeatures.SAMPLE_RATE_HZ
        var seed = 7L
        val head = FloatArray(rate * 25) {
            seed = (seed * 1103515245L + 12345L) % 2147483648L
            ((seed / 2147483648.0) * 2 - 1).toFloat() * 0.3f
        }
        // The probe really starts 26.1 ms before where the "decoder" claims.
        val trueStart = (20.2 * rate).toInt() - (0.0261 * rate).toInt()
        val probe = head.copyOfRange(trueStart, trueStart + rate)
        val error = measureSeekErrorMs(head, probe, claimedStartMs = 20_200.0, sampleRate = rate)!!
        assertTrue(abs(error - 26.1) < 0.1, "error $error")
        // Silence cannot be placed.
        assertNull(measureSeekErrorMs(head, FloatArray(rate), 20_200.0, rate))
        // A steady tone, decoded with a little noise of its own, matches every period equally
        // well and is refused.
        val tone = FloatArray(rate * 25) { (0.3 * sin(2 * PI * 441.0 * it / rate)).toFloat() }
        var noiseSeed = 99L
        val noisyProbe = FloatArray(rate) {
            noiseSeed = (noiseSeed * 1103515245L + 12345L) % 2147483648L
            tone[20 * rate + it] + ((noiseSeed / 2147483648.0) * 2 - 1).toFloat() * 1e-3f
        }
        assertNull(measureSeekErrorMs(tone, noisyProbe, 20_010.0, rate))
        assertEquals(0.0, measureSeekErrorMs(head, head.copyOfRange(20 * rate, 21 * rate), 20_000.0, rate))
    }
}
