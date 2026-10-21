package com.leejlredstar.redefinencm.kmp.recognition

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmMicrophoneSilenceTest {
    @Test
    fun exactZerosAreWhatABlockedMicrophoneDelivers() {
        assertTrue(isDigitalSilence(ByteArray(48_000)))
    }

    @Test
    fun theFaintestNoiseIsARealRecording() {
        // One least-significant bit in three seconds of 8 kHz mono is enough.
        val pcm = ByteArray(48_000).also { it[31_337] = 1 }
        assertFalse(isDigitalSilence(pcm))
    }

    @Test
    fun nothingRecordedIsNotCalledSilence() {
        assertFalse(isDigitalSilence(ByteArray(0)))
    }
}
