package com.leejlredstar.redefinencm.kmp.data.provider

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

    /**
     * NetEase works signed out for search and most lyrics, so availability is about having a
     * backend to talk to at all, not about being logged in.
     */
    override suspend fun isAvailable(): Boolean =
        settings.getStringAsync(SettingKeys.SERVER, "").isNotBlank()

    override suspend fun search(keyword: String, limit: Int): List<ProviderTrack> {
        if (keyword.isBlank()) return emptyList()
        return repository.search(keyword)
            ?.result
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

    override suspend fun streamUrl(id: ProviderItemId, quality: SoundQualityPreference): String? {
        val numericId = id.neteaseIdOrNull ?: return null
        // The stored setting is the full NetEase ladder, which the backend accepts verbatim.
        // Round-tripping it through SoundQualityPreference would collapse the spatial tiers.
        val storedQuality = settings.getStringAsync(
            SettingKeys.ONLINE_PLAY_QUALITY,
            quality.neteaseQualityName(),
        )
        return repository.getSongUrl(numericId, storedQuality)
    }
}

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
