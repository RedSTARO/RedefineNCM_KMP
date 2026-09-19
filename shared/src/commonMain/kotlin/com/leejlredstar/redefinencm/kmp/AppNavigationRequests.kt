package com.leejlredstar.redefinencm.kmp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object AppNavigationRequests {
    private val _openDownloadsRequestId = MutableStateFlow(0)
    private var consumedOpenDownloadsRequestId = 0
    private val _openNowPlayingRequestId = MutableStateFlow(0)
    private var consumedOpenNowPlayingRequestId = 0
    private val _openSearchRequestId = MutableStateFlow(0)
    private var consumedOpenSearchRequestId = 0

    val openDownloadsRequestId: StateFlow<Int> = _openDownloadsRequestId.asStateFlow()
    val openNowPlayingRequestId: StateFlow<Int> = _openNowPlayingRequestId.asStateFlow()
    val openSearchRequestId: StateFlow<Int> = _openSearchRequestId.asStateFlow()

    /** A request to open a page for one item, numbered so a repeat is told from a replay. */
    data class ItemRequest(val serial: Int, val id: Long)

    private val _openArtistRequest = MutableStateFlow<ItemRequest?>(null)
    private var consumedOpenArtistSerial = 0
    private val _openAlbumRequest = MutableStateFlow<ItemRequest?>(null)
    private var consumedOpenAlbumSerial = 0

    /** Song menus and the player open artists and albums from anywhere through these. */
    val openArtistRequest: StateFlow<ItemRequest?> = _openArtistRequest.asStateFlow()
    val openAlbumRequest: StateFlow<ItemRequest?> = _openAlbumRequest.asStateFlow()

    fun openArtist(id: Long) {
        _openArtistRequest.update { ItemRequest((it?.serial ?: 0) + 1, id) }
    }

    fun openAlbum(id: Long) {
        _openAlbumRequest.update { ItemRequest((it?.serial ?: 0) + 1, id) }
    }

    fun consumeOpenArtistRequest(request: ItemRequest?): Boolean {
        if (request == null || request.serial == consumedOpenArtistSerial) return false
        consumedOpenArtistSerial = request.serial
        return true
    }

    fun consumeOpenAlbumRequest(request: ItemRequest?): Boolean {
        if (request == null || request.serial == consumedOpenAlbumSerial) return false
        consumedOpenAlbumSerial = request.serial
        return true
    }

    fun openDownloads() {
        _openDownloadsRequestId.update { it + 1 }
    }

    fun openNowPlaying() {
        // OS now-playing surfaces and deep links open the sole full-screen player route.
        _openNowPlayingRequestId.update { it + 1 }
    }

    /** Keyboard shortcut (Ctrl/⌘+F) and other shells asking for the search page. */
    fun openSearch() {
        _openSearchRequestId.update { it + 1 }
    }

    fun consumeOpenSearchRequest(requestId: Int): Boolean {
        if (requestId <= 0 || requestId == consumedOpenSearchRequestId) return false
        consumedOpenSearchRequestId = requestId
        return true
    }

    fun consumeOpenDownloadsRequest(requestId: Int): Boolean {
        if (requestId <= 0 || requestId == consumedOpenDownloadsRequestId) return false
        consumedOpenDownloadsRequestId = requestId
        return true
    }

    fun consumeOpenNowPlayingRequest(requestId: Int): Boolean {
        if (requestId <= 0 || requestId == consumedOpenNowPlayingRequestId) return false
        consumedOpenNowPlayingRequestId = requestId
        return true
    }
}
