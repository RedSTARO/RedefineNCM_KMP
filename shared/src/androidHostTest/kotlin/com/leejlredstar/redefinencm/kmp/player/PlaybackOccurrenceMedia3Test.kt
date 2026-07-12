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
                currentWindowIndex = 1,
                previousWindowIndex = 0,
            ),
        )
    }

    @Test
    fun seekCountsOnlyWhenItSelectsAnotherWindow() {
        assertFalse(
            shouldAdvanceMedia3PlaybackOccurrence(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                currentWindowIndex = 1,
                previousWindowIndex = 1,
            ),
        )
        assertTrue(
            shouldAdvanceMedia3PlaybackOccurrence(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
                currentWindowIndex = 2,
                previousWindowIndex = 1,
            ),
        )
    }

    @Test
    fun naturalAndRepeatTransitionsAlwaysCreateOccurrences() {
        assertTrue(
            shouldAdvanceMedia3PlaybackOccurrence(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                currentWindowIndex = 1,
                previousWindowIndex = 1,
            ),
        )
        assertTrue(
            shouldAdvanceMedia3PlaybackOccurrence(
                reason = Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
                currentWindowIndex = 1,
                previousWindowIndex = 1,
            ),
        )
    }
}
