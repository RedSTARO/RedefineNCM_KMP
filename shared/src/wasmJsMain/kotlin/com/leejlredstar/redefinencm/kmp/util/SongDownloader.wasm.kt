package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
        val downloadedFile = WebDownloadStorage.download(
            item = item,
            fileName = "${item.id}.$extension",
            onProgress = onProgress,
        )
        if (!onReadyToPublish()) {
            withContext(NonCancellable) {
                check(WebDownloadStorage.delete(item.id)) { strings.downloadCanceledDeleteFailed }
            }
            throw CancellationException(strings.downloadCanceledNotSaved)
        }
        return downloadedFile
    }
}
