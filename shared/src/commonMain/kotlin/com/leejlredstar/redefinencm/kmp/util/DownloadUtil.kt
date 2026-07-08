package com.leejlredstar.redefinencm.kmp.util

data class DownloadedSongSnapshot(
    val id: Long,
    val fileName: String,
    val uri: String? = null,
    val sizeBytes: Long? = null,
    val lastModifiedEpochMillis: Long? = null,
)

/** Returns true if a local audio file for [songId] exists in the RedefineNCM download folder. */
expect fun isSongDownloaded(songId: Long): Boolean

/** Scans the RedefineNCM download folder and returns structured local-library entries. */
expect fun scanDownloadedSongs(): List<DownloadedSongSnapshot>

/** Platform-specific: scan the download directory and return all downloaded song IDs. */
expect fun scanDownloadedSongIds(): Set<Long>

/** Deletes local audio files for [songId] from the RedefineNCM download folder. */
expect fun deleteDownloadedSongFile(songId: Long): Boolean
