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
expect suspend fun scanDownloadedSongs(): DownloadScanResult

/** Deletes local audio files for [songId] from the RedefineNCM download folder. */
expect suspend fun deleteDownloadedSongFile(songId: Long): Boolean

/**
 * Where a platform asks the user to confirm deleting downloads it holds for another install of
 * the app; [onConfirmed] runs for each song the user let go. Placed on the Downloads page.
 */
@androidx.compose.runtime.Composable
expect fun MediaDeletionConsentHost(onConfirmed: (songId: Long) -> Unit)

/**
 * Whether downloads are kept where the user cannot open them (the browser's private storage),
 * so the download page offers [exportDownloadedSong] to save a copy out.
 */
expect val downloadsNeedExport: Boolean

/**
 * Saves a copy of the downloaded song stored as [fileName] where the user can open it, named
 * [displayName] plus the file's extension. Only called where [downloadsNeedExport] is true.
 */
expect suspend fun exportDownloadedSong(fileName: String, displayName: String)
