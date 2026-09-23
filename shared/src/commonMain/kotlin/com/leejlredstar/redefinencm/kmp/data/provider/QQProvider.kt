package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.api.QQSong
import com.leejlredstar.redefinencm.kmp.data.api.QQSonglistDetail
import com.leejlredstar.redefinencm.kmp.data.api.qqAlbumArtworkUrl
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredentialRenewer
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.getBooleanAsync
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import kotlinx.coroutines.withTimeoutOrNull

/**
 * QQ Music, reached through a self-hosted `L-1124/QQMusicApi` web gateway.
 *
 * Nothing here is cached. The cache tables are keyed by a numeric NetEase song id, and giving them
 * a provider column is the one step in this plan that can destroy a year of real user data, so it
 * is deliberately left for its own change. QQ results cost a round trip every time until then.
 */
class QQProvider(
    private val api: QQMusicApi,
    private val settings: PlatformSettings,
    /** Keeps the signed-in account's key alive; null disables renewal. */
    private val renewer: QQCredentialRenewer? = null,
) : MusicProvider {
    override val id: MusicProviderId = MusicProviderId.QQ

    override suspend fun isAvailable(): Boolean =
        settings.getBooleanAsync(SettingKeys.QQ_ENABLED, false) &&
            settings.getStringAsync(SettingKeys.QQ_SERVER, SettingKeys.QQ_SERVER_DEFAULT).isNotBlank()

    override suspend fun search(keyword: String, limit: Int, offset: Int): List<ProviderTrack> {
        if (keyword.isBlank() || !isAvailable()) return emptyList()
        renewCredentialOnce()
        // Null is a transport failure, not an empty result set — see NeteaseProvider.search.
        // This backend pages by page number rather than by offset.
        val response = api.search(keyword, limit, page = offset / limit.coerceAtLeast(1) + 1)
            ?: throw ProviderUnavailableException(id, "QQ音乐后端无响应")
        return response.song
            .filter { it.mid.isNotBlank() }
            .map { it.toProviderTrack() }
    }

    override suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? {
        if (id.provider != MusicProviderId.QQ || !isAvailable()) return null
        val listId = id.rawId.toLongOrNull() ?: return null
        renewCredentialOnce()
        val pages = collectQQPlaylistPages(MaxPlaylistPages) { page ->
            api.playlistDetail(listId, PlaylistPageSize, page)
        }
        val first = pages.firstOrNull() ?: return null
        val tracks = pages.flatMap { page ->
            page.songs.filter { it.mid.isNotBlank() }.map { it.toProviderTrack() }
        }
        val info = first.info
        return ProviderPlaylist(
            id = id,
            name = info?.title.orEmpty(),
            coverUrl = info?.picurl.orEmpty(),
            description = info?.desc.orEmpty(),
            trackCount = when {
                info != null && info.songnum > 0 -> info.songnum
                first.total > 0 -> first.total
                else -> tracks.size
            },
            tracks = tracks,
        )
    }

    override suspend fun lyric(id: ProviderItemId): ProviderLyric? {
        if (id.provider != MusicProviderId.QQ || !isAvailable()) return null
        val lyric = api.lyric(id.rawId) ?: return null
        return ProviderLyric(
            plain = lyric.lyric.takeIf(String::isNotBlank),
            translation = lyric.trans.takeIf(String::isNotBlank),
            // The gateway can also hand over QRC, QQ's word-level format, but the lyric pipeline
            // parses YRC; converting one into the other is that pipeline's change, not this one's.
            wordByWord = null,
            romanization = lyric.roma.takeIf(String::isNotBlank),
        ).takeIf { !it.isEmpty }
    }

    /**
     * Walks down from the requested tier until one answers.
     *
     * QQ gates quality on account entitlement server-side, so a signed-out or non-VIP account gets
     * an empty URL for the tiers it may not fetch. Asking once at the user's configured quality
     * would make an entire provider look broken whenever that quality is set to lossless, which is
     * the app's default-ish case rather than a corner.
     *
     * The walk is time-bounded because of where it runs: Android resolves stream URLs under
     * `runBlocking` on ExoPlayer's IO thread, and the shared external client allows 30s per
     * request. A track that answers empty at every rung would otherwise hold playback for two
     * minutes before admitting defeat. The per-attempt bound keeps one slow rung from eating the
     * whole budget; the overall bound caps the walk however many rungs remain.
     */
    override suspend fun streamUrl(id: ProviderItemId, quality: SoundQualityPreference): String? {
        if (id.provider != MusicProviderId.QQ || !isAvailable()) return null
        renewCredentialOnce()
        return withTimeoutOrNull(StreamResolveBudgetMillis) {
            quality.qqFileTypeLadder().firstNotNullOfOrNull { fileType ->
                withTimeoutOrNull(StreamResolveAttemptMillis) { api.songUrl(id.rawId, fileType) }
            }
        }
    }

    /** See [QQCredentialRenewer.ensureFresh]: asks once per stored credential, not once per call. */
    private suspend fun renewCredentialOnce() {
        renewer?.ensureFresh()
    }

    private companion object {
        const val StreamResolveAttemptMillis = 6_000L
        const val StreamResolveBudgetMillis = 15_000L

        /** The gateway pages playlists; 200 a page keeps a typical list to one or two calls. */
        const val PlaylistPageSize = 200
        const val MaxPlaylistPages = 10
    }
}

/**
 * Reads a playlist page by page until the gateway clears `hasmore`, a page comes back empty or
 * unanswered, or [maxPages] is reached. A page that fails after others succeeded ends the walk
 * with what was read rather than dropping the playlist; the first page failing yields nothing.
 */
internal suspend fun collectQQPlaylistPages(
    maxPages: Int,
    fetchPage: suspend (page: Int) -> QQSonglistDetail?,
): List<QQSonglistDetail> {
    val pages = ArrayList<QQSonglistDetail>()
    var pageNumber = 1
    while (pageNumber <= maxPages) {
        val page = fetchPage(pageNumber) ?: break
        pages += page
        if (page.hasmore == 0 || page.songs.isEmpty()) break
        pageNumber += 1
    }
    return pages
}

/** The rows of the gateway's file-type table this app plays. */
internal object QQFileType {
    const val FLAC = 7
    const val MP3_320 = 12
    const val MP3_128 = 13
    const val AAC_96 = 15
}

/**
 * QQ's tiers, highest first, starting at the rung matching the user's preference.
 *
 * Lossless stops at FLAC: the mastered and spatial rows above it are formats the players here do
 * not all handle, so trying them would only add round trips.
 */
internal fun SoundQualityPreference.qqFileTypeLadder(): List<Int> {
    val ladder = listOf(QQFileType.FLAC, QQFileType.MP3_320, QQFileType.MP3_128, QQFileType.AAC_96)
    val startAt = when (this) {
        SoundQualityPreference.HIRES, SoundQualityPreference.LOSSLESS -> 0
        SoundQualityPreference.HIGH -> 1
        SoundQualityPreference.STANDARD -> 2
    }
    return ladder.drop(startAt)
}

internal fun QQSong.toProviderTrack(): ProviderTrack = ProviderTrack(
    id = ProviderItemId.qq(mid),
    title = name.ifBlank { title },
    artists = singer.map { artist ->
        ProviderArtist(
            id = artist.mid.takeIf(String::isNotBlank)?.let(ProviderItemId::qq),
            name = artist.name,
        )
    },
    album = album?.let {
        ProviderAlbum(
            id = it.mid.takeIf(String::isNotBlank)?.let(ProviderItemId::qq),
            name = it.name,
            artworkUrl = qqAlbumArtworkUrl(it),
        )
    },
    durationMillis = interval * 1000,
)
