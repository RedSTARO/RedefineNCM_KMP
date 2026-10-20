package com.leejlredstar.redefinencm.kmp.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * ExoPlayer's play order under shuffle, kept by the rules [PlayQueue] keeps on the other players:
 * the item playing when shuffle starts, or the one a new list starts at, plays first and the rest
 * are shuffled; items added later play last; removing or moving items keeps the order of the rest.
 *
 * Media3's DefaultShuffleOrder ignores the start index when a list is set and puts added items at
 * random places, so the song the user picked could land anywhere in the order, and a shuffled
 * queue ended after however many songs happened to follow it.
 */
@OptIn(UnstableApi::class)
internal class CurrentFirstShuffleOrder private constructor(
    private val order: IntArray,
    private val random: Random,
) : ShuffleOrder {

    /** Where each window index sits in [order]. */
    private val positions = IntArray(order.size).also { positions ->
        order.forEachIndexed { position, index -> positions[index] = position }
    }

    /** The window indexes in the order they play. */
    val playOrder: List<Int> get() = order.toList()

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int {
        val next = positions[index] + 1
        return if (next < order.size) order[next] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val previous = positions[index] - 1
        return if (previous >= 0) order[previous] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order.last()

    override fun getFirstIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order.first()

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val shifted = order.map { if (it >= insertionIndex) it + insertionCount else it }
        val added = insertionIndex until insertionIndex + insertionCount
        return CurrentFirstShuffleOrder((shifted + added).toIntArray(), random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        val kept = order
            .filter { it < indexFrom || it >= indexToExclusive }
            .map { if (it >= indexToExclusive) it - count else it }
        return CurrentFirstShuffleOrder(kept.toIntArray(), random)
    }

    /** Moving items changes their window indexes, not when they play. */
    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        val moved = order.map { index ->
            if (index in indexFrom until indexToExclusive) {
                newIndexFrom + (index - indexFrom)
            } else {
                val withoutMoved = if (index < indexFrom) index else index - count
                if (withoutMoved < newIndexFrom) withoutMoved else withoutMoved + count
            }
        }
        return CurrentFirstShuffleOrder(moved.toIntArray(), random)
    }

    override fun cloneAndClear(): ShuffleOrder = CurrentFirstShuffleOrder(IntArray(0), random)

    /** A new list: ExoPlayer passes the index it starts at, which plays first. */
    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder =
        startingWith(startIndex, insertionCount, random)

    companion object {
        /** [current] first, the rest shuffled; an index outside the list fixes nothing. */
        fun startingWith(current: Int, size: Int, random: Random = Random.Default): CurrentFirstShuffleOrder {
            val first = current.takeIf { it in 0 until size }
            val rest = (0 until size.coerceAtLeast(0)).filter { it != first }.shuffled(random)
            return CurrentFirstShuffleOrder((listOfNotNull(first) + rest).toIntArray(), random)
        }

        /**
         * An order kept from before, such as the one saved with the queue. One that is not an
         * order of every index starts again from its first item rather than failing the queue.
         */
        fun of(order: List<Int>, random: Random = Random.Default): CurrentFirstShuffleOrder =
            if (order.sorted() == order.indices.toList()) {
                CurrentFirstShuffleOrder(order.toIntArray(), random)
            } else {
                startingWith(order.firstOrNull() ?: C.INDEX_UNSET, order.size, random)
            }
    }
}
