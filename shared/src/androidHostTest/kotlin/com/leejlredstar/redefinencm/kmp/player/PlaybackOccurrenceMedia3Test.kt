package com.leejlredstar.redefinencm.kmp.player

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackOccurrenceMedia3Test {

    @Test
    fun playlistMutationDoesNotDuplicateSynchronousSelectionOccurrence() {
        assertFalse(
            shouldAdvanceMedia3PlaybackOccurrence(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
                currentWindowUid = "new-queue-slot",
                previousWindowUid = "old-queue-slot",
                explicitSelection = true,
            ),
        )
    }

    @Test
    fun seekCountsOnlyWhenItSelectsAnotherWindow() {
        assertFalse(
            shouldAdvanceMedia3PlaybackOccurrence(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                currentWindowUid = "same-slot",
                previousWindowUid = "same-slot",
            ),
        )
        assertTrue(
            shouldAdvanceMedia3PlaybackOccurrence(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                currentWindowUid = "next-slot",
                previousWindowUid = "same-slot",
            ),
        )
    }

    @Test
    fun naturalAndRepeatTransitionsAlwaysCreateOccurrences() {
        assertTrue(
            shouldAdvanceMedia3PlaybackOccurrence(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                currentWindowUid = "same-slot",
                previousWindowUid = "same-slot",
            ),
        )
        assertTrue(
            shouldAdvanceMedia3PlaybackOccurrence(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
                currentWindowUid = "same-slot",
                previousWindowUid = "same-slot",
            ),
        )
    }

    @Test
    fun movingAnItemDoesNotChangeItsSelectionButRemovingItDoes() {
        assertFalse(shouldAdvanceMedia3PlaybackOccurrence(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED, "kept-slot", "kept-slot",
        ))
        // The successor occupies the same numeric index after removal; its identity differs.
        assertTrue(shouldAdvanceMedia3PlaybackOccurrence(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED, "successor-slot", "removed-slot",
        ))
        assertTrue(shouldAdvanceMedia3PlaybackOccurrence(
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK, "next-slot", "moved-slot",
        ))
    }

    /** Play, next, previous and a queue row must recover a player a failed stream stopped. */
    @Test
    fun onlyAnIdlePlayerWithItemsIsPreparedAgain() {
        assertTrue(needsPrepareBeforePlay(Player.STATE_IDLE, mediaItemCount = 3))
        assertFalse(needsPrepareBeforePlay(Player.STATE_IDLE, mediaItemCount = 0))
        assertFalse(needsPrepareBeforePlay(Player.STATE_READY, mediaItemCount = 3))
        assertFalse(needsPrepareBeforePlay(Player.STATE_BUFFERING, mediaItemCount = 3))
        assertFalse(needsPrepareBeforePlay(Player.STATE_ENDED, mediaItemCount = 3))
    }

    /** A pause the app did not make must not leave a blend's second player sounding. */
    @Test
    fun theAppFollowsPausesItDidNotMakeOnlyWhenTheyWouldLeaveSoundBehind() {
        assertTrue(pausesWithApp(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS, blendRunning = false))
        assertTrue(pausesWithApp(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY, blendRunning = false))
        assertTrue(pausesWithApp(Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST, blendRunning = true))
        assertFalse(pausesWithApp(Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST, blendRunning = false))
        assertFalse(pausesWithApp(Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM, blendRunning = true))
    }

    @Test
    fun aPlanWaitsForPlaybackButABlendRunsToItsEnd() {
        assertTrue(transitionLoopShouldRun(hasPlan = false, blending = true, playing = false))
        assertTrue(transitionLoopShouldRun(hasPlan = true, blending = false, playing = true))
        assertFalse(transitionLoopShouldRun(hasPlan = true, blending = false, playing = false))
        assertFalse(transitionLoopShouldRun(hasPlan = false, blending = false, playing = true))
    }

    @Test
    fun anEmptyTimelineNeverStartsAnOccurrence() {
        assertFalse(shouldAdvanceMedia3PlaybackOccurrence(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED, null, "removed-slot",
        ))
    }
}
