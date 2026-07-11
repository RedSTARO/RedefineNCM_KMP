package com.leejlredstar.redefinencm.kmp.util

actual object SongDownloader {
    actual suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): DownloadedSongFile {
        require(item.url.isNotBlank()) { "下载地址为空" }
        val extension = item.url.substringBefore('?')
            .substringAfterLast('/')
            .substringAfterLast('.', "mp3")
            .lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' }
            .take(12)
            .ifBlank { "mp3" }
        return WebDownloadStorage.download(
            item = item,
            fileName = "${item.id}.$extension",
            onProgress = onProgress,
        )
    }
}
