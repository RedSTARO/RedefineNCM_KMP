package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory cache of downloaded song IDs to avoid repeated file-system scans.
 *
 * The Android [isSongDownloaded] actual used to call [File.listFiles] per song,
 * causing UI jank when many rows called it at composition time. This singleton
 * scans the download directory once into a [Set] for O(1) lookups.
 */
object DownloadedSongsCache {
    // @Volatile：后台预热线程、播放器解析线程、Compose 组合线程都会读它，
    // 保证一个线程写入的扫描结果对其他线程立即可见（否则会重复扫描）。
    @Volatile
    private var cached: Set<Long>? = null

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun isDownloaded(songId: Long): Boolean {
        val snapshot = cached ?: scanDownloadedSongIds().also { cached = it }
        return songId in snapshot
    }

    fun refresh() {
        cached = scanDownloadedSongIds()
        _version.value = _version.value + 1
    }
}

/** Platform-specific: scan the download directory and return all downloaded song IDs. */
expect fun scanDownloadedSongIds(): Set<Long>
