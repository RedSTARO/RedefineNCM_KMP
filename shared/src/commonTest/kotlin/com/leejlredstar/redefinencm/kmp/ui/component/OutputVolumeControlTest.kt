package com.leejlredstar.redefinencm.kmp.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class OutputVolumeControlTest {
    @Test
    fun speakerGlyphFollowsTheDisplayedPercentageWithTheOuterWaveFromHalf() {
        assertEquals(OutputVolumeLevel.MUTED, outputVolumeLevel(0f))
        assertEquals(OutputVolumeLevel.MUTED, outputVolumeLevel(-0.5f))
        assertEquals(OutputVolumeLevel.MUTED, outputVolumeLevel(Float.NaN))
        // Reads 0%, so it is drawn muted even though the player is not exactly silent.
        assertEquals(OutputVolumeLevel.MUTED, outputVolumeLevel(0.004f))
        assertEquals(OutputVolumeLevel.LOW, outputVolumeLevel(0.01f))
        assertEquals(OutputVolumeLevel.LOW, outputVolumeLevel(0.49f))
        assertEquals(OutputVolumeLevel.HIGH, outputVolumeLevel(0.5f))
        assertEquals(OutputVolumeLevel.HIGH, outputVolumeLevel(1f))
        assertEquals(OutputVolumeLevel.HIGH, outputVolumeLevel(1.5f))
    }

    @Test
    fun labelIsTheWholePercentageThePlayerPersists() {
        assertEquals("0%", formatOutputVolumePercent(0f))
        assertEquals("0%", formatOutputVolumePercent(-1f))
        assertEquals("0%", formatOutputVolumePercent(Float.NaN))
        assertEquals("72%", formatOutputVolumePercent(0.72f))
        assertEquals("100%", formatOutputVolumePercent(1f))
        assertEquals("100%", formatOutputVolumePercent(1.7f))
    }
}
