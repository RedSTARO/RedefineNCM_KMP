package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.PlayerStatus
import com.leejlredstar.redefinencm.kmp.data.toPlayerStatus
import com.leejlredstar.redefinencm.kmp.i18n.uiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueRestorationTest {
    private val original = listOf("duplicate", "middle", "duplicate", "last").map {
        MediaInfo(id = it, title = it, artist = "artist")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun savedShuffleRestoresBothOrdersAndTheExactDuplicateSlot() = runTest {
        val order = listOf(2, 3, 0, 1)
        val before = PlayerQueueSnapshot(
            items = order.map(original::get), currentIndex = 0, currentMedia = original[2],
            shuffleEnabled = true, originalItems = original, playOrder = order,
        )
        val saved = Json.decodeFromString<PlayerStatus>(Json.encodeToString(before.toPlayerStatus(4_321, true)))
        assertEquals(2, saved.index)
        val player = InMemoryPlatformPlayer(scope = backgroundScope)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val restorer = PlayerStatusRestorer(
            awaitSettings = {}, playerProvider = { player }, statusLoader = { Result.success(saved) },
            onReady = {}, playerDispatcher = dispatcher, workerDispatcher = dispatcher,
        )
        restorer.awaitRestored()
        val restored = player.queueSnapshot.value
        assertEquals(order, restored.playOrder)
        assertEquals(original.map { it.id }, restored.originalItems.map { it.id })
        assertEquals(before.items.map { it.id }, restored.items.map { it.id })
        assertEquals(0, restored.currentIndex)
        assertEquals(4_321L, player.position.value)
        assertFalse(player.isPlaying.value)

        player.setShuffleEnabled(false)
        assertEquals(original.map { it.id }, player.queue.value.map { it.id })
        assertEquals(2, player.currentIndex.value)
    }

    @Test
    fun oldAndMalformedPermutationsPreserveTheAvailableOrder() {
        for (order in listOf(null, listOf(1, 1, 1, 1), listOf(0), listOf(0, 1, 2, 99))) {
            val queue = PlayQueue.restore(original, 2, shuffle = true, playOrder = order)
            assertEquals(original, queue.itemsInPlayOrder)
            assertEquals(2, queue.currentIndex)
        }
        val legacy = Json.decodeFromString<PlayerStatus>("""{"playlist":[],"isShuffling":true}""")
        assertNull(legacy.playOrder)
    }

    @Test
    fun replacingAQueueKeepsTheChosenShuffleMode() {
        val player = InMemoryPlatformPlayer()
        try {
            player.setQueue(original)
            player.setShuffleEnabled(true)
            player.setQueue(original.reversed(), 1)
            assertTrue(player.shuffleEnabled.value)
            assertEquals(original.reversed()[1], player.currentMedia.value)
        } finally { player.release() }
    }

    @Test
    fun anEmptyQueueHasAnEmptyPersistedSnapshotAndNoOldSource() {
        val player = InMemoryPlatformPlayer()
        try {
            player.setQueue(original)
            PlaybackSource.set(uiText("old playlist"))
            player.clearQueue()
            assertNull(PlaybackSource.label.value)
            val saved = player.queueSnapshot.value.toPlayerStatus(0, false)
            assertTrue(saved.playlist.isEmpty())
            player.addToQueue(original.first())
            assertNull(PlaybackSource.label.value)

            PlaybackSource.set(uiText("another playlist"))
            player.removeFromQueue(0)
            assertNull(PlaybackSource.label.value)
        } finally {
            player.release()
            PlaybackSource.set(null)
        }
    }
}
