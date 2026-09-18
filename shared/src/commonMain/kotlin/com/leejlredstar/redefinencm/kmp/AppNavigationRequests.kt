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
