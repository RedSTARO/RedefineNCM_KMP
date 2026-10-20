package com.leejlredstar.redefinencm.kmp.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** ExoPlayer's shuffle keeps PlayQueue's rules (PlayQueueTest), so Android plays what the others do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class CurrentFirstShuffleOrderTest {

    private fun ShuffleOrder.walk(): List<Int> {
        val order = mutableListOf<Int>()
        var index = firstIndex
        while (index != C.INDEX_UNSET) {
            order += index
            index = getNextIndex(index)
        }
        assertEquals(order.reversed(), walkBack(), "previous must retrace next")
        return order
    }

    private fun ShuffleOrder.walkBack(): List<Int> {
        val order = mutableListOf<Int>()
        var index = lastIndex
        while (index != C.INDEX_UNSET) {
            order += index
            index = getPreviousIndex(index)
        }
        return order
    }

    @Test
    fun theCurrentItemPlaysFirstAndEveryOtherOnce() {
        repeat(20) { seed ->
            val order = CurrentFirstShuffleOrder.startingWith(current = 5, size = 12, random = Random(seed)).walk()
            assertEquals(5, order.first())
            assertEquals((0 until 12).toList(), order.sorted())
        }
    }

    @Test
    fun aNewListStartsWhereExoPlayerSaysItStarts() {
        val order = CurrentFirstShuffleOrder.startingWith(C.INDEX_UNSET, 0).cloneAndSet(8, 3)
        assertEquals(3, order.walk().first())
        assertEquals(8, order.length)
    }

    @Test
    fun addedItemsPlayLastAndTheRestKeepTheirPlaces() {
        val order = CurrentFirstShuffleOrder.of(listOf(2, 0, 1))
        // Two items inserted at window 1: windows 1 and 2 move to 3 and 4.
        assertEquals(listOf(4, 0, 3, 1, 2), order.cloneAndInsert(1, 2).walk())
        // Appended at the end, as addToQueue does.
        assertEquals(listOf(2, 0, 1, 3), order.cloneAndInsert(3, 1).walk())
    }

    @Test
    fun removingItemsKeepsTheOrderOfTheRest() {
        val order = CurrentFirstShuffleOrder.of(listOf(4, 0, 3, 1, 2))
        assertEquals(listOf(2, 0, 1), order.cloneAndRemove(1, 3).walk())
        assertEquals(emptyList(), order.cloneAndClear().walk())
    }

    @Test
    fun movingItemsChangesTheirWindowsNotWhenTheyPlay() {
        // Windows a,b,c,d,e = 0..4, played e,a,d,b,c. Moving b,c (1..3) to the end gives a,d,e,b,c.
        val order = CurrentFirstShuffleOrder.of(listOf(4, 0, 3, 1, 2))
        assertEquals(listOf(2, 0, 1, 3, 4), order.cloneAndMove(1, 3, 3).walk())
    }

    @Test
    fun anOrderThatIsNotAPermutationStartsAgainFromItsFirstItem() {
        val order = CurrentFirstShuffleOrder.of(listOf(1, 1, 0)).walk()
        assertEquals(1, order.first())
        assertEquals(listOf(0, 1, 2), order.sorted())
    }

    @Test
    fun exoPlayerPlaysTheChosenSongFirstUnderShuffle() {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            player.setShuffleOrder(CurrentFirstShuffleOrder.startingWith(C.INDEX_UNSET, 0))
            player.shuffleModeEnabled = true
            val items = (0 until 10).map { MediaItem.Builder().setMediaId("$it").setUri("redefinencm://playbackPlaceHolder?id=$it").build() }

            player.setMediaItems(items, 5, 0L)
            assertEquals(5, player.currentTimeline.getFirstWindowIndex(true))

            player.addMediaItem(MediaItem.Builder().setMediaId("10").setUri("redefinencm://playbackPlaceHolder?id=10").build())
            assertEquals(10, player.currentTimeline.getLastWindowIndex(true))
        } finally {
            player.release()
        }
    }
}
