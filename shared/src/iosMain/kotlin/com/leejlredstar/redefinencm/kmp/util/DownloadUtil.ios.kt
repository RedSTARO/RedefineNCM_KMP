@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import platform.Foundation.NSFileManager
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

private const val DOWNLOAD_SUBDIR = "RedefineNCM"

actual fun isSongDownloaded(songId: Long): Boolean =
    DownloadedSongsCache.isDownloaded(songId)

actual fun scanDownloadedSongs(): List<DownloadedSongSnapshot> {
    val dir = iosDownloadDirectoryPath()
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(dir)) return emptyList()
    return (manager.contentsOfDirectoryAtPath(dir, error = null).orEmpty())
        .asSequence()
        .mapNotNull { entry -> (entry as? String)?.toDownloadedSongSnapshot(dir) }
        .sortedWith(
            compareByDescending<DownloadedSongSnapshot> { it.lastModifiedEpochMillis ?: 0L }
                .thenBy { it.id }
        )
        .toList()
}

actual fun scanDownloadedSongIds(): Set<Long> =
    scanDownloadedSongs().mapTo(linkedSetOf()) { it.id }

actual fun deleteDownloadedSongFile(songId: Long): Boolean {
    val dir = iosDownloadDirectoryPath()
    val manager = NSFileManager.defaultManager
    val entries = manager.contentsOfDirectoryAtPath(dir, error = null).orEmpty()
    var deleted = false
    entries.forEach { entry ->
        val fileName = entry as? String ?: return@forEach
        if (!fileName.startsWith("$songId.")) return@forEach
        deleted = manager.removeItemAtPath("$dir/$fileName", error = null) || deleted
    }
    return deleted
}

internal fun iosDownloadDirectoryPath(): String {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: error("Documents directory unavailable")
    return "$documents/$DOWNLOAD_SUBDIR"
}

internal fun ensureIosDownloadDirectory(): String {
    val dir = iosDownloadDirectoryPath()
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(dir)) {
        val created = manager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        if (!created) error("无法创建下载目录")
    }
    return dir
}

private fun String.toDownloadedSongSnapshot(dir: String): DownloadedSongSnapshot? {
    val songId = substringBeforeLast('.', this).toLongOrNull() ?: return null
    val path = "$dir/$this"
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(path)) return null
    return DownloadedSongSnapshot(
        id = songId,
        fileName = this,
        uri = NSURL.fileURLWithPath(path).absoluteString,
        sizeBytes = null,
        lastModifiedEpochMillis = null,
    )
}
