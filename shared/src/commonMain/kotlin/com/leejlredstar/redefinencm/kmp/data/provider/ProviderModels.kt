package com.leejlredstar.redefinencm.kmp.data.provider

/**
 * Provider-neutral shapes for the capabilities more than one service can answer.
 *
 * These are deliberately narrower than the NetEase DTOs. Only search, playlist detail, lyrics and
 * stream URLs are cross-provider today; user profiles, liked songs, listening records, daily
 * recommendations and playback reporting remain NetEase-only and keep speaking their own DTOs.
 * Which of those a provider's tracks support is its [ProviderCapability] set.
 */
data class ProviderArtist(
    val id: ProviderItemId?,
    val name: String,
)

data class ProviderAlbum(
    val id: ProviderItemId?,
    val name: String,
    val artworkUrl: String = "",
)

/**
 * What a service says about a track, for badges. These describe the track, not what the signed-in
 * account may do with it: a QQ "VIP" track has played at 128k without an account.
 */
enum class TrackTag(val label: String) {
    /** The service marks it as a members' track. */
    VIP("VIP"),

    /** Sold by album or by the song. */
    PAID("付费"),

    /** The service holds a lossless copy. */
    LOSSLESS("无损"),
}

data class ProviderTrack(
    val id: ProviderItemId,
    val title: String,
    val artists: List<ProviderArtist> = emptyList(),
    val album: ProviderAlbum? = null,
    val durationMillis: Long = 0,
    val tags: Set<TrackTag> = emptySet(),
) {
    val provider: MusicProviderId get() = id.provider

    val artistLine: String
        get() = artists.joinToString(" / ") { it.name }.ifEmpty { UnknownArtist }

    val artworkUrl: String get() = album?.artworkUrl.orEmpty()

    companion object {
        const val UnknownArtist = "未知歌手"
    }
}

data class ProviderPlaylist(
    val id: ProviderItemId,
    val name: String,
    val coverUrl: String = "",
    val description: String = "",
    val trackCount: Int = 0,
    val tracks: List<ProviderTrack> = emptyList(),
) {
    val provider: MusicProviderId get() = id.provider
}

/**
 * Lyrics in whichever forms the provider supplies.
 *
 * All four fields are raw timed-text payloads, not parsed lines: the existing lyric pipeline
 * already parses LRC and YRC, and re-parsing here would fork that logic. QQ Music supplies only
 * [plain] and [translation]; [wordByWord] and [romanization] stay null for it.
 */
data class ProviderLyric(
    val plain: String? = null,
    val translation: String? = null,
    val wordByWord: String? = null,
    val romanization: String? = null,
) {
    val isEmpty: Boolean
        get() = plain.isNullOrBlank() &&
            translation.isNullOrBlank() &&
            wordByWord.isNullOrBlank() &&
            romanization.isNullOrBlank()
}

/**
 * A provider could not answer: its backend is unreachable, erroring, or rejected the request.
 *
 * This exists because "matched nothing" and "is down" are different answers, and collapsing them
 * into an empty list makes a dead backend read as "没有找到结果". [MusicProvider.search] throws it
 * so an aggregating caller can report which provider failed while still showing the others' hits.
 */
class ProviderUnavailableException(
    val provider: MusicProviderId,
    message: String,
) : Exception(message)

/**
 * The features built for NetEase that a provider's tracks may or may not have.
 *
 * Screens and view models ask [MusicProviderRegistry.capabilitiesOf] for the current track instead
 * of parsing its id: a missing capability is shown as "not offered by this provider", never as an
 * error. It says what the provider can do, not what the signed-in account may do — a feature the
 * account is not signed in for is a separate state its screen shows on its own.
 */
enum class ProviderCapability {
    /** Lyrics through the app's lyric pipeline. */
    LYRIC,

    /** The account's own "liked" list. */
    LIKE,
    COMMENTS,

    /** The song-details sheet. */
    SONG_WIKI,

    /** The animated cover some tracks carry. */
    DYNAMIC_COVER,

    /** Artist and album pages reached from the song. */
    CREDITS,

    /** Downloads, and the local copies they leave. */
    DOWNLOAD,

    /** A web link to the song, see [MusicProvider.shareUrl]. */
    SHARE_LINK,

    /** Listening records reported to the account. */
    PLAYBACK_REPORTING,
}

/** Why a provider produced no stream for a track. */
enum class StreamFailureReason {
    /** The provider is switched off in settings. */
    PROVIDER_DISABLED,

    /** Its backend could not be reached or errored. */
    UNREACHABLE,

    /**
     * Its backend answered without a playable copy: a VIP-only or unlicensed track, or one the
     * service has no file for. The gateways do not say which, so neither does the app.
     */
    NO_SOURCE,
}

/** One provider's answer to "what do I play for this track". */
sealed interface StreamResolution {
    data class Playable(val url: String) : StreamResolution
    data class Failed(val reason: StreamFailureReason) : StreamResolution
}

/**
 * One music service, reduced to what every provider must be able to answer.
 *
 * [search] throws [ProviderUnavailableException] on transport failure, because its caller
 * aggregates across providers and has to tell an empty result from a broken one. The rest address
 * a single item on a single provider, where a null — or, for streams, a [StreamResolution.Failed]
 * with its reason — is the whole answer.
 */
interface MusicProvider {
    val id: MusicProviderId

    /** The NetEase-era features this provider's tracks support. */
    val capabilities: Set<ProviderCapability>

    /**
     * Whether this provider has enough configuration to be worth calling. A provider that is not
     * configured is skipped by aggregating callers instead of contributing an error.
     */
    suspend fun isAvailable(): Boolean

    /**
     * One page of results: [limit] tracks after the first [offset] of this keyword's.
     *
     * @throws ProviderUnavailableException when the backend could not be reached or errored.
     */
    suspend fun search(
        keyword: String,
        limit: Int = DefaultSearchLimit,
        offset: Int = 0,
    ): List<ProviderTrack>

    suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist?

    suspend fun lyric(id: ProviderItemId): ProviderLyric?

    /**
     * A playable URL, or why there is none.
     *
     * [quality] is the user's preference, not a demand: providers whose tiers do not line up with
     * it are expected to degrade rather than fail.
     */
    suspend fun resolveStream(id: ProviderItemId, quality: SoundQualityPreference): StreamResolution

    /** A web page for the track, for "copy link"; null when the provider has none. */
    fun shareUrl(id: ProviderItemId): String? = null

    companion object {
        const val DefaultSearchLimit = 30
    }
}

/**
 * The user's configured quality, restated so provider implementations do not have to import the
 * settings layer's NetEase-shaped [com.leejlredstar.redefinencm.kmp.util.SoundQuality] ladder.
 * Ordinal order is ascending, so a provider can walk down from the requested rung.
 */
enum class SoundQualityPreference {
    STANDARD,
    HIGH,
    LOSSLESS,
    HIRES,
    ;

    companion object {
        fun fromSoundQualityName(name: String): SoundQualityPreference = when (name.uppercase()) {
            "STANDARD" -> STANDARD
            "HIGHER", "EXHIGH" -> HIGH
            "LOSSLESS" -> LOSSLESS
            // Spatial and mastered tiers are NetEase-only formats. They are all lossless-or-better
            // sources, so a provider without them should reach for its best rung, not its lowest.
            "HIRES", "JYEFFECT", "SKY", "DOLBY", "JYMASTER" -> HIRES
            else -> HIGH
        }
    }
}
