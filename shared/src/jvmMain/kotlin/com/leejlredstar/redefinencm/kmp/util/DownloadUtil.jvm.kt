package com.leejlredstar.redefinencm.kmp.util

import java.io.File

actual fun scanDownloadedSongs(): DownloadScanResult = runCatching {
    val dir = jvmDownloadDirectory()
    if (!dir.exists()) return@runCatching emptyList()
    check(dir.isDirectory) { "下载路径不是目录：$dir" }
    val files = dir.listFiles() ?: error("无法读取下载目录：$dir")
    files.asSequence()
        .filter(File::isFile)
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
}.fold(
    onSuccess = DownloadScanResult::Success,
    onFailure = { error -> DownloadScanResult.Failure("无法读取桌面下载目录", error) },
)

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
