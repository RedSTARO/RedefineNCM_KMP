package com.leejlredstar.redefinencm.kmp

import com.leejlredstar.redefinencm.kmp.player.IosPlaybackEndTransition
import com.leejlredstar.redefinencm.kmp.player.PlayQueue
import com.leejlredstar.redefinencm.kmp.player.PlayerState
import com.leejlredstar.redefinencm.kmp.player.iosPlaybackEndTransition
import com.leejlredstar.redefinencm.kmp.util.IosDownloadLifecycleState
import com.leejlredstar.redefinencm.kmp.util.iosDownloadCancelledDecision
import com.leejlredstar.redefinencm.kmp.util.iosDownloadDidFinishDecision
import com.leejlredstar.redefinencm.kmp.util.iosDownloadFilePersisted
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
    fun cancelledBackgroundDownloadCannotPersistWhenFinishArrivesLater() {
        val cancelled = iosDownloadCancelledDecision(IosDownloadLifecycleState())

        val lateFinish = iosDownloadDidFinishDecision(cancelled.state)

        assertFalse(lateFinish.shouldPersistTemporaryFile)
    }

    @Test
    fun cancellationAfterFinishRequiresPersistedFileDeletion() {
        val persisted = iosDownloadFilePersisted(IosDownloadLifecycleState())

        val cancelled = iosDownloadCancelledDecision(persisted)

        assertTrue(cancelled.shouldDeletePersistedFile)
        assertFalse(cancelled.state.filePersisted)
    }
}
