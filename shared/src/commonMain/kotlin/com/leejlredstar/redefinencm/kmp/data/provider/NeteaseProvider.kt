package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongAlbum
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongArtist
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.flow.firstOrNull

/**
 * The existing NetEase stack, presented as a [MusicProvider].
 *
 * This is an adapter, not a rewrite: [Repository] keeps its NetEase-shaped API and every existing
 * caller keeps using it directly. Only the four cross-provider capabilities are routed through
 * here, so nothing NetEase-only — playback reporting, liked songs, daily recommendations — is
 * pulled into the provider abstraction where it would have to be made conditional.
 */
class NeteaseProvider(
    private val repository: Repository,
    private val settings: PlatformSettings,
) : MusicProvider {
    override val id: MusicProviderId = MusicProviderId.NETEASE

    /** Every feature was built for NetEase, so it has them all. */
    override val capabilities: Set<ProviderCapability> = ProviderCapability.entries.toSet()

    /**
     * Always available.
     *
     * Gating this on a configured server address would be a new behaviour: NetEase search was
     * never conditional before, and excluding the provider would turn an unconfigured backend into
     * "没有找到结果" instead of the search failure it actually is. Letting the call run and fail
     * keeps the honest message.
     */
    override suspend fun isAvailable(): Boolean = true

    override suspend fun search(keyword: String, limit: Int, offset: Int): List<ProviderTrack> {
        if (keyword.isBlank()) return emptyList()
        // A search that matched nothing answers with code 200 and an empty song list; null means
        // the call itself failed. Flattening that to an empty list would report a dead backend as
        // "no results".
        val response = repository.search(keyword, limit = limit, offset = offset)
            ?: throw ProviderUnavailableException(id, "网易云音乐搜索请求失败")
        return response.result
            ?.songs
            .orEmpty()
            .take(limit)
            .map { it.toProviderTrack() }
    }

    override suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? {
        val numericId = id.neteaseIdOrNull ?: return null
        val detail = repository.getPlaylistDetail(numericId).firstOrNull()?.value?.playlist
        val tracks = repository.getPlaylistTrackAllOnce(numericId)
            ?.songs
            .orEmpty()
            .map { it.toProviderTrack() }

        if (detail == null && tracks.isEmpty()) return null
        return ProviderPlaylist(
            id = id,
            name = detail?.name.orEmpty(),
            coverUrl = detail?.coverImgUrl.orEmpty(),
            description = detail?.description.orEmpty(),
            trackCount = detail?.trackCount?.toInt() ?: tracks.size,
            tracks = tracks,
        )
    }

    override suspend fun lyric(id: ProviderItemId): ProviderLyric? {
        val numericId = id.neteaseIdOrNull ?: return null
        val lyric = repository.getLyric(numericId).firstOrNull() ?: return null
        return ProviderLyric(
            plain = lyric.lrc?.lyric?.takeIf(String::isNotBlank),
            translation = lyric.tlyric?.lyric?.takeIf(String::isNotBlank),
            wordByWord = lyric.yrc?.lyric?.takeIf(String::isNotBlank),
            romanization = lyric.romalrc?.lyric?.takeIf(String::isNotBlank),
        ).takeIf { !it.isEmpty }
    }

    override suspend fun resolveStream(
        id: ProviderItemId,
        quality: SoundQualityPreference,
    ): StreamResolution {
        val numericId = id.neteaseIdOrNull
            ?: return StreamResolution.Failed(StreamFailureReason.NO_SOURCE)
        // The stored setting is the full NetEase ladder, which the backend accepts verbatim.
        // Round-tripping it through SoundQualityPreference would collapse the spatial tiers.
        val storedQuality = settings.getStringAsync(
            SettingKeys.ONLINE_PLAY_QUALITY,
            quality.neteaseQualityName(),
        )
        // The repository answers null both when the backend is down and when it has no URL for
        // the track; "returned no address" is true of both.
        return repository.getSongUrl(numericId, storedQuality)
            ?.let(StreamResolution::Playable)
            ?: StreamResolution.Failed(StreamFailureReason.NO_SOURCE)
    }

    override fun shareUrl(id: ProviderItemId): String? =
        id.neteaseIdOrNull?.let(::neteaseSongPageUrl)
}

/** The public web page of a NetEase song. */
internal fun neteaseSongPageUrl(songId: Long): String = "https://music.163.com/song?id=$songId"

private fun SoundQualityPreference.neteaseQualityName(): String = when (this) {
    SoundQualityPreference.STANDARD -> "standard"
    SoundQualityPreference.HIGH -> "exhigh"
    SoundQualityPreference.LOSSLESS -> "lossless"
    SoundQualityPreference.HIRES -> "hires"
}

internal fun SongDetailSongs.toProviderTrack(): ProviderTrack = ProviderTrack(
    id = ProviderItemId.netease(id),
    title = name,
    artists = ar.map { it.toProviderArtist() },
    album = al.toProviderAlbum(),
    durationMillis = dt,
    // The same reading the playlist pages give `fee`; NetEase's search rows carry no quality list.
    tags = when (fee) {
        1 -> setOf(TrackTag.VIP)
        4 -> setOf(TrackTag.PAID)
        else -> emptySet()
    },
)

private fun SongArtist.toProviderArtist(): ProviderArtist = ProviderArtist(
    id = if (id > 0) ProviderItemId.netease(id) else null,
    name = name,
)

private fun SongAlbum.toProviderAlbum(): ProviderAlbum = ProviderAlbum(
    id = if (id > 0) ProviderItemId.netease(id) else null,
    name = name,
    artworkUrl = picUrl,
)
