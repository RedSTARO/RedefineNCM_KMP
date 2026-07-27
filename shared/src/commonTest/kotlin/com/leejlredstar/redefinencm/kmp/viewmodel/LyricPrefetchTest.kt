package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlayerQueueSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricPrefetchTest {
    private val first = MediaInfo(id = "1", title = "first", artist = "artist")
    private val second = MediaInfo(id = "2", title = "second", artist = "artist")

    @Test
    fun prefetchUsesTheNextItemInActualPlaybackOrder() {
        val snapshot = PlayerQueueSnapshot(
            items = listOf(second, first),
            currentIndex = 0,
            currentMedia = second,
            shuffleEnabled = true,
        )

        assertEquals(first, nextLyricPrefetchCandidate(snapshot))
    }

    @Test
    fun finalAndDuplicateItemsAreNotPrefetched() {
        assertNull(
            nextLyricPrefetchCandidate(
                PlayerQueueSnapshot(
                    items = listOf(first),
                    currentIndex = 0,
                    currentMedia = first,
                ),
            ),
        )
        assertNull(
            nextLyricPrefetchCandidate(
                PlayerQueueSnapshot(
                    items = listOf(first, first.copy(title = "duplicate")),
                    currentIndex = 0,
                    currentMedia = first,
                ),
            ),
        )
    }
}
