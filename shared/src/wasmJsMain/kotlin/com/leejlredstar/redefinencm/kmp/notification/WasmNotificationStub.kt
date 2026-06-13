package com.leejlredstar.redefinencm.kmp.notification

/**
 * Web/WASM stub — no-op implementation.
 * Web notifications are handled differently and are lower priority.
 */
actual object LyricNotificationController {
    actual fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String?,
        isPlaying: Boolean,
    ) { /* no-op for web */ }

    actual fun clearFocus() { /* no-op for web */ }
    actual fun reset() { /* no-op for web */ }
}
