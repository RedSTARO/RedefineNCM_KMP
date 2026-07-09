package com.leejlredstar.redefinencm.kmp.util

import java.io.File

actual fun isSongDownloaded(songId: Long): Boolean {
    return DownloadedSongsCache.isDownloaded(songId)
}

actual fun scanDownloadedSongs(): List<DownloadedSongSnapshot> {
    val dir = jvmDownloadDirectory()
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()
        ?.asSequence()
        ?.filter(File::isFile)
        ?.mapNotNull { file ->
            val songId = file.nameWithoutExtension.toLongOrNull() ?: return@mapNotNull null
            DownloadedSongSnapshot(
                id = songId,
                fileName = file.name,
                uri = file.toURI().toString(),
                sizeBytes = file.length().takeIf { it > 0L },
                lastModifiedEpochMillis = file.lastModified().takeIf { it > 0L },
            )
        }
        ?.sortedWith(
            compareByDescending<DownloadedSongSnapshot> { it.lastModifiedEpochMillis ?: 0L }
                .thenBy { it.id }
        )
        ?.toList()
        ?: emptyList()
}

actual fun scanDownloadedSongIds(): Set<Long> =
    scanDownloadedSongs().mapTo(linkedSetOf()) { it.id }

actual fun deleteDownloadedSongFile(songId: Long): Boolean {
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
