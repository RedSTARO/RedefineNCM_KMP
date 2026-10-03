package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.local.LocalAccount
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryDocument
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryStore
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId
import com.leejlredstar.redefinencm.kmp.data.toPlayerMediaInfo
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The local account's playlists and favourites for the pages that show and change them, and the
 * "add to a local playlist" dialog every song menu opens.
 *
 * While the local account is switched off, every way into the library is hidden and this adds
 * nothing to it; what it holds is kept.
 */
class LocalLibraryViewModel(
    private val store: LocalLibraryStore,
    private val providers: MusicProviderRegistry,
    private val localAccount: LocalAccount,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Null until the stored library has been read. */
    val library: StateFlow<LocalLibraryDocument?> = store.updates()
        .stateIn(scope, SharingStarted.Eagerly, null)
    val readFailed = store.readFailed
    private val _reloading = MutableStateFlow(false)
    val reloading = _reloading.asStateFlow()

    fun retryLoad() {
        if (_reloading.value) return
        _reloading.value = true
        scope.launch {
            try { store.reload() } finally { _reloading.value = false }
        }
    }

    /**
     * Whether the local account is switched on, for showing the ways into its library. False until
     * the stored switch has been read, so nothing of it appears before that.
     */
    val enabled: StateFlow<Boolean> = localAccount.enabledUpdates()
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /** Songs waiting for the add dialog to be told where they go; null when it is closed. */
    private val _pendingAddition = MutableStateFlow<PendingAddition?>(null)
    val pendingAddition: StateFlow<PendingAddition?> = _pendingAddition.asStateFlow()

    data class PendingAddition(
        val tracks: List<MediaInfo>,
        /** The name a new playlist starts with, such as the playlist the songs came from. */
        val suggestedName: String = "",
    )

    init {
        // An add dialog still open when the account is switched off closes. This follows the
        // stored switch only, never the default the state above starts from.
        scope.launch {
            localAccount.enabledUpdates().collect { on ->
                if (!on) _pendingAddition.value = null
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Opens the add dialog for [tracks], while the local account is switched on. */
    fun requestAddition(tracks: List<MediaInfo>, suggestedName: String = "") {
        if (tracks.isNotEmpty() && enabled.value) {
            _pendingAddition.value = PendingAddition(tracks, suggestedName)
        }
    }

    fun dismissAddition() {
        _pendingAddition.value = null
    }

    fun addPendingTo(playlistId: String) {
        val pending = _pendingAddition.value ?: return
        _pendingAddition.value = null
        scope.launch {
            try {
                val name = store.snapshot().playlist(playlistId)?.displayName.orEmpty()
                val added = store.addTracks(playlistId, pending.tracks).getOrThrow()
                _message.value = when {
                    added == 0 -> strings.songsAlreadyInPlaylist(name)
                    pending.tracks.size == 1 -> strings.addedToPlaylist(name)
                    else -> strings.addedSongsToPlaylist(added, name)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _message.value = failure.message ?: strings.addToPlaylistFailed }
        }
    }

    fun addPendingToNewPlaylist(name: String) {
        val pending = _pendingAddition.value ?: return
        _pendingAddition.value = null
        createPlaylist(name, pending.tracks)
    }

    fun createPlaylist(name: String, tracks: List<MediaInfo> = emptyList()) {
        scope.launch {
            store.createPlaylist(name, tracks)
                .onSuccess { playlist ->
                    _message.value = if (tracks.isEmpty()) {
                        strings.playlistCreated(playlist.name)
                    } else {
                        strings.playlistCreatedWithSongs(playlist.name, playlist.tracks.size)
                    }
                }
                .onFailure { failure -> _message.value = failure.message ?: strings.playlistCreateFailed }
        }
    }

    fun renamePlaylist(id: String, name: String) {
        scope.launch {
            store.renamePlaylist(id, name).onFailure { failure -> _message.value = failure.message ?: strings.playlistRenameFailed }
        }
    }

    fun deletePlaylist(id: String) {
        scope.launch {
            store.deletePlaylist(id)
                .onSuccess { _message.value = strings.playlistDeleted }
                .onFailure { failure -> _message.value = failure.message ?: strings.playlistDeleteFailed }
        }
    }

    fun removeTrack(playlistId: String, trackKey: String) {
        scope.launch {
            store.removeTrack(playlistId, trackKey).onFailure { failure -> _message.value = failure.message ?: strings.playlistRemoveSongFailed }
        }
    }

    /**
     * Copies a provider's playlist into a new local one, from a link or an id the user pasted:
     * `https://y.qq.com/n/ryqq/playlist/…`, `https://music.163.com/playlist?id=…`, `qq:…`, or a
     * bare number, which is read as NetEase's the way every bare id in the app is.
     */
    fun importPlaylist(reference: String) {
        val id = parsePlaylistReference(reference)
        if (id == null) {
            _message.value = strings.playlistLinkUnrecognized
            return
        }
        _importing.value = true
        scope.launch {
            try {
                val playlist = providers.playlistDetail(id)
                if (playlist == null || playlist.tracks.isEmpty()) {
                    _message.value = strings.providerPlaylistReadFailed(id.provider.displayName)
                    return@launch
                }
                store.createPlaylist(playlist.name, playlist.tracks.map { it.toPlayerMediaInfo() })
                    .onSuccess { created ->
                        _message.value = strings.playlistImported(created.name, created.tracks.size)
                    }
                    .onFailure { failure -> _message.value = failure.message ?: strings.playlistImportFailed }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _message.value = strings.playlistImportFailed
            } finally {
                _importing.value = false
            }
        }
    }
}

/**
 * The provider playlist a pasted link or id names, or null when it names none. Links are matched
 * on their host, so a NetEase link is never read as QQ's.
 */
internal fun parsePlaylistReference(input: String): ProviderItemId? {
    val text = input.trim()
    if (text.isEmpty()) return null
    if (!text.contains("://")) {
        val parsed = ProviderItemId.parseOrNull(text) ?: return null
        return numericPlaylistId(parsed.provider, parsed.rawId)
    }
    val url = runCatching { Url(text) }.getOrNull() ?: return null
    if (url.protocol.name !in setOf("http", "https") || url.user != null || url.password != null) return null
    val host = url.host.lowercase()
    val provider = when {
        host == "music.163.com" || host.endsWith(".music.163.com") -> MusicProviderId.NETEASE
        host == "y.qq.com" || host.endsWith(".y.qq.com") -> MusicProviderId.QQ
        else -> return null
    }
    // NetEase's desktop share link puts its route and query inside the fragment.
    val route = if (url.fragment.startsWith('/')) {
        runCatching { Url("https://$host${url.fragment}") }.getOrNull() ?: return null
    } else url
    val path = route.encodedPath.trimEnd('/')
    val id = when (provider) {
        MusicProviderId.NETEASE -> {
            if (path == "/playlist" || path == "/m/playlist") route.parameters["id"]
            else NeteasePlaylistPath.matchEntire(path)?.groupValues?.get(1)
        }
        MusicProviderId.QQ -> {
            if (path == "/n2/m/share/details/taoge.html") route.parameters["id"]
            else QQPlaylistPath.matchEntire(path)?.groupValues?.get(1)
        }
    } ?: return null
    return numericPlaylistId(provider, id)
}

private fun numericPlaylistId(provider: MusicProviderId, raw: String): ProviderItemId? {
    if (raw.isEmpty() || raw.any { it !in '0'..'9' }) return null
    val number = raw.toLongOrNull()?.takeIf { it > 0 } ?: return null
    return ProviderItemId(provider, number.toString())
}

private val QQPlaylistPath = Regex("""/(?:n/ryqq/)?playlist/([0-9]+)""")
private val NeteasePlaylistPath = Regex("""/(?:m/)?playlist/([0-9]+)""")
