package com.leejlredstar.redefinencm.kmp.ui.amll

import com.leejlredstar.amll.compose.nextAmllArtworkUriAfterFailure
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The song's details open from Now Playing, never over this page, so the full-screen dynamic
 * cover has no details video to pause for. The details cases live in `SongWikiDetailsTest`.
 */
class NativeAmllScreenTest {
    @Test
    fun localArtworkFailureFallsBackOnceToTheRemoteArtwork() {
        assertEquals(
            "https://music.example/cover.jpg",
            nextAmllArtworkUriAfterFailure(
                failedUri = "file:///downloads/42.cover.heic",
                primaryUri = "file:///downloads/42.cover.heic",
                fallbackUri = "https://music.example/cover.jpg",
            ),
        )
        assertEquals(
            null,
            nextAmllArtworkUriAfterFailure(
                failedUri = "https://music.example/cover.jpg",
                primaryUri = "file:///downloads/42.cover.heic",
                fallbackUri = "https://music.example/cover.jpg",
            ),
        )
    }
}
