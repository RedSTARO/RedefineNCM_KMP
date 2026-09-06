package com.leejlredstar.redefinencm.kmp.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingScreenTest {

    @Test
    fun clockFormatsMinutesAndSecondsWithPaddedSeconds() {
        assertEquals("0:00", formatNowPlayingClock(0L))
        assertEquals("0:07", formatNowPlayingClock(7_999L))
        assertEquals("4:16", formatNowPlayingClock(256_000L))
        assertEquals("59:59", formatNowPlayingClock(3_599_999L))
    }

    @Test
    fun clockAddsAnHourFieldPastSixtyMinutes() {
        assertEquals("1:00:00", formatNowPlayingClock(3_600_000L))
        assertEquals("1:02:03", formatNowPlayingClock(3_723_000L))
        assertEquals("12:00:05", formatNowPlayingClock(43_205_000L))
    }

    @Test
    fun clockClampsNegativePositionsToZero() {
        // Some backends report a negative position between selecting and opening a track.
        assertEquals("0:00", formatNowPlayingClock(-1_500L))
    }
}
