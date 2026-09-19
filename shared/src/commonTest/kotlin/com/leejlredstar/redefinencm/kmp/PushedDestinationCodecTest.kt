package com.leejlredstar.redefinencm.kmp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pushed pages are saved as strings when the app's state is saved and read back when it is
 * restored; a destination that does not come back as itself loses the whole stack of pages.
 */
class PushedDestinationCodecTest {
    private val everyDestination = listOf(
        PushedDest.Login,
        PushedDest.NowPlaying,
        PushedDest.FullLyric,
        PushedDest.Downloads,
        PushedDest.SongRecognition,
        PushedDest.DailySongs,
        PushedDest.Settings,
        PushedDest.Playlist(123L),
        PushedDest.Artist(33927412L),
        PushedDest.Album(94214994L),
    )

    @Test
    fun everyDestinationComesBackAsItself() {
        everyDestination.forEach { destination ->
            assertEquals(destination, decodePushedDestination(encodePushedDestination(destination)))
        }
    }

    @Test
    fun destinationsEncodeToDistinctStrings() {
        val encoded = everyDestination.map(::encodePushedDestination)
        assertEquals(encoded.size, encoded.toSet().size)
    }

    @Test
    fun unknownOrMalformedStringsDecodeToNothing() {
        assertNull(decodePushedDestination("unknown"))
        assertNull(decodePushedDestination("playlist:"))
        assertNull(decodePushedDestination("artist:abc"))
        assertNull(decodePushedDestination("album:"))
    }
}
