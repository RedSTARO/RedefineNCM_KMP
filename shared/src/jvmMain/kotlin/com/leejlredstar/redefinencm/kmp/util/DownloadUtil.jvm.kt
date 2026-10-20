package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings
import java.io.File

actual suspend fun scanDownloadedSongs(): DownloadScanResult = runCatching {
    scanJvmDownloadedSongs(jvmDownloadDirectory())
}.fold(
    onSuccess = DownloadScanResult::Success,
    onFailure = { error -> DownloadScanResult.Failure(strings.desktopDownloadFolderScanFailed, error) },
)

/**
 * The downloaded songs in [dir]. An empty file is not a song: one left by a crash or a full disk
 * would otherwise count as downloaded, play as an error, and never be fetched again, because a
 * download skips a song that is already there.
 */
internal fun scanJvmDownloadedSongs(dir: File): List<DownloadedSongSnapshot> {
    if (!dir.exists()) return emptyList()
    check(dir.isDirectory) { strings.downloadFolderNotFolder(dir) }
    val files = dir.listFiles() ?: error(strings.downloadFolderUnreadable(dir))
    return files.asSequence()
        .filter { it.isFile && it.length() > 0L }
        .mapNotNull { file ->
            val songId = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
            DownloadedSongSnapshot(
                id = songId,
                fileName = file.name,
                uri = file.toURI().toString(),
                sizeBytes = file.length().takeIf { it > 0L },
                lastModifiedEpochMillis = file.lastModified().takeIf { it > 0L },
            )
        }
        .sortedWith(
            compareByDescending<DownloadedSongSnapshot> { it.lastModifiedEpochMillis ?: 0L }
                .thenBy { it.id }
        )
        .toList()
}

actual suspend fun deleteDownloadedSongFile(songId: Long): Boolean {
    val dir = jvmDownloadDirectory()
    if (!dir.exists() || !dir.isDirectory) return false
    var deleted = false
    dir.listFiles()
        ?.asSequence()
        ?.filter(File::isFile)
        ?.filter { file -> file.name.startsWith("$songId.") }
        ?.forEach { file -> deleted = file.delete() || deleted }
    return deleted
}

// Downloads already land in a folder the user can open.
actual val downloadsNeedExport: Boolean = false

actual suspend fun exportDownloadedSong(fileName: String, displayName: String) = Unit
