@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL

/** iOS actual：写入应用 Documents/RedefineNCM/，供本地库扫描与离线播放复用。 */
actual object SongDownloader {
    actual fun discardPartial(songId: Long) = Unit

    actual suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        onReadyToPublish: () -> Boolean,
    ): DownloadedSongFile {
        require(item.url.isNotBlank()) { "下载地址为空" }

        val existingSnapshots = when (val scan = scanDownloadedSongs()) {
            is DownloadScanResult.Success -> scan.snapshots
            is DownloadScanResult.Failure -> throw scan.cause
                ?: IllegalStateException(scan.message)
        }
        existingSnapshots.firstOrNull { it.id == item.id }?.let { existing ->
            if (!onReadyToPublish()) throw CancellationException("下载发布已取消")
            return DownloadedSongFile(fileName = existing.fileName, uri = existing.uri)
        }

        val url = NSURL.URLWithString(item.url) ?: error("下载地址无效")
        val extension = extensionFromUrl(item.url)
        val fileName = "${item.id}.$extension"
        onProgress(0L, item.expectedBytes)
        val downloadedFile = IosBackgroundDownloadCoordinator.download(url, fileName, onProgress)
        if (!onReadyToPublish()) {
            withContext(NonCancellable) {
                check(deleteDownloadedSongFile(item.id)) { "无法回滚已取消的 iOS 下载" }
            }
            throw CancellationException("下载发布已取消")
        }
        return downloadedFile
    }
}
