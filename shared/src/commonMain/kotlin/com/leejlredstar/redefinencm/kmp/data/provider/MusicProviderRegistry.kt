package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

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
        providerCall { it.isAvailable() }.getOrDefault(false)
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

    /** The capabilities of the provider [mediaId] belongs to; none for an id no provider claims. */
    fun capabilitiesOf(mediaId: String?): Set<ProviderCapability> {
        val itemId = mediaId?.toProviderItemIdOrNull() ?: return emptySet()
        return this[itemId.provider]?.capabilities.orEmpty()
    }

    /** A web page for the track behind [mediaId], when its provider has one. */
    fun shareUrl(mediaId: String): String? {
        val itemId = mediaId.toProviderItemIdOrNull() ?: return null
        return this[itemId.provider]?.shareUrl(itemId)
    }

    private val _streamFailures = MutableStateFlow<StreamFailure?>(null)
    private val failureSequence = MutableStateFlow(0L)

    /**
     * Why the most recent stream resolution produced nothing, until that track resolves.
     *
     * A side channel rather than a player API: the player keeps seeing a URL or null and never
     * learns what a provider is, while the now-playing screen reads the reason from here.
     */
    val streamFailures: StateFlow<StreamFailure?> = _streamFailures.asStateFlow()

    fun recordStreamFailure(mediaId: String, provider: MusicProviderId, reason: StreamFailureReason) {
        val sequence = failureSequence.updateAndGet { it + 1 }
        _streamFailures.value = StreamFailure(mediaId, provider, reason, sequence)
    }

    fun clearStreamFailure(mediaId: String) {
        _streamFailures.update { current -> if (current?.mediaId == mediaId) null else current }
    }

    /**
     * The URL to play [id], or null — with the reason recorded in [streamFailures] under
     * [mediaId], the id the queue carries.
     */
    suspend fun streamUrl(
        id: ProviderItemId,
        quality: SoundQualityPreference,
        mediaId: String = id.mediaId,
    ): String? {
        val provider = this[id.provider]
        val resolution = if (provider == null) {
            StreamResolution.Failed(StreamFailureReason.NO_SOURCE)
        } else {
            providerCall { provider.resolveStream(id, quality) }
                .getOrElse { StreamResolution.Failed(StreamFailureReason.UNREACHABLE) }
        }
        return when (resolution) {
            is StreamResolution.Playable -> {
                clearStreamFailure(mediaId)
                resolution.url
            }
            is StreamResolution.Failed -> {
                recordStreamFailure(mediaId, id.provider, resolution.reason)
                null
            }
        }
    }

    suspend fun lyric(id: ProviderItemId): ProviderLyric? =
        this[id.provider]?.let { provider ->
            providerCall { provider.lyric(id) }.getOrNull()
        }

    suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? =
        this[id.provider]?.let { provider ->
            providerCall { provider.playlistDetail(id) }.getOrNull()
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
        offset: Int = 0,
    ): List<ProviderSearchResults> = coroutineScope {
        if (keyword.isBlank()) return@coroutineScope emptyList()
        available()
            .map { provider ->
                provider to async {
                    providerCall { provider.search(keyword, limit, offset) }
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

/**
 * [runCatching] that lets cancellation through. A caller that was cancelled — a new search typed,
 * a track skipped — is not a provider that failed, and recording it as one would report a healthy
 * backend as down.
 */
internal inline fun <T> providerCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Result.failure(failure)
}

/** Why [mediaId] could not be played; [sequence] tells two failures of the same track apart. */
data class StreamFailure(
    val mediaId: String,
    val provider: MusicProviderId,
    val reason: StreamFailureReason,
    val sequence: Long,
) {
    /** A clause fit for "无法播放「…」：" on screen. */
    val message: String
        get() = when (reason) {
            StreamFailureReason.PROVIDER_DISABLED -> "${provider.displayName}已关闭"
            StreamFailureReason.UNREACHABLE -> "${provider.displayName}后端无响应"
            StreamFailureReason.NO_SOURCE -> "${provider.displayName}没有提供这首歌的播放地址"
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
