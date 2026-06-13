package com.leejlredstar.redefinencm.kmp.util

/**
 * In-memory cache of downloaded song IDs to avoid repeated file-system scans.
 *
 * The Android [isSongDownloaded] actual used to call [File.listFiles] per song,
 * causing UI jank when many rows called it at composition time. This singleton
 * scans the download directory once into a [Set] for O(1) lookups.
 */
object DownloadedSongsCache {
    private var cached: Set<Long>? = null

    fun isDownloaded(songId: Long): Boolean {
        if (cached == null) refresh()
        return songId in cached!!
    }

    fun refresh() {
        cached = scanDownloadedSongIds()
    }
}

/** Platform-specific: scan the download directory and return all downloaded song IDs. */
expect fun scanDownloadedSongIds(): Set<Long>
