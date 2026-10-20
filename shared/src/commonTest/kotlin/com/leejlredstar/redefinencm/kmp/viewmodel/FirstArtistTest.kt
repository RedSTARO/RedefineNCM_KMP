package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.provider.ProviderArtist
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A queue item's artist line is joined differently by each list; the first name is the same. */
class FirstArtistTest {
    @Test
    fun theFirstNameOfEveryJoiningIsFound() {
        assertEquals("周杰伦", firstArtistOf("周杰伦, 费玉清"))
        assertEquals("周杰伦", firstArtistOf("周杰伦 / 费玉清"))
        assertEquals("周杰伦", firstArtistOf("周杰伦、费玉清"))
        assertEquals("Beyond", firstArtistOf("Beyond"))
    }

    @Test
    fun aJoinerInsideOneArtistsNameKeepsEveryReading() {
        assertEquals(listOf("AC", "AC/DC"), artistLineHeads("AC/DC"))
        assertEquals(
            listOf("Earth", "Earth, Wind & Fire"),
            artistLineHeads("Earth, Wind & Fire"),
        )
        assertEquals(listOf("周杰伦", "周杰伦 / 费玉清"), artistLineHeads("周杰伦 / 费玉清"))
        assertEquals(listOf("Beyond"), artistLineHeads("Beyond"))
    }

    @Test
    fun switchingSourceFindsAnArtistWhoseNameHoldsAJoiner() {
        val qqCopy = ProviderTrack(
            id = ProviderItemId.qq("mid"),
            title = "Thunderstruck",
            artists = listOf(ProviderArtist(id = null, name = "AC/DC")),
            durationMillis = 292_000,
        )

        val found = sameSongIn(
            id = ProviderItemId.netease(1L),
            title = "Thunderstruck",
            artistLine = "AC/DC",
            durationMillis = 292_500,
            candidates = listOf(qqCopy),
        )

        assertEquals(qqCopy, found)
    }

    @Test
    fun aCutIsNeverAPrefixOfAWord() {
        val otherBand = ProviderTrack(
            id = ProviderItemId.qq("mid"),
            title = "海阔天空",
            artists = listOf(ProviderArtist(id = null, name = "Bey")),
            durationMillis = 300_000,
        )

        assertNull(
            sameSongIn(
                id = ProviderItemId.netease(1L),
                title = "海阔天空",
                artistLine = "Beyond",
                durationMillis = 300_000,
                candidates = listOf(otherBand),
            ),
        )
    }
}
