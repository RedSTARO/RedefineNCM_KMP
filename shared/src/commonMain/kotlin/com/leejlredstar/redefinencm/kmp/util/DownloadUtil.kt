package com.leejlredstar.redefinencm.kmp.util

data class DownloadedSongSnapshot(
    val id: Long,
    val fileName: String,
    val uri: String? = null,
    val sizeBytes: Long? = null,
    val lastModifiedEpochMillis: Long? = null,
)

/**
 * Result of a platform download-folder scan.
 *
 * An empty [DownloadScanResult.Success] means the folder was read successfully and contains no
 * matching files. [DownloadScanResult.Failure] means the state of the folder is unknown; callers
 * must retain their previous snapshot and must not infer that previously downloaded files were
 * deleted.
 */
sealed interface DownloadScanResult {
    data class Success(
        val snapshots: List<DownloadedSongSnapshot>,
    ) : DownloadScanResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : DownloadScanResult
}

/** Scans the RedefineNCM download folder without collapsing failures into an empty library. */
expect fun scanDownloadedSongs(): DownloadScanResult

/** Deletes local audio files for [songId] from the RedefineNCM download folder. */
expect fun deleteDownloadedSongFile(songId: Long): Boolean
