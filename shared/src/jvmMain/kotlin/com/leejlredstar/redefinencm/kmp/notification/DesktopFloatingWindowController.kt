package com.leejlredstar.redefinencm.kmp.notification

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop actual implementation of LyricNotificationController.
 *
 * Instead of a notification, this drives a floating desktop lyrics window.
 * The window is created by the desktop app's main.kt using Compose Desktop.
 * This controller manages the state that the window renders.
 */
actual object LyricNotificationController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _floatingLyricData = MutableStateFlow<FloatingLyricData?>(null)
    val floatingLyricData: StateFlow<FloatingLyricData?> = _floatingLyricData.asStateFlow()

    private val _isWindowVisible = MutableStateFlow(false)
    val isWindowVisible: StateFlow<Boolean> = _isWindowVisible.asStateFlow()

    actual fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String?,
    ) {
        _floatingLyricData.value = FloatingLyricData(
            title = title ?: "",
            artist = artist ?: "",
            currentLyric = currentLyric?.trim().orEmpty(),
            nextLyric = nextLyric?.trim().orEmpty(),
            artworkUri = artworkUri ?: "",
        )
    }

    actual fun clearFocus() {
        _floatingLyricData.value = null
    }

    actual fun reset() {
        _floatingLyricData.value = null
    }

    fun show() { _isWindowVisible.value = true }
    fun hide() { _isWindowVisible.value = false }
    fun toggle() { _isWindowVisible.value = !_isWindowVisible.value }
}

data class FloatingLyricData(
    val title: String,
    val artist: String,
    val currentLyric: String,
    val nextLyric: String,
    val artworkUri: String,
)
