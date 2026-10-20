package com.leejlredstar.redefinencm.kmp

import com.leejlredstar.redefinencm.kmp.player.IosPlaybackEndTransition
import com.leejlredstar.redefinencm.kmp.player.PlayQueue
import com.leejlredstar.redefinencm.kmp.player.PlayerState
import com.leejlredstar.redefinencm.kmp.player.IOS_REOPEN_PROGRESS_MS
import com.leejlredstar.redefinencm.kmp.player.IosPlaybackStatus
import com.leejlredstar.redefinencm.kmp.player.iosMayReopenStream
import com.leejlredstar.redefinencm.kmp.player.iosPlaybackEndTransition
import com.leejlredstar.redefinencm.kmp.player.iosPlaybackStatus
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharedLogicIOSTest {

    @Test
    fun iosPlaybackEndStopsAtLastTrackWithoutWrapping() {
        val queue = PlayQueue.of(listOf("first", "last"), startIndex = 1)

        val transition = iosPlaybackEndTransition(queue, durationMs = 12_345L)

        val ended = assertIs<IosPlaybackEndTransition.Ended>(transition)
        assertEquals(PlayerState.ENDED, ended.state)
        assertFalse(ended.isPlaying)
        assertEquals(12_345L, ended.positionMs)
    }

    @Test
    fun iosPlaybackEndAdvancesWhenAnotherTrackExists() {
        val queue = PlayQueue.of(listOf("first", "second"), startIndex = 0)

        val transition = iosPlaybackEndTransition(queue, durationMs = 12_000L)

        val advance = assertIs<IosPlaybackEndTransition.Advance<String>>(transition)
        assertEquals("second", advance.queue.currentItem)
    }

    @Test
    fun waitingForDataIsBufferingNotPlaying() {
        // AVPlayer's rate stays 1 while it waits, so only the time control status tells.
        assertEquals(
            IosPlaybackStatus.BUFFERING,
            status(AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate, sincePlayRequestMs = 5_000L),
        )
        assertEquals(IosPlaybackStatus.PLAYING, status(AVPlayerTimeControlStatusPlaying))
    }

    @Test
    fun aPauseJustAfterAskingToPlayIsStillStarting() {
        assertEquals(IosPlaybackStatus.BUFFERING, status(AVPlayerTimeControlStatusPaused, sincePlayRequestMs = 100L))
    }

    @Test
    fun aStopAtTheEndIsLeftToTheEndNotification() {
        assertEquals(
            IosPlaybackStatus.ENDING,
            status(AVPlayerTimeControlStatusPaused, positionMs = 199_500L, durationMs = 200_000L),
        )
    }

    @Test
    fun aStopMidTrackThatNobodyAskedForIsTheSystems() {
        assertEquals(
            IosPlaybackStatus.STOPPED_BY_SYSTEM,
            status(AVPlayerTimeControlStatusPaused, positionMs = 60_000L, durationMs = 200_000L),
        )
        assertEquals(
            IosPlaybackStatus.PAUSED,
            status(AVPlayerTimeControlStatusPaused, playRequested = false, positionMs = 60_000L),
        )
    }

    @Test
    fun aFailedStreamIsReopenedOnceUntilItPlaysOn() {
        assertTrue(iosMayReopenStream(reopenedAtMs = null, positionMs = 0L))
        assertFalse(iosMayReopenStream(reopenedAtMs = 30_000L, positionMs = 30_000L))
        assertFalse(iosMayReopenStream(reopenedAtMs = 30_000L, positionMs = 35_000L))
        assertTrue(iosMayReopenStream(reopenedAtMs = 30_000L, positionMs = 30_000L + IOS_REOPEN_PROGRESS_MS))
    }

    private fun status(
        timeControlStatus: Long,
        playRequested: Boolean = true,
        sincePlayRequestMs: Long? = 5_000L,
        positionMs: Long = 10_000L,
        durationMs: Long = 200_000L,
    ) = iosPlaybackStatus(timeControlStatus, playRequested, sincePlayRequestMs, positionMs, durationMs)
}
