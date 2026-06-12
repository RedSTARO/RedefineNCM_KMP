package com.leejlredstar.redefinencm.kmp.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared event bus for lyric state updates.
 * Mirrors the original PlaybackService.LyricBus pattern.
 */
object LyricBus {
    val lyricMapFlow = MutableStateFlow<LinkedHashMap<Long?, String?>>(linkedMapOf())
    val lyricIndexFlow = MutableStateFlow(0)
    val currentPosition = MutableStateFlow(0L)
    val isPlaying = MutableStateFlow(false)
    val songLength = MutableStateFlow(0L)
}
