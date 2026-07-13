package com.leejlredstar.redefinencm.kmp.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackDurationTest {
    @Test
    fun formatsNonNegativeWholeSecondPlaybackTime() {
        assertEquals("0:00", formatPlaybackDuration(-1L))
        assertEquals("0:59", formatPlaybackDuration(59_999L))
        assertEquals("61:01", formatPlaybackDuration(3_661_000L))
    }
}
