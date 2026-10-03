package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings

actual object SongDownloader {
    actual fun discardPartial(songId: Long) = Unit

    actual suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onReadyToPublish: () -> Boolean,
    ): DownloadedSongFile {
        require(item.url.isNotBlank()) { strings.downloadUrlEmpty }
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
            onReadyToPublish = onReadyToPublish,
        )
    }
}
