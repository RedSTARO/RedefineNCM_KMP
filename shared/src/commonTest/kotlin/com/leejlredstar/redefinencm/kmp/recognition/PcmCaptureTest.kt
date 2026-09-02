package com.leejlredstar.redefinencm.kmp.recognition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The PCM arithmetic the four microphone recorders used to each carry their own copy of.
 *
 * Every one of these was previously exercised only by running the app on a device with a real
 * microphone, which is why the copies were free to disagree.
 */
class PcmCaptureTest {

    @Test
    fun targetSampleCountRoundsUpSoAFullDurationIsAlwaysCaptured() {
        assertEquals(44_100, pcmTargetSampleCount(1_000L, 44_100))
        assertEquals(441, pcmTargetSampleCount(10L, 44_100))
        // 8000 Hz for 1 ms is 8 samples exactly; 1 ms at 44100 Hz is 44.1 and must not truncate.
        assertEquals(8, pcmTargetSampleCount(1L, 8_000))
        assertEquals(45, pcmTargetSampleCount(1L, 44_100))
    }

    @Test
    fun elapsedMillisMatchesTheSampleCountAtTheCaptureRate() {
        assertEquals(0L, pcmElapsedMillis(0, 44_100))
        assertEquals(1_000L, pcmElapsedMillis(44_100, 44_100))
        assertEquals(500L, pcmElapsedMillis(22_050, 44_100))
    }

    @Test
    fun int16ScalesIntoTheDocumentedRange() {
        assertEquals(0f, 0.toShort().toPcmSample())
        assertEquals(-1f, Short.MIN_VALUE.toPcmSample())
        // 32767 / 32768 — full positive scale is just short of 1f, which is why the level
        // meter clamps rather than assuming the maximum is exactly reachable.
        assertTrue(Short.MAX_VALUE.toPcmSample() < 1f)
        assertTrue(Short.MAX_VALUE.toPcmSample() > 0.999f)
    }

    @Test
    fun decodesLittleEndianFramesTheWayEveryPlatformBufferLaysThemOut() {
        // 0x0000, 0x7FFF, 0x8000, 0xFFFF as little-endian pairs.
        val bytes = byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0x7F, 0x00, 0x80.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals(0f, pcmSampleAt(bytes, 0))
        assertEquals(Short.MAX_VALUE.toPcmSample(), pcmSampleAt(bytes, 2))
        assertEquals(-1f, pcmSampleAt(bytes, 4))
        assertEquals((-1).toShort().toPcmSample(), pcmSampleAt(bytes, 6))
    }

    @Test
    fun rmsLevelIsTheRootMeanSquareClampedToTheMeterRange() {
        // Four samples of 0.5 => energy 1.0, RMS 0.5.
        assertEquals(0.5f, pcmRmsLevel(energy = 1.0, sampleCount = 4))
        assertEquals(1f, pcmRmsLevel(energy = 1.0, sampleCount = 1))
        // Above full scale is clamped rather than reported as a level greater than one.
        assertEquals(1f, pcmRmsLevel(energy = 100.0, sampleCount = 1))
    }

    @Test
    fun rmsLevelReportsSilenceForAnEmptyReadInsteadOfNaN() {
        // A read that returns no frames must not divide by zero: the JVM backend polls a line
        // that can legitimately hand back nothing.
        assertEquals(0f, pcmRmsLevel(energy = 0.0, sampleCount = 0))
    }
}
