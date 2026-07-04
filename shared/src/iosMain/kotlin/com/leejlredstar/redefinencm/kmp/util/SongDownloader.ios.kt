package com.leejlredstar.redefinencm.kmp.util

/** iOS 占位实现：迁移期不支持下载（后续可用 NSURLSessionDownloadTask）。 */
actual object SongDownloader {
    actual fun enqueue(items: List<DownloadRequestItem>) {
        // TODO(ios): NSURLSessionDownloadTask into the app documents folder
    }
}
