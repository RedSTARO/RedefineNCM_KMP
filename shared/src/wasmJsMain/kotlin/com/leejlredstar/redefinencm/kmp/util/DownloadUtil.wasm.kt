package com.leejlredstar.redefinencm.kmp.util

actual suspend fun scanDownloadedSongs(): DownloadScanResult = WebDownloadStorage.scan()

actual suspend fun deleteDownloadedSongFile(songId: Long): Boolean =
    WebDownloadStorage.delete(songId)

// The browser keeps downloads in the site's private storage (OPFS); they never reach the
// system Downloads folder unless the user saves one out.
actual val downloadsNeedExport: Boolean = true

actual suspend fun exportDownloadedSong(fileName: String, displayName: String) {
    val extension = fileName.substringAfterLast('.', "")
    val safeName = displayName
        .map { if (it in "\\/:*?\"<>|" || it.code < 0x20) '_' else it }
        .joinToString("")
        .trim()
        .take(120)
        .ifBlank { fileName.substringBeforeLast('.') }
    WebDownloadStorage.export(
        fileName = fileName,
        downloadName = if (extension.isEmpty()) safeName else "$safeName.$extension",
    )
}
