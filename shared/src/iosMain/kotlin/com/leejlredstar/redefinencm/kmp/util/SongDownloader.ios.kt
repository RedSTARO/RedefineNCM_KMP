package com.leejlredstar.redefinencm.kmp.util

/** iOS 占位实现：迁移期不支持下载（后续可用 NSURLSessionDownloadTask）。 */
actual object SongDownloader {
    actual suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): DownloadedSongFile {
        throw UnsupportedOperationException("当前平台暂不支持下载")
    }
}
