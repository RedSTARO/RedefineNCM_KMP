package com.leejlredstar.redefinencm.kmp

import com.leejlredstar.redefinencm.kmp.player.IosPlaybackEndTransition
import com.leejlredstar.redefinencm.kmp.player.PlayQueue
import com.leejlredstar.redefinencm.kmp.player.PlayerState
import com.leejlredstar.redefinencm.kmp.player.iosPlaybackEndTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

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

}
