package com.leejlredstar.redefinencm.kmp.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression suite for the shuffle-ordering invariant (see PlayQueue / AGENTS.md).
 *
 * The central guarantee, asserted by [assertInvariant] after every mutation: the current-item
 * highlight is always consistent with the play order, i.e.
 * `currentItem == itemsInPlayOrder[positionInPlayOrder]`. This is what broke in the original
 * Android app when the shuffle permutation regenerated and a cached highlight went stale.
 */
class PlayQueueTest {

    private val tracks = listOf("a", "b", "c", "d", "e")

    private fun <T> PlayQueue<T>.assertInvariant() {
        if (isEmpty) {
            assertEquals(-1, currentIndex)
            assertTrue(playOrder.isEmpty())
            assertNull(currentItem)
            return
        }
        // playOrder is a true permutation of all item indices.
        assertEquals(items.indices.toList(), playOrder.sorted())
        // The highlight and the visible order derive from one source — they cannot disagree.
        assertEquals(currentItem, itemsInPlayOrder[positionInPlayOrder])
        assertEquals(currentIndex, playOrder[positionInPlayOrder])
    }

    @Test
    fun ofNoShuffleIsNaturalOrder() {
        val q = PlayQueue.of(tracks, startIndex = 2)
        assertEquals(tracks.indices.toList(), q.playOrder)
        assertEquals("c", q.currentItem)
        assertEquals(2, q.positionInPlayOrder)
        q.assertInvariant()
    }

    @Test
    fun shuffleKeepsCurrentFirstAndItemUnchanged() {
        val q = PlayQueue.of(tracks, startIndex = 3).setShuffle(true, Random(42))
        assertEquals("d", q.currentItem)        // current track does not change
        assertEquals(0, q.positionInPlayOrder)  // current item leads the shuffled order
        assertEquals(3, q.playOrder.first())
        q.assertInvariant()
    }

    @Test
    fun toggleShuffleOffRestoresNaturalOrderKeepingCurrent() {
        val q = PlayQueue.of(tracks, startIndex = 1)
            .setShuffle(true, Random(7))
            .setShuffle(false)
        assertEquals(tracks.indices.toList(), q.playOrder)
        assertEquals("b", q.currentItem)
        q.assertInvariant()
    }

    @Test
    fun nextAdvancesThroughNaturalOrder() {
        var q = PlayQueue.of(tracks, startIndex = 0)
        val seen = mutableListOf(q.currentItem)
        repeat(tracks.size - 1) {
            q = q.next(repeat = false)
            seen.add(q.currentItem)
        }
        assertEquals<List<String?>>(tracks, seen)
        q.assertInvariant()
    }

    @Test
    fun nextWrapsWhenRepeat() {
        val q = PlayQueue.of(tracks, startIndex = tracks.lastIndex).next(repeat = true)
        assertEquals("a", q.currentItem)
        q.assertInvariant()
    }

    @Test
    fun nextStaysAtEndWhenNoRepeat() {
        val last = PlayQueue.of(tracks, startIndex = tracks.lastIndex)
        assertEquals(last.currentItem, last.next(repeat = false).currentItem)
    }

    @Test
    fun previousWrapsWhenRepeat() {
        val q = PlayQueue.of(tracks, startIndex = 0).previous(repeat = true)
        assertEquals("e", q.currentItem)
        q.assertInvariant()
    }

    @Test
    fun skipToKeepsHighlightConsistentUnderShuffle() {
        val jumped = PlayQueue.of(tracks, startIndex = 0)
            .setShuffle(true, Random(1))
            .skipTo(4) // jump to "e" by item index, NOT play-order position
        assertEquals("e", jumped.currentItem)
        jumped.assertInvariant()
    }

    @Test
    fun reshuffleNeverDriftsHighlight() {
        // The original bug, made impossible: regenerate the permutation many times and the
        // current track + its highlight stay consistent every time.
        var q = PlayQueue.of(tracks, startIndex = 2).setShuffle(true, Random(5))
        repeat(10) { i ->
            q = q.reshuffle(Random(i.toLong()))
            assertEquals("c", q.currentItem)       // current track never changes on reshuffle
            assertEquals(0, q.positionInPlayOrder)  // current stays first
            q.assertInvariant()
        }
    }

    @Test
    fun addItemAppendsToOrder() {
        val q = PlayQueue.of(tracks, startIndex = 0).addItem("f")
        assertEquals(6, q.size)
        assertEquals(5, q.playOrder.last())
        q.assertInvariant()
    }

    @Test
    fun addItemToEmptyStartsAtFirst() {
        val q = PlayQueue.empty<String>().addItem("only")
        assertEquals("only", q.currentItem)
        assertEquals(0, q.currentIndex)
        q.assertInvariant()
    }

    @Test
    fun emptyQueueIsSafe() {
        val q = PlayQueue.empty<String>()
        assertTrue(q.isEmpty)
        assertNull(q.currentItem)
        assertNull(q.next().currentItem)      // no crash, no movement
        assertNull(q.previous().currentItem)
        q.assertInvariant()
    }

    @Test
    fun shuffleIsDeterministicForSeed() {
        val a = PlayQueue.of(tracks, startIndex = 0, shuffle = true, rng = Random(99))
        val b = PlayQueue.of(tracks, startIndex = 0, shuffle = true, rng = Random(99))
        assertEquals(a.playOrder, b.playOrder)
        a.assertInvariant()
    }
}
