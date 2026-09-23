@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings
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
        require(item.url.isNotBlank()) { strings.downloadUrlEmpty }

        val existingSnapshots = when (val scan = scanDownloadedSongs()) {
            is DownloadScanResult.Success -> scan.snapshots
            is DownloadScanResult.Failure -> throw scan.cause
                ?: IllegalStateException(scan.message)
        }
        existingSnapshots.firstOrNull { it.id == item.id }?.let { existing ->
            if (!onReadyToPublish()) throw CancellationException(strings.downloadCanceledNotSaved)
            return DownloadedSongFile(fileName = existing.fileName, uri = existing.uri)
        }

        val url = NSURL.URLWithString(item.url) ?: error(strings.downloadUrlInvalid)
        val extension = extensionFromUrl(item.url)
        val fileName = "${item.id}.$extension"
        onProgress(0L, item.expectedBytes)
        val downloadedFile = IosBackgroundDownloadCoordinator.download(url, fileName, onProgress)
        if (!onReadyToPublish()) {
            withContext(NonCancellable) {
                check(deleteDownloadedSongFile(item.id)) { strings.downloadCanceledDeleteFailed }
            }
            throw CancellationException(strings.downloadCanceledNotSaved)
        }
        return downloadedFile
    }
}
