package com.leejlredstar.redefinencm.kmp.data.provider

import kotlin.test.Test
import kotlin.test.assertEquals

/** Only the same song from different providers folds into one row, and never on a guess. */
class SearchMergingTest {
    private fun ncm(id: Long, title: String, artist: String, duration: Long) = ProviderTrack(
        id = ProviderItemId.netease(id),
        title = title,
        artists = listOf(ProviderArtist(null, artist)),
        durationMillis = duration,
    )

    private fun qq(mid: String, title: String, artist: String, duration: Long) = ProviderTrack(
        id = ProviderItemId.qq(mid),
        title = title,
        artists = listOf(ProviderArtist(null, artist)),
        durationMillis = duration,
    )

    @Test
    fun theSameSongFromTwoProvidersBecomesOneRowInFirstAppearanceOrder() {
        val entries = listOf(
            ncm(1, "晴天", "周杰伦", 269_000),
            qq("a", "晴天", "周杰伦", 269_700),
            ncm(2, "稻香", "周杰伦", 223_000),
            qq("b", "稻 香", "周杰伦", 224_500),
        ).mergeSameSongs()

        assertEquals(2, entries.size)
        assertEquals(ProviderItemId.netease(1), entries[0].track.id)
        assertEquals(listOf(ProviderItemId.qq("a")), entries[0].alternates.map { it.id })
        assertEquals(listOf(MusicProviderId.NETEASE, MusicProviderId.QQ), entries[1].providers)
    }

    @Test
    fun anotherVersionAnUnknownLengthOrAFarLengthStaysApart() {
        val entries = listOf(
            ncm(1, "晴天", "周杰伦", 269_000),
            qq("live", "晴天 (Live)", "周杰伦", 269_000),
            qq("far", "晴天", "周杰伦", 290_000),
            qq("unknown", "晴天", "周杰伦", 0),
        ).mergeSameSongs()

        assertEquals(4, entries.size)
    }

    @Test
    fun twoTracksOfOneProviderAreNeverMerged() {
        val entries = listOf(
            ncm(1, "晴天", "周杰伦", 269_000),
            ncm(2, "晴天", "周杰伦", 269_000),
            qq("a", "晴天", "周杰伦", 269_000),
        ).mergeSameSongs()

        // The QQ track joins the first NetEase row; the second NetEase row stays its own.
        assertEquals(2, entries.size)
        assertEquals(listOf(ProviderItemId.qq("a")), entries[0].alternates.map { it.id })
        assertEquals(emptyList(), entries[1].alternates)
    }

    @Test
    fun aDifferentArtistStaysApart() {
        val entries = listOf(
            ncm(1, "晴天", "周杰伦", 269_000),
            qq("a", "晴天", "翻唱者", 269_000),
        ).mergeSameSongs()
        assertEquals(2, entries.size)
    }
}
