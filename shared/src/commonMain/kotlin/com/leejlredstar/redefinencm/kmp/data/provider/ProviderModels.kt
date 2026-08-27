package com.leejlredstar.redefinencm.kmp.data.provider

/**
 * Provider-neutral shapes for the capabilities more than one service can answer.
 *
 * These are deliberately narrower than the NetEase DTOs. Only search, playlist detail, lyrics and
 * stream URLs are cross-provider today; user profiles, liked songs, listening records, daily
 * recommendations and playback reporting remain NetEase-only and keep speaking their own DTOs.
 * Widening this model to cover them is a separate decision, not an oversight.
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

data class ProviderTrack(
    val id: ProviderItemId,
    val title: String,
    val artists: List<ProviderArtist> = emptyList(),
    val album: ProviderAlbum? = null,
    val durationMillis: Long = 0,
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
 * One music service, reduced to the four things every provider must be able to answer.
 *
 * Implementations return null (or an empty list) for "unavailable" rather than throwing, because
 * every caller is a UI path where one provider failing must not take the others down with it.
 */
interface MusicProvider {
    val id: MusicProviderId

    /**
     * Whether this provider has enough configuration to be worth calling. A provider that is not
     * configured is skipped by aggregating callers instead of contributing an error.
     */
    suspend fun isAvailable(): Boolean

    suspend fun search(keyword: String, limit: Int = DefaultSearchLimit): List<ProviderTrack>

    suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist?

    suspend fun lyric(id: ProviderItemId): ProviderLyric?

    /**
     * A playable URL, or null when this provider cannot serve the track at any reachable quality.
     *
     * [quality] is the user's preference, not a demand: providers whose tiers do not line up with
     * it are expected to degrade rather than fail.
     */
    suspend fun streamUrl(id: ProviderItemId, quality: SoundQualityPreference): String?

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
