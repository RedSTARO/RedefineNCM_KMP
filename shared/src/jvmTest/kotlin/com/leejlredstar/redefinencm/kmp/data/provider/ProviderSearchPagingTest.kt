package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** A later page of search results reaches every provider as the offset it asked for. */
class ProviderSearchPagingTest {
    private class RecordingProvider(override val id: MusicProviderId) : MusicProvider {
        val requests = mutableListOf<Pair<Int, Int>>()

        override suspend fun isAvailable(): Boolean = true
        override suspend fun search(keyword: String, limit: Int, offset: Int): List<ProviderTrack> {
            requests += limit to offset
            return List(limit) { i -> ProviderTrack(id = ProviderItemId.qq("$id-${offset + i}"), title = "t") }
        }
        override suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? = null
        override suspend fun lyric(id: ProviderItemId): ProviderLyric? = null
        override val capabilities: Set<ProviderCapability> = emptySet()
        override suspend fun resolveStream(
            id: ProviderItemId,
            quality: SoundQualityPreference,
        ): StreamResolution = StreamResolution.Failed(StreamFailureReason.NO_SOURCE)
    }

    @Test
    fun theOffsetReachesEveryProvider() = runTest {
        val netease = RecordingProvider(MusicProviderId.NETEASE)
        val qq = RecordingProvider(MusicProviderId.QQ)
        val registry = MusicProviderRegistry(listOf(netease, qq), PlatformSettings())

        registry.searchAll("x")
        registry.searchAll("x", limit = 30, offset = 30)

        assertEquals(listOf(30 to 0, 30 to 30), netease.requests)
        assertEquals(listOf(30 to 0, 30 to 30), qq.requests)
    }
}
