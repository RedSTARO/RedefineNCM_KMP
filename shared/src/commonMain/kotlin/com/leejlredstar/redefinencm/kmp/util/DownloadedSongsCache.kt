package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory cache of downloaded songs to avoid repeated file-system scans.
 *
 * The Android [isSongDownloaded] actual used to call [File.listFiles] per song,
 * causing UI jank when many rows called it at composition time. This singleton
 * scans the download directory once into a snapshot map for O(1) lookups.
 */
object DownloadedSongsCache {
    // @Volatile：后台预热线程、播放器解析线程、Compose 组合线程都会读它，
    // 保证一个线程写入的扫描结果对其他线程立即可见（否则会重复扫描）。
    @Volatile
    private var cached: Map<Long, DownloadedSongSnapshot>? = null

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun isDownloaded(songId: Long): Boolean {
        val snapshot = cached ?: scanSnapshot().also { cached = it }
        return songId in snapshot
    }

    fun refresh(): Set<Long> = refreshAndGet()

    fun refreshAndGet(): Set<Long> {
        val snapshot = refreshSnapshots()
        return snapshot.keys
    }

    fun snapshot(): Map<Long, DownloadedSongSnapshot> =
        cached ?: scanSnapshot().also { cached = it }

    fun refreshSnapshots(): Map<Long, DownloadedSongSnapshot> {
        val snapshot = scanSnapshot()
        cached = snapshot
        _version.update { it + 1 }
        return snapshot
    }

    private fun scanSnapshot(): Map<Long, DownloadedSongSnapshot> {
        val result = linkedMapOf<Long, DownloadedSongSnapshot>()
        scanDownloadedSongs().forEach { snapshot ->
            result.putIfAbsent(snapshot.id, snapshot)
        }
        return result
    }
}
