package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.api.QQPlaylistSong
import com.leejlredstar.redefinencm.kmp.data.api.QQSearchSong
import com.leejlredstar.redefinencm.kmp.data.api.QQSinger
import com.leejlredstar.redefinencm.kmp.data.api.qqAlbumArtworkUrl
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.withTimeoutOrNull

/**
 * QQ Music, reached through a self-hosted `Rain120/qq-music-api` backend.
 *
 * Nothing here is cached. The cache tables are keyed by a numeric NetEase song id, and giving them
 * a provider column is the one step in this plan that can destroy a year of real user data, so it
 * is deliberately left for its own change. QQ results cost a round trip every time until then.
 */
class QQProvider(
    private val api: QQMusicApi,
    private val settings: PlatformSettings,
) : MusicProvider {
    override val id: MusicProviderId = MusicProviderId.QQ

    override suspend fun isAvailable(): Boolean =
        settings.getBooleanAsync(SettingKeys.QQ_ENABLED, false) &&
            settings.getStringAsync(SettingKeys.QQ_SERVER, SettingKeys.QQ_SERVER_DEFAULT).isNotBlank()

    override suspend fun search(keyword: String, limit: Int): List<ProviderTrack> {
        if (keyword.isBlank() || !isAvailable()) return emptyList()
        // Null is a transport failure, not an empty result set — see NeteaseProvider.search.
        val response = api.search(keyword, limit)
            ?: throw ProviderUnavailableException(id, "QQ音乐后端无响应")
        return response.data
            ?.song
            ?.list
            .orEmpty()
            .filter { it.songmid.isNotBlank() }
            .map { it.toProviderTrack() }
    }

    override suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? {
        if (id.provider != MusicProviderId.QQ || !isAvailable()) return null
        val entry = api.playlistDetail(id.rawId)?.cdlist?.firstOrNull() ?: return null
        val tracks = entry.songlist
            .filter { it.mid.isNotBlank() }
            .map { it.toProviderTrack() }
        return ProviderPlaylist(
            id = id,
            name = entry.dissname,
            coverUrl = entry.logo,
            description = entry.desc,
            trackCount = if (entry.songnum > 0) entry.songnum else tracks.size,
            tracks = tracks,
        )
    }

    override suspend fun lyric(id: ProviderItemId): ProviderLyric? {
        if (id.provider != MusicProviderId.QQ || !isAvailable()) return null
        val lyric = api.lyric(id.rawId) ?: return null
        return ProviderLyric(
            plain = lyric.lyric.takeIf(String::isNotBlank),
            translation = lyric.trans.takeIf(String::isNotBlank),
            // QQ's word-level format (QRC) is not exposed by this backend, and it has no
            // romanization track at all.
            wordByWord = null,
            romanization = null,
        ).takeIf { !it.isEmpty }
    }

    /**
     * Walks down from the requested tier until one answers.
     *
     * QQ gates quality on account entitlement server-side, so a signed-out or non-VIP backend
     * returns an empty URL for everything above `128` and for VIP tracks at every tier. Asking
     * once at the user's configured quality would make an entire provider look broken whenever
     * that quality is set to lossless, which is the app's default-ish case rather than a corner.
     *
     * The walk is time-bounded because of where it runs: Android resolves stream URLs under
     * `runBlocking` on ExoPlayer's IO thread, and the shared external client allows 30s per
     * request. A VIP track answers empty at every rung, so an unbounded walk would hold playback
     * for two minutes before admitting defeat. The per-attempt bound keeps one slow rung from
     * eating the whole budget; the overall bound caps the walk however many rungs remain.
     *
     * The `size128`/`sizeflac` fields on a search row cannot shortcut this: 晴天 reports
     * `size128 = 4308000` and still resolves empty, so they describe which encodings exist, not
     * which the configured account may fetch.
     */
    override suspend fun streamUrl(id: ProviderItemId, quality: SoundQualityPreference): String? {
        if (id.provider != MusicProviderId.QQ || !isAvailable()) return null
        return withTimeoutOrNull(StreamResolveBudgetMillis) {
            quality.qqTierLadder().firstNotNullOfOrNull { tier ->
                withTimeoutOrNull(StreamResolveAttemptMillis) { api.songUrl(id.rawId, tier) }
            }
        }
    }

    private companion object {
        const val StreamResolveAttemptMillis = 6_000L
        const val StreamResolveBudgetMillis = 15_000L
    }
}

/**
 * QQ's tiers, highest first, starting at the rung matching the user's preference.
 *
 * `ape` is omitted: it is the same lossless content as `flac` in a container the players here do
 * not all handle, so trying it would only add a round trip.
 */
internal fun SoundQualityPreference.qqTierLadder(): List<String> {
    val ladder = listOf("flac", "320", "128", "m4a")
    val startAt = when (this) {
        SoundQualityPreference.HIRES, SoundQualityPreference.LOSSLESS -> 0
        SoundQualityPreference.HIGH -> 1
        SoundQualityPreference.STANDARD -> 2
    }
    return ladder.drop(startAt)
}

internal fun QQSearchSong.toProviderTrack(): ProviderTrack = ProviderTrack(
    id = ProviderItemId.qq(songmid),
    title = songname,
    artists = singer.map { it.toProviderArtist() },
    album = ProviderAlbum(
        id = albummid.takeIf(String::isNotBlank)?.let { ProviderItemId.qq(it) },
        name = albumname,
        artworkUrl = qqAlbumArtworkUrl(albummid),
    ),
    durationMillis = interval * 1000,
)

internal fun QQPlaylistSong.toProviderTrack(): ProviderTrack = ProviderTrack(
    id = ProviderItemId.qq(mid),
    title = name,
    artists = singer.map { it.toProviderArtist() },
    album = album?.let {
        ProviderAlbum(
            id = it.mid.takeIf(String::isNotBlank)?.let { mid -> ProviderItemId.qq(mid) },
            name = it.name,
            artworkUrl = qqAlbumArtworkUrl(it.mid),
        )
    },
    durationMillis = interval * 1000,
)

private fun QQSinger.toProviderArtist(): ProviderArtist = ProviderArtist(
    id = mid.takeIf(String::isNotBlank)?.let { ProviderItemId.qq(it) },
    name = name,
)
