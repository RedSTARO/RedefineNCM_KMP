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

    val openDownloadsRequestId: StateFlow<Int> = _openDownloadsRequestId.asStateFlow()
    val openNowPlayingRequestId: StateFlow<Int> = _openNowPlayingRequestId.asStateFlow()

    fun openDownloads() {
        _openDownloadsRequestId.update { it + 1 }
    }

    fun openNowPlaying() {
        _openNowPlayingRequestId.update { it + 1 }
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
