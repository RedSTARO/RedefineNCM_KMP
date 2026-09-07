package com.leejlredstar.redefinencm.kmp.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackClockTest {

    @Test
    fun compactClockFormatsNonNegativeWholeSecondPlaybackTime() {
        assertEquals("0:00", formatPlaybackDuration(-1L))
        assertEquals("0:59", formatPlaybackDuration(59_999L))
    }

    @Test
    fun compactClockKeepsCountingMinutesPastAnHour() {
        assertEquals("61:01", formatPlaybackDuration(3_661_000L))
    }

    @Test
    fun fullClockFormatsMinutesAndSecondsWithPaddedSeconds() {
        assertEquals("0:00", formatPlaybackClock(0L))
        assertEquals("0:07", formatPlaybackClock(7_999L))
        assertEquals("4:16", formatPlaybackClock(256_000L))
        assertEquals("59:59", formatPlaybackClock(3_599_999L))
    }

    @Test
    fun fullClockAddsAnHourFieldPastSixtyMinutes() {
        assertEquals("1:00:00", formatPlaybackClock(3_600_000L))
        assertEquals("1:02:03", formatPlaybackClock(3_723_000L))
        assertEquals("12:00:05", formatPlaybackClock(43_205_000L))
    }

    @Test
    fun bothClocksClampNegativePositionsToZero() {
        // Some backends report a negative position between selecting and opening a track.
        assertEquals("0:00", formatPlaybackClock(-1_500L))
        assertEquals("0:00", formatPlaybackDuration(-1_500L))
    }
}
