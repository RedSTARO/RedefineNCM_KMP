package com.leejlredstar.redefinencm.kmp.smtc

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacNowPlayingGateTest {
    private val playing = MediaControlMetadata(title = "Song", artist = "Artist", duration = 200_000L, position = 10_000L, isPlaying = true)

    @Test
    fun theFirstStateIsPushed() {
        assertTrue(macNowPlayingNeedsUpdate(null, 0L, playing, 0L))
    }

    @Test
    fun aPositionMovingWithTheClockIsLeftToTheSystem() {
        assertFalse(macNowPlayingNeedsUpdate(playing, 1_000L, playing.copy(position = 10_100L), 1_100L))
        assertFalse(macNowPlayingNeedsUpdate(playing, 1_000L, playing.copy(position = 15_000L), 6_000L))
    }

    @Test
    fun aSeekAPauseOrAnotherTrackIsPushed() {
        assertTrue(macNowPlayingNeedsUpdate(playing, 1_000L, playing.copy(position = 90_000L), 1_100L))
        assertTrue(macNowPlayingNeedsUpdate(playing, 1_000L, playing.copy(isPlaying = false), 1_100L))
        assertTrue(macNowPlayingNeedsUpdate(playing, 1_000L, playing.copy(title = "Next", position = 0L), 1_100L))
    }

    @Test
    fun aPausedPositionThatDoesNotMoveIsNotPushedAgain() {
        val paused = playing.copy(isPlaying = false)
        assertFalse(macNowPlayingNeedsUpdate(paused, 1_000L, paused, 60_000L))
    }
}
