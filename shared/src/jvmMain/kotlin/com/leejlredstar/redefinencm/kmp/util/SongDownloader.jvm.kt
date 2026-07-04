package com.leejlredstar.redefinencm.kmp.util

/** JVM/Desktop 占位实现：迁移期不支持下载（后续可用 Ktor 下载到用户目录）。 */
actual object SongDownloader {
    actual fun enqueue(items: List<DownloadRequestItem>) {
        // TODO(desktop): download via Ktor into a user-visible folder
    }
}
