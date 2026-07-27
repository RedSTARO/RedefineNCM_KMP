package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.PersistedMediaItem
import com.leejlredstar.redefinencm.kmp.data.PlayerStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class PlayerStatusRestorerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun restoresQueueBeforeReadyAndKeepsPlaybackPaused() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val player = InMemoryPlatformPlayer(scope = backgroundScope)
        val status = PlayerStatus(
            playlist = listOf(
                PersistedMediaItem(
                    id = "123",
                    title = "restored",
                    artist = "artist",
                    duration = 120_000,
                ),
            ),
            index = 0,
            position = 4_321,
            isPlaying = true,
        )
        var loads = 0
        val restorer = PlayerStatusRestorer(
            awaitSettings = {},
            playerProvider = { player },
            statusLoader = {
                loads += 1
                Result.success(status)
            },
            onReady = {},
            playerDispatcher = dispatcher,
            workerDispatcher = dispatcher,
        )

        val restored = restorer.awaitRestored()

        assertIs<PlayerStatusRestoreState.Restored>(restored)
        assertEquals(1, loads)
        assertEquals("123", player.currentMedia.value?.id)
        assertEquals("123", player.queueSnapshot.value.currentMedia?.id)
        assertEquals(4_321, player.position.value)
        assertFalse(player.isPlaying.value)
    }
}
