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

    @Test
    fun anEmptyTimelineNeverStartsAnOccurrence() {
        assertFalse(shouldAdvanceMedia3PlaybackOccurrence(
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED, null, "removed-slot",
        ))
    }
}
