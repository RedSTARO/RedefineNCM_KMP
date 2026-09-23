package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.LanguageSetting
import com.leejlredstar.redefinencm.kmp.player.resolveStreamUrl
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Screens ask the registry what a track's provider offers, and read why a track would not play
 * from the registry's failure channel; the player in between only ever sees a URL or null.
 */
class ProviderCapabilityAndFailureTest {
    /** The expected copy below is the Chinese, so the test pins it whatever the machine's language. */
    @BeforeTest
    fun showChinese() {
        I18n.apply(LanguageSetting.ZH)
    }

    @AfterTest
    fun followTheSystemAgain() {
        I18n.apply(LanguageSetting.SYSTEM)
    }

    private class FixedProvider(
        override val id: MusicProviderId,
        override val capabilities: Set<ProviderCapability>,
        var answer: StreamResolution = StreamResolution.Failed(StreamFailureReason.NO_SOURCE),
    ) : MusicProvider {
        override suspend fun isAvailable(): Boolean = true
        override suspend fun search(keyword: String, limit: Int, offset: Int) = emptyList<ProviderTrack>()
        override suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? = null
        override suspend fun lyric(id: ProviderItemId): ProviderLyric? = null
        override suspend fun resolveStream(id: ProviderItemId, quality: SoundQualityPreference) = answer
        override fun shareUrl(id: ProviderItemId): String = "https://example.test/${id.rawId}"
    }

    private val netease = FixedProvider(MusicProviderId.NETEASE, ProviderCapability.entries.toSet())
    private val qq = FixedProvider(MusicProviderId.QQ, setOf(ProviderCapability.SHARE_LINK))
    private val registry = MusicProviderRegistry(listOf(netease, qq), PlatformSettings())

    private suspend fun resolve(
        mediaId: String,
        local: String? = null,
        online: String? = null,
    ): String? = resolveStreamUrl(
        mediaId = mediaId,
        providers = registry,
        localAudioUri = { local },
        onlineUrl = { _, _ -> online },
        quality = { SoundQuality.EXHIGH },
    )

    @Test
    fun capabilitiesFollowTheProviderTheIdBelongsTo() {
        assertEquals(ProviderCapability.entries.toSet(), registry.capabilitiesOf("123"))
        assertEquals(setOf(ProviderCapability.SHARE_LINK), registry.capabilitiesOf("qq:0039MnYb0qxYhV"))
        // An id no provider claims offers nothing rather than being read as NetEase's.
        assertTrue(registry.capabilitiesOf("spotify:1").isEmpty())
        assertTrue(registry.capabilitiesOf(null).isEmpty())
        assertEquals("https://example.test/0039MnYb0qxYhV", registry.shareUrl("qq:0039MnYb0qxYhV"))
    }

    @Test
    fun aForeignProvidersFailureIsRecordedWithItsReasonAndClearedByAUrl() = runTest {
        qq.answer = StreamResolution.Failed(StreamFailureReason.PROVIDER_DISABLED)

        assertNull(resolve("qq:0039MnYb0qxYhV"))
        val failure = registry.streamFailures.value!!
        assertEquals("qq:0039MnYb0qxYhV", failure.mediaId)
        assertEquals(StreamFailureReason.PROVIDER_DISABLED, failure.reason)
        assertEquals("QQ音乐已关闭", failure.message)

        // The same track failing again is a new failure, so the screen says so again.
        assertNull(resolve("qq:0039MnYb0qxYhV"))
        assertTrue(registry.streamFailures.value!!.sequence > failure.sequence)

        qq.answer = StreamResolution.Playable("https://cdn.test/a.mp3")
        assertEquals("https://cdn.test/a.mp3", resolve("qq:0039MnYb0qxYhV"))
        assertNull(registry.streamFailures.value)
    }

    @Test
    fun aNeteaseTrackWithoutAnAddressIsRecordedAndALocalCopyClearsIt() = runTest {
        assertNull(resolve("123"))
        assertEquals(MusicProviderId.NETEASE, registry.streamFailures.value?.provider)
        assertEquals(StreamFailureReason.NO_SOURCE, registry.streamFailures.value?.reason)

        assertEquals("file:///music/123.mp3", resolve("123", local = "file:///music/123.mp3"))
        assertNull(registry.streamFailures.value)
    }

    @Test
    fun anotherTracksSuccessDoesNotClearAFailure() = runTest {
        assertNull(resolve("123"))
        assertEquals("https://cdn.test/456.mp3", resolve("456", online = "https://cdn.test/456.mp3"))
        assertEquals("123", registry.streamFailures.value?.mediaId)
    }
}
