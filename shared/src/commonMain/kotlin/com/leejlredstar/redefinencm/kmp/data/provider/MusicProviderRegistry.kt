package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** How a merged view interleaves results from more than one provider. */
enum class LibraryAggregationMode(val wireValue: String) {
    /** One library, providers merged together and labelled by origin. */
    MERGED("merged"),

    /** One tab per provider. */
    PER_PROVIDER("perProvider"),
    ;

    companion object {
        val Default: LibraryAggregationMode = MERGED

        fun fromWireValueOrDefault(value: String): LibraryAggregationMode =
            entries.firstOrNull { it.wireValue == value } ?: Default
    }
}

/**
 * The set of configured providers, and the dispatch point for anything addressed by a composite id.
 *
 * Callers hold this rather than a concrete provider so that adding a third service is a
 * registration, not a new branch at every call site.
 */
class MusicProviderRegistry(
    private val providers: List<MusicProvider>,
    private val settings: PlatformSettings,
) {
    operator fun get(id: MusicProviderId): MusicProvider? = providers.firstOrNull { it.id == id }

    /** Every registered provider, configured or not, in registration order. */
    val all: List<MusicProvider> get() = providers

    /**
     * The providers worth calling right now. A provider that is switched off or has no backend
     * configured is skipped rather than allowed to contribute an error to an aggregated result.
     */
    suspend fun available(): List<MusicProvider> = providers.filter {
        runCatching { it.isAvailable() }.getOrDefault(false)
    }

    suspend fun aggregationMode(): LibraryAggregationMode =
        LibraryAggregationMode.fromWireValueOrDefault(
            settings.getStringAsync(SettingKeys.LIBRARY_AGGREGATION_MODE, ""),
        )

    /** The user's configured playback quality, expressed in provider-neutral terms. */
    suspend fun playbackQuality(): SoundQualityPreference =
        SoundQualityPreference.fromSoundQualityName(
            settings.getStringAsync(SettingKeys.ONLINE_PLAY_QUALITY, SoundQuality.EXHIGH.name),
        )

    suspend fun streamUrl(id: ProviderItemId, quality: SoundQualityPreference): String? =
        this[id.provider]?.let { provider ->
            runCatching { provider.streamUrl(id, quality) }.getOrNull()
        }

    suspend fun lyric(id: ProviderItemId): ProviderLyric? =
        this[id.provider]?.let { provider ->
            runCatching { provider.lyric(id) }.getOrNull()
        }

    suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? =
        this[id.provider]?.let { provider ->
            runCatching { provider.playlistDetail(id) }.getOrNull()
        }

    /**
     * Searches every available provider at once and returns the results grouped by provider,
     * in registration order.
     *
     * Grouped rather than flattened because both library views have to ship: the merged view
     * interleaves these itself, and the per-provider view needs them kept apart. One provider
     * failing yields an empty list for it instead of failing the whole search.
     */
    suspend fun searchAll(
        keyword: String,
        limit: Int = MusicProvider.DefaultSearchLimit,
    ): List<ProviderSearchResults> = coroutineScope {
        if (keyword.isBlank()) return@coroutineScope emptyList()
        available()
            .map { provider ->
                provider to async {
                    runCatching { provider.search(keyword, limit) }
                }
            }
            .map { (provider, deferred) ->
                val outcome = deferred.await()
                ProviderSearchResults(
                    provider = provider.id,
                    tracks = outcome.getOrDefault(emptyList()),
                    failed = outcome.isFailure,
                )
            }
    }
}

data class ProviderSearchResults(
    val provider: MusicProviderId,
    val tracks: List<ProviderTrack>,
    /**
     * Whether this provider errored rather than simply matching nothing. Kept apart so a caller
     * can report "one of two providers is down" instead of "no results", which are different
     * things to a user with two accounts.
     */
    val failed: Boolean = false,
)

/**
 * Interleaves grouped results one-per-provider so a merged library does not open with a solid
 * block of whichever provider answered first.
 */
fun List<ProviderSearchResults>.interleaved(): List<ProviderTrack> {
    if (isEmpty()) return emptyList()
    val cursors = map { it.tracks }
    val longest = cursors.maxOf { it.size }
    val merged = ArrayList<ProviderTrack>(cursors.sumOf { it.size })
    for (index in 0 until longest) {
        for (tracks in cursors) {
            tracks.getOrNull(index)?.let(merged::add)
        }
    }
    return merged
}
