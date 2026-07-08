package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

/**
 * In-memory cache of downloaded songs to avoid repeated file-system scans.
 *
 * The Android [isSongDownloaded] actual used to call [File.listFiles] per song,
 * causing UI jank when many rows called it at composition time. This singleton
 * keeps a process-local snapshot map for O(1) lookups. Full directory scans must be
 * triggered by explicit sync points; UI reads never perform disk I/O.
 */
object DownloadedSongsCache {
    // @Volatile：后台预热线程、播放器解析线程、Compose 组合线程都会读它，
    // 保证一个线程写入的扫描结果对其他线程立即可见（否则会重复扫描）。
    @Volatile
    private var cached: Map<Long, DownloadedSongSnapshot> = emptyMap()

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun isDownloaded(songId: Long): Boolean {
        return songId in cached
    }

    fun refresh(): Set<Long> = refreshAndGet()

    fun refreshAndGet(): Set<Long> {
        val snapshot = refreshSnapshots()
        return snapshot.keys
    }

    fun snapshot(): Map<Long, DownloadedSongSnapshot> = cached

    fun refreshSnapshots(): Map<Long, DownloadedSongSnapshot> {
        val snapshot = replaceWith(scanDownloadedSongs())
        bumpVersion()
        return snapshot
    }

    fun upsert(snapshot: DownloadedSongSnapshot) {
        cached = cached.toMutableMap().apply { put(snapshot.id, snapshot) }
        bumpVersion()
    }

    fun remove(songId: Long) {
        if (songId !in cached) return
        cached = cached.toMutableMap().apply { remove(songId) }
        bumpVersion()
    }

    private fun replaceWith(snapshots: List<DownloadedSongSnapshot>): Map<Long, DownloadedSongSnapshot> {
        val result = linkedMapOf<Long, DownloadedSongSnapshot>()
        snapshots.forEach { snapshot ->
            if (snapshot.id !in result) {
                result[snapshot.id] = snapshot
            }
        }
        cached = result
        return result
    }

    private fun bumpVersion() {
        _version.update { it + 1 }
    }
}
