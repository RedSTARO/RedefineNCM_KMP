package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmllSongDetailsTest {
    @Test
    fun mapsOnlyCurrentDisplayMetadataIntoBridgePayload() {
        val payload = MediaInfo(
            id = "1958384591",
            title = "Song 'title'",
            artist = "Artist",
            albumTitle = "Album",
            artworkUri = "https://example.test/cover.jpg",
            placeholderUri = "redefinencm://playbackPlaceHolder?id=1958384591",
            duration = 123_000,
            sourceId = "42",
        ).toAmllSongDetails()

        assertEquals("1958384591", payload.mediaId)
        assertEquals("Song 'title'", payload.title)
        assertEquals("Artist", payload.artist)
        assertEquals("Album", payload.albumTitle)
        assertEquals("https://example.test/cover.jpg", payload.artworkUri)

        val encoded = Json.encodeToString(payload)
        assertTrue(encoded.contains("Song 'title'"))
        assertTrue(!encoded.contains("placeholderUri"))
        assertTrue(!encoded.contains("sourceId"))
    }

    @Test
    fun nullMediaClearsEveryPreviouslyRenderedField() {
        assertEquals(AmllSongDetails(), null.toAmllSongDetails())
    }
}
