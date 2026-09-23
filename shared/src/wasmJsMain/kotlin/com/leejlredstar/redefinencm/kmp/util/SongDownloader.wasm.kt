package com.leejlredstar.redefinencm.kmp.util

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
        require(item.url.isNotBlank()) { "下载地址为空" }
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
                check(WebDownloadStorage.delete(item.id)) { "下载已取消，但无法删除已下载的文件" }
            }
            throw CancellationException("下载已取消，文件未保存")
        }
        return downloadedFile
    }
}
