package com.leejlredstar.redefinencm.kmp.data.local

import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId
import com.leejlredstar.redefinencm.kmp.data.provider.mediaId
import com.leejlredstar.redefinencm.kmp.data.provider.toProviderItemIdOrNull
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock

/**
 * The device-local account's library: playlists whose tracks may come from any provider, and the
 * local favourites that give every track a heart.
 */
@Serializable
data class LocalLibraryDocument(
    val playlists: List<LocalPlaylist> = emptyList(),
) {
    /** The one favourites list, once anything has been favourited. */
    val favorites: LocalPlaylist? get() = playlists.firstOrNull { it.kind == LocalPlaylist.Kind.FAVORITES }

    /** The playlists the user made, in the order they were made. */
    val userPlaylists: List<LocalPlaylist> get() = playlists.filter { it.kind == LocalPlaylist.Kind.PLAYLIST }

    fun playlist(id: String): LocalPlaylist? = playlists.firstOrNull { it.id == id }

    fun isFavorite(mediaId: String): Boolean {
        val key = LocalTrack.keyOf(mediaId) ?: return false
        return favorites?.tracks?.any { it.key == key } == true
    }
}

@Serializable
data class LocalPlaylist(
    /** Stable across devices, so importing a backup replaces a playlist instead of copying it. */
    val id: String,
    val name: String,
    val kind: Kind = Kind.PLAYLIST,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val tracks: List<LocalTrack> = emptyList(),
) {
    @Serializable
    enum class Kind { PLAYLIST, FAVORITES }
}

/**
 * One track of a local playlist, named by its provider and that provider's own id. It needs no
 * NetEase song id, so a QQ track keeps its place beside a NetEase one.
 */
@Serializable
data class LocalTrack(
    /** [MusicProviderId.key]: `ncm`, `qq`. */
    val provider: String,
    val rawId: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    /** Only a web address: a device's local artwork URI is never stored (AGENTS.md). */
    val artworkUri: String = "",
    val durationMillis: Long = 0,
    val addedAtMillis: Long = 0,
) {
    /** Null for a provider this build does not know; the track is kept but cannot play. */
    val itemId: ProviderItemId?
        get() = MusicProviderId.fromKey(provider)?.let { ProviderItemId(it, rawId) }

    val key: String get() = "$provider:$rawId"

    /** The queue item for this track, or null when its provider is unknown here. */
    fun toMediaInfo(): MediaInfo? {
        val mediaId = itemId?.mediaId ?: return null
        return MediaInfo(
            id = mediaId,
            title = title,
            artist = artist,
            albumTitle = album,
            artworkUri = artworkUri,
            placeholderUri = "redefinencm://playbackPlaceHolder?id=$mediaId",
            duration = durationMillis,
        )
    }

    companion object {
        /** The key a queue item's id stands for, or null for an id no provider claims. */
        fun keyOf(mediaId: String): String? =
            mediaId.toProviderItemIdOrNull()?.let { "${it.provider.key}:${it.rawId}" }

        fun from(media: MediaInfo, addedAtMillis: Long): LocalTrack? {
            val id = media.id.toProviderItemIdOrNull() ?: return null
            return LocalTrack(
                provider = id.provider.key,
                rawId = id.rawId,
                title = media.title,
                artist = media.artist,
                album = media.albumTitle,
                artworkUri = media.artworkUri.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    .orEmpty(),
                durationMillis = media.duration,
                addedAtMillis = addedAtMillis,
            )
        }
    }
}

/**
 * Reads and writes the local library, one JSON document in the `LocalLibrary` table.
 *
 * Writes are serialized and each rewrites the document; pages follow [updates], so a track added
 * from a song's menu shows on the playlist page without the page asking again.
 */
class LocalLibraryStore(
    private val database: AppDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val writes = Mutex()

    /** Null until the stored document has been read once. */
    private val current = MutableStateFlow<LocalLibraryDocument?>(null)

    /** The library now, then after every change. */
    fun updates(): Flow<LocalLibraryDocument> = flow {
        if (current.value == null) writes.withLock { load() }
        emitAll(current.filterNotNull())
    }

    suspend fun snapshot(): LocalLibraryDocument = writes.withLock { load() }

    suspend fun createPlaylist(name: String, tracks: List<MediaInfo> = emptyList()): Result<LocalPlaylist> =
        change { library ->
            val time = now()
            val playlist = LocalPlaylist(
                id = newId(time),
                name = name.trim().ifEmpty { UntitledPlaylistName },
                createdAtMillis = time,
                updatedAtMillis = time,
                tracks = tracks.mapNotNull { LocalTrack.from(it, time) }.distinctBy { it.key },
            )
            library.copy(playlists = library.playlists + playlist) to playlist
        }

    suspend fun renamePlaylist(id: String, name: String): Result<Unit> = change { library ->
        library.updatePlaylist(id) { it.copy(name = name.trim().ifEmpty { it.name }, updatedAtMillis = now()) } to Unit
    }

    suspend fun deletePlaylist(id: String): Result<Unit> = change { library ->
        library.copy(playlists = library.playlists.filterNot { it.id == id }) to Unit
    }

    /** Appends the tracks the playlist does not hold yet; the answer is how many were added. */
    suspend fun addTracks(playlistId: String, tracks: List<MediaInfo>): Result<Int> = change { library ->
        val playlist = library.playlist(playlistId) ?: error("本地歌单不存在")
        val time = now()
        val held = playlist.tracks.mapTo(mutableSetOf()) { it.key }
        val added = tracks.mapNotNull { LocalTrack.from(it, time) }
            .filter { held.add(it.key) }
        library.updatePlaylist(playlistId) {
            it.copy(tracks = it.tracks + added, updatedAtMillis = time)
        } to added.size
    }

    suspend fun removeTrack(playlistId: String, trackKey: String): Result<Unit> = change { library ->
        library.updatePlaylist(playlistId) { playlist ->
            playlist.copy(tracks = playlist.tracks.filterNot { it.key == trackKey }, updatedAtMillis = now())
        } to Unit
    }

    /** Adds [media] to the local favourites or takes it out; the list is made on first use. */
    suspend fun setFavorite(media: MediaInfo, favorite: Boolean): Result<Unit> = change { library ->
        val time = now()
        val track = LocalTrack.from(media, time) ?: error("无法识别这首歌")
        val favorites = library.favorites ?: LocalPlaylist(
            id = newId(time),
            name = FavoritesName,
            kind = LocalPlaylist.Kind.FAVORITES,
            createdAtMillis = time,
            updatedAtMillis = time,
        )
        val tracks = favorites.tracks.filterNot { it.key == track.key }
        val updated = favorites.copy(
            tracks = if (favorite) listOf(track) + tracks else tracks,
            updatedAtMillis = time,
        )
        val others = library.playlists.filterNot { it.id == favorites.id }
        library.copy(playlists = listOf(updated) + others) to Unit
    }

    /**
     * Takes playlists from a backup: one with an id held here replaces it, the rest are added,
     * and nothing here is removed. Favourites from another device join these favourites rather
     * than making a second list.
     */
    suspend fun importPlaylists(imported: List<LocalPlaylist>): Result<Int> = change { library ->
        var playlists = library.playlists
        imported.forEach { incoming ->
            val localFavorites = playlists.firstOrNull { it.kind == LocalPlaylist.Kind.FAVORITES }
            playlists = when {
                incoming.kind == LocalPlaylist.Kind.FAVORITES && localFavorites != null -> {
                    val held = localFavorites.tracks.mapTo(mutableSetOf()) { it.key }
                    val merged = localFavorites.copy(
                        tracks = localFavorites.tracks + incoming.tracks.filter { held.add(it.key) },
                        updatedAtMillis = now(),
                    )
                    playlists.map { if (it.id == localFavorites.id) merged else it }
                }
                playlists.any { it.id == incoming.id } ->
                    playlists.map { if (it.id == incoming.id) incoming else it }
                else -> playlists + incoming
            }
        }
        library.copy(playlists = playlists) to imported.size
    }

    private suspend fun <T> change(
        transform: (LocalLibraryDocument) -> Pair<LocalLibraryDocument, T>,
    ): Result<T> = writes.withLock {
        try {
            val (updated, answer) = transform(load())
            withContext(Dispatchers.Default) {
                database.localLibraryQueries.upsert(LibraryJson.encodeToString(LocalLibraryDocument.serializer(), updated))
            }
            current.value = updated
            Result.success(answer)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    /** The document, read from the table the first time; called under [writes]. */
    private suspend fun load(): LocalLibraryDocument {
        current.value?.let { return it }
        val stored = withContext(Dispatchers.Default) {
            database.localLibraryQueries.select().executeAsOneOrNull()
        }
        val document = stored
            ?.let { runCatching { LibraryJson.decodeFromString(LocalLibraryDocument.serializer(), it) }.getOrNull() }
            ?: LocalLibraryDocument()
        current.value = document
        return document
    }

    private fun LocalLibraryDocument.updatePlaylist(
        id: String,
        change: (LocalPlaylist) -> LocalPlaylist,
    ): LocalLibraryDocument {
        require(playlists.any { it.id == id }) { "本地歌单不存在" }
        return copy(playlists = playlists.map { if (it.id == id) change(it) else it })
    }

    private fun newId(time: Long): String = "local-$time-${Random.nextInt(0, 1_000_000)}"

    companion object {
        const val FavoritesName = "本地喜欢"
        const val UntitledPlaylistName = "未命名歌单"

        internal val LibraryJson = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }
}
