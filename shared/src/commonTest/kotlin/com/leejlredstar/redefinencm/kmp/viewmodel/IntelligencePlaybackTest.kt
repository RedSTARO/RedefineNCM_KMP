package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.api.dto.SongAlbum
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongArtist
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.player.InMemoryPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntelligencePlaybackTest {
    @Test
    fun seedSelectionFiltersInvalidIdsAndDuplicatesBeforeSelecting() {
        var candidateCount = -1

        val selected = selectIntelligenceSeed(
            ids = listOf(0L, -1L, 101L, 101L, 202L, 303L, 202L),
            indexSelector = { count ->
                candidateCount = count
                1
            },
        )

        assertEquals(3, candidateCount)
        assertEquals(202L, selected)
    }

    @Test
    fun seedSelectionDoesNotInvokeSelectorWithoutCandidates() {
        val selected = selectIntelligenceSeed(listOf(0L, -1L, 0L)) {
            error("Selector must not run for an empty candidate list")
        }

        assertNull(selected)
    }

    @Test
    fun seedSelectionRejectsAnOutOfRangeInjectedIndex() {
        assertFailsWith<IllegalArgumentException> {
            selectIntelligenceSeed(listOf(101L)) { 1 }
        }
    }

    @Test
    fun queueMappingKeepsFirstOccurrenceOrderAndPlaylistSource() {
        val queue = buildIntelligenceQueue(
            songInfos = listOf(
                song(202L, "Second", "Artist B"),
                null,
                song(0L, "Invalid", "Nobody"),
                song(101L, "First", "Artist A"),
                song(202L, "Duplicate", "Other artist"),
                song(303L, "Third", "Artist C"),
            ),
            playlistId = 55L,
        )

        assertEquals(listOf("202", "101", "303"), queue.map { it.id })
        assertEquals(listOf("Second", "First", "Third"), queue.map { it.title })
        assertTrue(queue.all { it.sourceId == "55" })
        assertEquals("Artist B", queue.first().artist)
        assertEquals("Intelligence album", queue.first().albumTitle)
        assertEquals("https://example.test/202.jpg", queue.first().artworkUri)
        assertEquals("redefinencm://playbackPlaceHolder?id=202", queue.first().placeholderUri)
        assertEquals(180_000L, queue.first().duration)
    }

    @Test
    fun replacementDisablesShuffleAndStartsFirstSmartSong() {
        val player = InMemoryPlatformPlayer()
        try {
            player.setQueue(listOf(MediaInfo(id = "old", title = "Old", artist = "Artist")))
            player.setShuffleEnabled(true)
            val queue = listOf(
                MediaInfo(id = "101", title = "First", artist = "Artist A"),
                MediaInfo(id = "202", title = "Second", artist = "Artist B"),
            )

            replaceQueueWithIntelligenceList(player, queue)

            val snapshot = player.queueSnapshot.value
            assertFalse(snapshot.shuffleEnabled)
            assertEquals(listOf("101", "202"), snapshot.items.map { it.id })
            assertEquals(0, snapshot.currentIndex)
            assertEquals("101", snapshot.currentMedia?.id)
            assertTrue(player.isPlaying.value)
            assertEquals(PlayerState.PLAYING, player.state.value)
        } finally {
            player.release()
        }
    }

    @Test
    fun replacementRejectsAnEmptyQueueWithoutChangingPlayback() {
        val player = InMemoryPlatformPlayer()
        try {
            val existing = MediaInfo(id = "old", title = "Old", artist = "Artist")
            player.setQueue(listOf(existing))
            player.setShuffleEnabled(true)

            assertFailsWith<IllegalArgumentException> {
                replaceQueueWithIntelligenceList(player, emptyList())
            }

            assertEquals(listOf(existing), player.queueSnapshot.value.items)
            assertTrue(player.queueSnapshot.value.shuffleEnabled)
            assertFalse(player.isPlaying.value)
        } finally {
            player.release()
        }
    }

    private fun song(
        id: Long,
        title: String,
        artist: String,
    ) = SongDetailSongs(
        id = id,
        name = title,
        ar = listOf(SongArtist(id = id, name = artist)),
        al = SongAlbum(
            id = id,
            name = "Intelligence album",
            picUrl = "https://example.test/$id.jpg",
        ),
        dt = 180_000L,
    )
}
