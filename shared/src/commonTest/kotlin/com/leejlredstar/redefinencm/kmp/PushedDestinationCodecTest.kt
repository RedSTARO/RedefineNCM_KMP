package com.leejlredstar.redefinencm.kmp

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The pushed pages are saved as strings when the app's state is saved and read back when it is
 * restored; a destination that does not come back as itself loses the whole stack of pages.
 */
class PushedDestinationCodecTest {
    private val everyDestination = listOf(
        PushedDest.Login(),
        PushedDest.Login(MusicProviderId.QQ),
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
        assertNull(decodePushedDestination("login:spotify"))
    }

    @Test
    fun neteaseLoginKeepsTheFormSavedBeforeProvidersExisted() {
        // State saved by an older build holds the bare word; it must still open NetEase's login.
        assertEquals("login", encodePushedDestination(PushedDest.Login()))
        assertEquals(PushedDest.Login(MusicProviderId.NETEASE), decodePushedDestination("login"))
        assertEquals("login:qq", encodePushedDestination(PushedDest.Login(MusicProviderId.QQ)))
    }
}
