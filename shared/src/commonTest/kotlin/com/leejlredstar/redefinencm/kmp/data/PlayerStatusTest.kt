package com.leejlredstar.redefinencm.kmp.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerStatusTest {
    @Test
    fun oldPersistedMediaDefaultsToNoSourceId() {
        val oldJson =
            """{"id":"42","title":"Song","artist":"Artist","duration":180000}"""

        val restored = Json.decodeFromString<PersistedMediaItem>(oldJson)

        assertEquals("", restored.sourceId)
    }

    @Test
    fun sourceIdSurvivesPlayerStatusJsonRoundTrip() {
        val original = PlayerStatus(
            playlist = listOf(
                PersistedMediaItem(
                    id = "42",
                    title = "Song",
                    artist = "Artist",
                    sourceId = "36780169",
                ),
            ),
            index = 0,
            position = 12_000L,
            isPlaying = false,
            isShuffling = false,
        )

        val restored = Json.decodeFromString<PlayerStatus>(Json.encodeToString(original))

        assertEquals(original, restored)
        assertEquals("36780169", restored.playlist.single().sourceId)
    }
}
