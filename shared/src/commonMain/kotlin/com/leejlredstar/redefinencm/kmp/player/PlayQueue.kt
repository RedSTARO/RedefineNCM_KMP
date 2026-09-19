package com.leejlredstar.redefinencm.kmp.player

import kotlin.random.Random

/**
 * Pure, platform-independent model of the play queue and its shuffle ordering.
 *
 * This is the unit-testable heart of the **shuffle-ordering invariant** both repos treat as
 * regression-critical (see AGENTS.md): the visible queue, the play order, and the current-item
 * highlight must always be derived **together from one source of truth** — never from a
 * separately cached index that can drift when the shuffle permutation is regenerated.
 *
 * The original Android bug: under shuffle, Media3 could regenerate its internal permutation
 * without firing a timeline-change callback, leaving a cached highlight index pointing at the
 * wrong row. Here there is no cached highlight: [positionInPlayOrder] and [itemsInPlayOrder]
 * are computed from [items] + [currentIndex] + [playOrder] on every read, so
 * `currentItem == itemsInPlayOrder[positionInPlayOrder]` holds by construction.
 *
 * `PlatformPlayer` actuals should delegate ordering decisions to this model rather than
 * re-deriving them (which is how the bug was reintroduced).
 *
 * Immutable: every operation returns a new [PlayQueue].
 *
 * @param items queue contents in their original (unshuffled) order.
 * @param currentIndex index into [items] of the current track, or -1 when empty.
 * @param shuffleEnabled whether shuffle is on.
 * @param playOrder a permutation of `items.indices` giving the order tracks actually play in.
 */
class PlayQueue<T> private constructor(
    val items: List<T>,
    val currentIndex: Int,
    val shuffleEnabled: Boolean,
    val playOrder: List<Int>,
) {
    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    /** The current track, or null when the queue is empty. */
    val currentItem: T? get() = items.getOrNull(currentIndex)

    /** Where the current track sits within [playOrder]; -1 when empty. */
    val positionInPlayOrder: Int get() = playOrder.indexOf(currentIndex)

    /** The queue rendered in play order (what "up next" should show under shuffle). */
    val itemsInPlayOrder: List<T> get() = playOrder.map { items[it] }

    private fun copy(
        items: List<T> = this.items,
        currentIndex: Int = this.currentIndex,
        shuffleEnabled: Boolean = this.shuffleEnabled,
        playOrder: List<Int> = this.playOrder,
    ): PlayQueue<T> = PlayQueue(items, currentIndex, shuffleEnabled, playOrder)

    /** Enable/disable shuffle. Enabling keeps the current track first, then shuffles the rest. */
    fun setShuffle(enabled: Boolean, rng: Random = Random.Default): PlayQueue<T> {
        if (enabled == shuffleEnabled) return this
        if (isEmpty) return copy(shuffleEnabled = enabled)
        return copy(
            shuffleEnabled = enabled,
            playOrder = if (enabled) shuffledOrder(rng) else items.indices.toList(),
        )
    }

    /** Regenerate the shuffle permutation (mirrors Media3 regenerating its order). No-op if off. */
    fun reshuffle(rng: Random = Random.Default): PlayQueue<T> {
        if (!shuffleEnabled || isEmpty) return this
        return copy(playOrder = shuffledOrder(rng))
    }

    private fun shuffledOrder(rng: Random): List<Int> {
        val rest = items.indices.filterTo(mutableListOf()) { it != currentIndex }
        rest.shuffle(rng)
        return buildList {
            if (currentIndex in items.indices) add(currentIndex)
            addAll(rest)
        }
    }

    /** Advance to the next track in play order. Wraps to the start when [repeat]; else stays put. */
    fun next(repeat: Boolean = true): PlayQueue<T> {
        if (isEmpty) return this
        val pos = positionInPlayOrder
        if (pos < 0) return this
        val nextPos = when {
            pos + 1 < playOrder.size -> pos + 1
            repeat -> 0
            else -> return this
        }
        return copy(currentIndex = playOrder[nextPos])
    }

    /** Step to the previous track in play order. Wraps to the end when [repeat]; else stays put. */
    fun previous(repeat: Boolean = true): PlayQueue<T> {
        if (isEmpty) return this
        val pos = positionInPlayOrder
        if (pos < 0) return this
        val prevPos = when {
            pos - 1 >= 0 -> pos - 1
            repeat -> playOrder.lastIndex
            else -> return this
        }
        return copy(currentIndex = playOrder[prevPos])
    }

    /**
     * Jump to the item at [position] in play order.
     *
     * This is the index a queue list hands back, and under shuffle it is not an index into
     * [items] — translating between the two is exactly where a cached highlight used to drift.
     * Out of range leaves the queue alone.
     */
    fun skipToPlayOrderPosition(position: Int): PlayQueue<T> =
        playOrder.getOrNull(position)?.let(::skipTo) ?: this

    /** Jump to a specific item (index into [items]). Does NOT reshuffle the play order. */
    fun skipTo(index: Int): PlayQueue<T> {
        if (index !in items.indices) return this
        return copy(currentIndex = index)
    }

    /** Append an item to the end of both the queue and the play order. */
    fun addItem(item: T): PlayQueue<T> {
        val newItems = items + item
        return copy(
            items = newItems,
            currentIndex = if (currentIndex < 0) 0 else currentIndex,
            playOrder = playOrder + newItems.lastIndex,
        )
    }

    /**
     * Remove the track at [position] in play order — the row the queue sheet shows there, which
     * under shuffle is not [items]`[position]`. The rest keep their relative play order.
     *
     * Removing the current track makes the one after it in play order current, wrapping to the
     * first when it was last; removing the only track leaves an empty queue. Out of range
     * leaves the queue alone.
     */
    fun removeAtPlayOrderPosition(position: Int): PlayQueue<T> {
        val index = playOrder.getOrNull(position) ?: return this
        if (items.size == 1) {
            return copy(items = emptyList(), currentIndex = -1, playOrder = emptyList())
        }
        val remainingItems = items.filterIndexed { i, _ -> i != index }
        val remainingOrder = playOrder
            .filterIndexed { i, _ -> i != position }
            .map { if (it > index) it - 1 else it }
        val newCurrent = when {
            index < currentIndex -> currentIndex - 1
            index > currentIndex -> currentIndex
            else -> remainingOrder.getOrNull(position) ?: remainingOrder.first()
        }
        return copy(items = remainingItems, currentIndex = newCurrent, playOrder = remainingOrder)
    }

    /**
     * Move the track at play-order position [from] to [to], keeping the current track current.
     *
     * Only while shuffle is off, where the play order is the item order itself; under shuffle
     * this leaves the queue alone. Android's player cannot reorder a shuffled timeline, and a
     * rule that held on some platforms only would show the same drag doing different things.
     */
    fun movePlayOrderPosition(from: Int, to: Int): PlayQueue<T> {
        if (shuffleEnabled || from == to || from !in items.indices || to !in items.indices) return this
        val movedItems = items.toMutableList().apply { add(to, removeAt(from)) }
        val newCurrent = when {
            currentIndex == from -> to
            from < currentIndex && currentIndex <= to -> currentIndex - 1
            to <= currentIndex && currentIndex < from -> currentIndex + 1
            else -> currentIndex
        }
        return copy(items = movedItems, currentIndex = newCurrent)
    }

    fun clear(): PlayQueue<T> = empty()

    companion object {
        fun <T> empty(): PlayQueue<T> = PlayQueue(emptyList(), -1, false, emptyList())

        /** Build a queue from [items], starting at [startIndex], optionally shuffled. */
        fun <T> of(
            items: List<T>,
            startIndex: Int = 0,
            shuffle: Boolean = false,
            rng: Random = Random.Default,
        ): PlayQueue<T> {
            if (items.isEmpty()) return empty()
            val start = startIndex.coerceIn(0, items.lastIndex)
            val natural = PlayQueue(items, start, false, items.indices.toList())
            return if (shuffle) natural.setShuffle(true, rng) else natural
        }
    }
}
