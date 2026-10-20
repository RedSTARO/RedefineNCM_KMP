package com.leejlredstar.redefinencm.kmp.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/**
 * Media3 opens one period's source again for every retry and seek, mid-song: each open must reach
 * the same file, and a failure must reach the screen once.
 */
class PeriodStreamResolverTest {
    private object NoSettings : DataStore<Preferences> {
        override val data: Flow<Preferences> = flowOf(emptyPreferences())
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(emptyPreferences())
    }

    private val registry = MusicProviderRegistry(emptyList(), PlatformSettings(NoSettings))

    private fun period(
        quality: () -> SoundQuality,
        local: () -> String?,
        online: (SoundQuality) -> String?,
        active: Boolean = true,
    ) = PeriodStreamResolver(
        providers = registry,
        currentQuality = quality,
        localAudioUri = { _, _ -> local() },
        onlineUrl = { _, chosen -> online(chosen) },
        isActive = { active },
    )

    @Test
    fun onePeriodKeepsTheQualityAndTheSourceItStartedWith() = runTest {
        var quality = SoundQuality.EXHIGH
        var local: String? = null
        val asked = mutableListOf<SoundQuality>()
        val resolver = period({ quality }, { local }, { asked += it; "https://cdn/${it.name}" })

        assertEquals("https://cdn/EXHIGH", resolver.resolve("1"))
        // The network changed and a download of this song finished while it played.
        quality = SoundQuality.STANDARD
        local = "content://downloads/1"
        assertEquals("https://cdn/EXHIGH", resolver.resolve("1"))
        assertEquals(listOf(SoundQuality.EXHIGH, SoundQuality.EXHIGH), asked)

        // The next period (another track, or this one played again) follows the new state.
        assertEquals("content://downloads/1", period({ quality }, { local }, { "https://cdn/${it.name}" }).resolve("1"))
    }

    @Test
    fun aPeriodThatStartedFromADownloadStreamsAtItsQualityOnceTheFileIsGone() = runTest {
        var local: String? = "content://downloads/1"
        val resolver = period({ SoundQuality.LOSSLESS }, { local }, { "https://cdn/${it.name}" })

        assertEquals("content://downloads/1", resolver.resolve("1"))
        local = null
        assertEquals("https://cdn/LOSSLESS", resolver.resolve("1"))
    }

    @Test
    fun aFailureIsReportedOncePerPeriodAndWithdrawnByALaterSuccess() = runTest {
        var online: String? = null
        val resolver = period({ SoundQuality.EXHIGH }, { null }, { online })

        assertNull(resolver.resolve("1"))
        val first = registry.streamFailures.value!!.sequence
        assertNull(resolver.resolve("1"))
        assertEquals(first, registry.streamFailures.value!!.sequence, "a retry is not a new failure")

        online = "https://cdn/1"
        assertEquals("https://cdn/1", resolver.resolve("1"))
        assertNull(registry.streamFailures.value)

        online = null
        assertNull(period({ SoundQuality.EXHIGH }, { null }, { online }).resolve("1"))
        assertTrue(registry.streamFailures.value!!.sequence > first, "a new period reports again")
    }

    @Test
    fun theOtherPlayersFailuresAreNotReported() = runTest {
        assertNull(period({ SoundQuality.EXHIGH }, { null }, { null }, active = false).resolve("1"))
        assertNull(registry.streamFailures.value)
    }
}
