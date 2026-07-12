package com.leejlredstar.redefinencm.kmp.ui.screen

import com.leejlredstar.redefinencm.kmp.data.api.dto.SongAlbum
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongArtist
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.player.InMemoryPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyRecommendationPlaybackTest {
    @Test
    fun playAllReplacesExistingQueueInRecommendationOrder() {
        val player = InMemoryPlatformPlayer()
        try {
            player.setQueue(
                listOf(MediaInfo(id = "old", title = "Old", artist = "Artist")),
            )
            val recommendations = listOf(
                song(id = 101L, title = "First", artist = "Artist A"),
                song(id = 202L, title = "Second", artist = "Artist B"),
            )

            replaceQueueWithDailyRecommendations(player, recommendations)

            val snapshot = player.queueSnapshot.value
            assertEquals(listOf("101", "202"), snapshot.items.map { it.id })
            assertEquals(0, snapshot.currentIndex)
            assertEquals("101", snapshot.currentMedia?.id)
            assertEquals("Artist A", snapshot.currentMedia?.artist)
            assertEquals("Daily album", snapshot.currentMedia?.albumTitle)
            assertEquals("https://example.test/101.jpg", snapshot.currentMedia?.artworkUri)
            assertEquals("redefinencm://playbackPlaceHolder?id=101", snapshot.currentMedia?.placeholderUri)
            assertEquals(180_000L, snapshot.currentMedia?.duration)
        } finally {
            player.release()
        }
    }

    @Test
    fun emptyRecommendationsKeepExistingQueue() {
        val player = InMemoryPlatformPlayer()
        try {
            val existing = MediaInfo(id = "old", title = "Old", artist = "Artist")
            player.setQueue(listOf(existing))

            replaceQueueWithDailyRecommendations(player, emptyList())

            assertEquals(listOf(existing), player.queueSnapshot.value.items)
            assertEquals(existing, player.queueSnapshot.value.currentMedia)
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
            name = "Daily album",
            picUrl = "https://example.test/$id.jpg",
        ),
        dt = 180_000L,
    )
}
