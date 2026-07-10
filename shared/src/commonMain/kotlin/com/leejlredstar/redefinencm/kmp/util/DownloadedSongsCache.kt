package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory cache of downloaded songs to avoid repeated file-system scans.
 *
 * Per-row platform file checks used to scan the download folder repeatedly, causing UI jank when
 * many rows requested their status during composition. This singleton
 * keeps a process-local snapshot map for O(1) lookups. Full directory scans must be
 * triggered by explicit sync points; UI reads never perform disk I/O.
 */
object DownloadedSongsCache {
    private data class CacheState(
        val snapshots: Map<Long, DownloadedSongSnapshot> = emptyMap(),
        val revision: Long = 0L,
    )

    // MutableStateFlow.update performs an atomic read-modify-write. A volatile Map alone did not:
    // concurrent scan/upsert/remove operations could overwrite one another with stale copies.
    private val cacheState = MutableStateFlow(CacheState())

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    fun isDownloaded(songId: Long): Boolean {
        return songId in cacheState.value.snapshots
    }

    fun snapshot(): Map<Long, DownloadedSongSnapshot> = cacheState.value.snapshots

    fun refreshSnapshots(): DownloadScanResult {
        val revisionAtStart = cacheState.value.revision
        return when (val scanResult = scanDownloadedSongs()) {
            is DownloadScanResult.Failure -> scanResult
            is DownloadScanResult.Success -> {
                val scanned = scanResult.snapshots.toSnapshotMap()
                var applied = cacheState.value.snapshots
                var changed = false
                cacheState.update { current ->
                    // An in-app download/delete completed after this scan started. Keeping that
                    // newer state is safer than overwriting it with the older scan result.
                    if (current.revision != revisionAtStart) {
                        applied = current.snapshots
                        current
                    } else {
                        applied = scanned
                        changed = current.snapshots != scanned
                        current.copy(
                            snapshots = scanned,
                            revision = current.revision + 1,
                        )
                    }
                }
                if (changed) bumpVersion()
                DownloadScanResult.Success(applied.values.toList())
            }
        }
    }

    fun upsert(snapshot: DownloadedSongSnapshot) {
        var changed = false
        cacheState.update { current ->
            if (current.snapshots[snapshot.id] == snapshot) {
                current
            } else {
                changed = true
                current.copy(
                    snapshots = current.snapshots + (snapshot.id to snapshot),
                    revision = current.revision + 1,
                )
            }
        }
        if (!changed) return
        bumpVersion()
    }

    fun remove(songId: Long) {
        var changed = false
        cacheState.update { current ->
            if (songId !in current.snapshots) {
                current
            } else {
                changed = true
                current.copy(
                    snapshots = current.snapshots - songId,
                    revision = current.revision + 1,
                )
            }
        }
        if (!changed) return
        bumpVersion()
    }

    private fun List<DownloadedSongSnapshot>.toSnapshotMap(): Map<Long, DownloadedSongSnapshot> {
        val result = linkedMapOf<Long, DownloadedSongSnapshot>()
        forEach { snapshot ->
            if (snapshot.id !in result) {
                result[snapshot.id] = snapshot
            }
        }
        return result
    }

    private fun bumpVersion() {
        _version.update { it + 1 }
    }
}
