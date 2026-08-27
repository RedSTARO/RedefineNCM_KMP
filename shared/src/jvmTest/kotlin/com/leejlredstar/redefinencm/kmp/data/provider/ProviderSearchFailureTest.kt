package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Matched nothing" and "is down" must stay distinguishable.
 *
 * Flattening a failed backend into an empty list makes the UI say 没有找到结果 for a provider that
 * never answered, which is the behaviour the pre-provider search deliberately avoided.
 */
class ProviderSearchFailureTest {
    private class FakeProvider(
        override val id: MusicProviderId,
        private val available: Boolean = true,
        private val result: () -> List<ProviderTrack>,
    ) : MusicProvider {
        override suspend fun isAvailable(): Boolean = available
        override suspend fun search(keyword: String, limit: Int): List<ProviderTrack> = result()
        override suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? = null
        override suspend fun lyric(id: ProviderItemId): ProviderLyric? = null
        override suspend fun streamUrl(
            id: ProviderItemId,
            quality: SoundQualityPreference,
        ): String? = null
    }

    private fun track(title: String) =
        ProviderTrack(id = ProviderItemId.qq(title), title = title)

    // searchAll reaches settings only through each provider's isAvailable(), which the fakes
    // answer themselves, so constructing this never reads or writes real preferences.
    private fun registryOf(vararg providers: MusicProvider) =
        MusicProviderRegistry(providers.toList(), PlatformSettings())

    @Test
    fun aFailingProviderIsMarkedFailedRatherThanEmpty() = runTest {
        val registry = registryOf(
            FakeProvider(MusicProviderId.NETEASE) {
                throw ProviderUnavailableException(MusicProviderId.NETEASE, "down")
            },
        )

        val groups = registry.searchAll("x")
        assertEquals(1, groups.size)
        assertTrue(groups.single().failed)
        assertTrue(groups.single().tracks.isEmpty())
    }

    @Test
    fun aProviderThatMatchedNothingIsNotMarkedFailed() = runTest {
        val registry = registryOf(FakeProvider(MusicProviderId.QQ) { emptyList() })

        val group = registry.searchAll("x").single()
        assertFalse(group.failed)
        assertTrue(group.tracks.isEmpty())
    }

    @Test
    fun oneProviderFailingStillReturnsTheOthersHits() = runTest {
        val registry = registryOf(
            FakeProvider(MusicProviderId.NETEASE) {
                throw ProviderUnavailableException(MusicProviderId.NETEASE, "down")
            },
            FakeProvider(MusicProviderId.QQ) { listOf(track("a"), track("b")) },
        )

        val groups = registry.searchAll("x")
        assertEquals(2, groups.size)
        assertTrue(groups.first { it.provider == MusicProviderId.NETEASE }.failed)

        val qq = groups.first { it.provider == MusicProviderId.QQ }
        assertFalse(qq.failed)
        assertEquals(listOf("a", "b"), qq.tracks.map { it.title })
        // The surviving provider's hits must still reach the merged list.
        assertEquals(listOf("a", "b"), groups.interleaved().map { it.title })
    }

    @Test
    fun unavailableProvidersAreSkippedWithoutBeingReportedAsFailures() = runTest {
        val registry = registryOf(
            FakeProvider(MusicProviderId.QQ, available = false) { error("must not be called") },
            FakeProvider(MusicProviderId.NETEASE) { listOf(track("a")) },
        )

        val groups = registry.searchAll("x")
        assertEquals(listOf(MusicProviderId.NETEASE), groups.map { it.provider })
        assertFalse(groups.single().failed)
    }

    @Test
    fun blankKeywordsDoNotReachAnyProvider() = runTest {
        val registry = registryOf(
            FakeProvider(MusicProviderId.NETEASE) { error("must not be called") },
        )
        assertTrue(registry.searchAll("   ").isEmpty())
    }
}
