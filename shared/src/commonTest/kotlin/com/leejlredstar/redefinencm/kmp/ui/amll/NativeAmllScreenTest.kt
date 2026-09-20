package com.leejlredstar.redefinencm.kmp.ui.amll

import com.leejlredstar.amll.compose.nextAmllArtworkUriAfterFailure
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The song's details moved to Now Playing, and with them the rule about when the full-screen
 * dynamic cover paused — it paused for the details video, which never opens over this page any
 * more. Those cases live in `SongWikiDetailsTest` or are gone.
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
