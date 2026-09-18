package com.leejlredstar.redefinencm.kmp.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the current queue came from, in words the Now Playing header can show ("每日推荐",
 * "歌单「…」").
 *
 * Set by whoever replaces the queue; null when the queue has no describable origin, such as one
 * restored at startup. It lives beside the player rather than in it because the four platform
 * players carry media, not the reason it was chosen.
 */
object PlaybackSource {
    private val current = MutableStateFlow<String?>(null)
    val label: StateFlow<String?> = current.asStateFlow()

    fun set(label: String?) {
        current.value = label?.takeIf { it.isNotBlank() }
    }
}
