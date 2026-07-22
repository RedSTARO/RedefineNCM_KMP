package com.leejlredstar.redefinencm.kmp.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DynamicCoverUiStateTest {
    @Test
    fun exposesUrlOnlyToItsOwningMedia() {
        val state = DynamicCoverUiState(
            mediaId = "song-a",
            url = "https://example.test/song-a.mp4",
        )

        assertEquals("https://example.test/song-a.mp4", state.urlFor("song-a"))
        assertNull(state.urlFor("song-b"))
        assertNull(state.urlFor(null))
    }
}
