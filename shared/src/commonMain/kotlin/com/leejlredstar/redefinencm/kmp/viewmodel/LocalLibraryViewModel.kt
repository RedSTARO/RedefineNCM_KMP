package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryDocument
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryStore
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId
import com.leejlredstar.redefinencm.kmp.data.toPlayerMediaInfo
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
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
 */
class LocalLibraryViewModel(
    private val store: LocalLibraryStore,
    private val providers: MusicProviderRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Null until the stored library has been read. */
    val library: StateFlow<LocalLibraryDocument?> = store.updates()
        .stateIn(scope, SharingStarted.Eagerly, null)

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

    fun consumeMessage() {
        _message.value = null
    }

    /** Opens the add dialog for [tracks]. */
    fun requestAddition(tracks: List<MediaInfo>, suggestedName: String = "") {
        if (tracks.isNotEmpty()) _pendingAddition.value = PendingAddition(tracks, suggestedName)
    }

    fun dismissAddition() {
        _pendingAddition.value = null
    }

    fun addPendingTo(playlistId: String) {
        val pending = _pendingAddition.value ?: return
        _pendingAddition.value = null
        scope.launch {
            val name = store.snapshot().playlist(playlistId)?.name.orEmpty()
            store.addTracks(playlistId, pending.tracks)
                .onSuccess { added ->
                    _message.value = when {
                        added == 0 -> "「$name」里已经有这些歌"
                        pending.tracks.size == 1 -> "已添加到「$name」"
                        else -> "已把 $added 首歌添加到「$name」"
                    }
                }
                .onFailure { failure -> _message.value = failure.message ?: "添加失败" }
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
                        "已新建「${playlist.name}」"
                    } else {
                        "已新建「${playlist.name}」，共 ${playlist.tracks.size} 首"
                    }
                }
                .onFailure { failure -> _message.value = failure.message ?: "新建失败" }
        }
    }

    fun renamePlaylist(id: String, name: String) {
        scope.launch {
            store.renamePlaylist(id, name).onFailure { failure -> _message.value = failure.message ?: "重命名失败" }
        }
    }

    fun deletePlaylist(id: String) {
        scope.launch {
            store.deletePlaylist(id)
                .onSuccess { _message.value = "已删除歌单" }
                .onFailure { failure -> _message.value = failure.message ?: "删除失败" }
        }
    }

    fun removeTrack(playlistId: String, trackKey: String) {
        scope.launch {
            store.removeTrack(playlistId, trackKey).onFailure { failure -> _message.value = failure.message ?: "移除失败" }
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
            _message.value = "无法识别这个歌单链接"
            return
        }
        _importing.value = true
        scope.launch {
            try {
                val playlist = providers.playlistDetail(id)
                if (playlist == null || playlist.tracks.isEmpty()) {
                    _message.value = "没能读取这个${id.provider.displayName}歌单"
                    return@launch
                }
                store.createPlaylist(playlist.name, playlist.tracks.map { it.toPlayerMediaInfo() })
                    .onSuccess { created ->
                        _message.value = "已导入「${created.name}」，共 ${created.tracks.size} 首"
                    }
                    .onFailure { failure -> _message.value = failure.message ?: "导入失败" }
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
    if ("qq.com" in text) {
        val id = QQPlaylistPath.find(text)?.groupValues?.get(1)
            ?: IdParameter.find(text)?.groupValues?.get(1)
            ?: return null
        return ProviderItemId(MusicProviderId.QQ, id)
    }
    if ("163.com" in text || "163cn.tv" in text) {
        val id = IdParameter.find(text)?.groupValues?.get(1)
            ?: NeteasePlaylistPath.find(text)?.groupValues?.get(1)
            ?: return null
        return ProviderItemId.netease(id.toLong())
    }
    val parsed = ProviderItemId.parseOrNull(text) ?: return null
    // A playlist id is a number on both services; anything else is not one.
    return parsed.takeIf { it.rawId.all(Char::isDigit) }
}

private val QQPlaylistPath = Regex("""playlist/(\d+)""")
private val NeteasePlaylistPath = Regex("""playlist/(\d+)""")
private val IdParameter = Regex("""[?&]id=(\d+)""")
