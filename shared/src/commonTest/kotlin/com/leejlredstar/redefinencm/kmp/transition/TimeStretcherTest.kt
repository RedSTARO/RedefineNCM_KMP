package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeStretcherTest {
    private val rate = 44_100

    /** A stereo chord with a slow amplitude wobble, so no two windows are exactly alike. */
    private fun music(frames: Int): FloatArray = FloatArray(frames * 2) { i ->
        val t = (i / 2).toDouble() / rate
        val wobble = 0.6 + 0.4 * sin(2 * PI * 0.7 * t)
        val tone = sin(2 * PI * 220.0 * t) + 0.5 * sin(2 * PI * 330.0 * t + 1.0) + 0.25 * sin(2 * PI * 551.0 * t)
        (wobble * tone * if (i % 2 == 0) 0.3 else 0.25).toFloat()
    }

    private fun stretch(stretcher: TimeStretcher, input: FloatArray, from: Int, frames: Int, block: Int = 1_000): FloatArray {
        val out = ArrayList<Float>()
        val buffer = FloatArray(block * 2)
        var position = from
        val end = from + frames
        while (true) {
            val wanted = stretcher.inputFramesWanted(block)
            if (wanted > 0 && position < end) {
                val take = minOf(wanted, end - position)
                stretcher.write(input.copyOfRange(position * 2, (position + take) * 2), take)
                position += take
                if (position >= end) stretcher.endOfInput()
            }
            val got = stretcher.read(buffer, block)
            for (i in 0 until got * 2) out += buffer[i]
            if (got == 0 && position >= end) break
        }
        return out.toFloatArray()
    }

    @Test
    fun primedAtRateOneItContinuesItsInputExactly() {
        val input = music(rate * 3)
        val stretcher = TimeStretcher(2, rate)
        val history = stretcher.frameSize
        val start = rate // one second in
        stretcher.prime(input.copyOfRange((start - history) * 2, start * 2), history)
        val out = stretch(stretcher, input, start, rate)
        // The first second after the switch point is the input itself.
        for (i in 0 until rate / 2 * 2) {
            assertTrue(abs(out[i] - input[start * 2 + i]) < 1e-5, "sample $i: ${out[i]} vs ${input[start * 2 + i]}")
        }
    }

    @Test
    fun rateChangesDurationButNotPitch() {
        val input = music(rate * 4)
        for (r in listOf(0.95, 1.05)) {
            val stretcher = TimeStretcher(2, rate).apply { this.rate = r }
            val out = stretch(stretcher, input, 0, rate * 4)
            val frames = out.size / 2
            val expected = rate * 4 / r
            assertTrue(abs(frames - expected) / expected < 0.02, "rate $r: $frames frames, expected $expected")
            // Pitch: the 220 Hz fundamental's zero crossings per second are unchanged.
            val left = FloatArray(frames) { out[it * 2] }
            val sourceLeft = FloatArray(rate * 4) { input[it * 2] }
            val measured = zeroCrossingRate(left, rate / 2, rate * 2)
            val original = zeroCrossingRate(sourceLeft, rate / 2, rate * 2)
            assertTrue(abs(measured - original) / original < 0.03, "rate $r: crossings $measured vs $original")
        }
    }

    private fun zeroCrossingRate(signal: FloatArray, from: Int, length: Int): Double {
        var crossings = 0
        for (i in from + 1 until minOf(signal.size, from + length)) {
            if ((signal[i - 1] < 0f) != (signal[i] < 0f)) crossings += 1
        }
        return crossings.toDouble() / length
    }

    @Test
    fun outputStaysBounded() {
        val input = music(rate * 2)
        val stretcher = TimeStretcher(2, rate).apply { this.rate = 1.06 }
        val out = stretch(stretcher, input, 0, rate * 2)
        val peakIn = input.maxOf { abs(it) }
        val peakOut = out.maxOf { abs(it) }
        assertTrue(peakOut < peakIn * 1.5f, "peak $peakOut vs $peakIn")
        assertEquals(0, out.count { it.isNaN() })
    }
}
