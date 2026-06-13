package com.leejlredstar.redefinencm.kmp.util

import android.os.Environment

/**
 * Android-specific: scan the RedefineNCM download folder and return all downloaded song IDs.
 * Called once by [DownloadedSongsCache] and cached for O(1) lookups.
 */
actual fun scanDownloadedSongIds(): Set<Long> {
    val dir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS + "/RedefineNCM"
    )
    if (!dir.exists() || !dir.isDirectory) return emptySet()
    return dir.listFiles()
        ?.mapNotNull { it.nameWithoutExtension.toLongOrNull() }
        ?.toSet() ?: emptySet()
}

/**
 * Legacy direct file-system check. Use [DownloadedSongsCache.isDownloaded] from UI code
 * to avoid repeated directory scans.
 */
actual fun isSongDownloaded(songId: Long): Boolean =
    DownloadedSongsCache.isDownloaded(songId)
