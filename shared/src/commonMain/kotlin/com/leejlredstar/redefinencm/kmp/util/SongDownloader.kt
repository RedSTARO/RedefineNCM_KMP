package com.leejlredstar.redefinencm.kmp.util

/** 一条待写入本地文件的歌曲。队列调度、URL 解析和状态展示由 common 层负责。 */
data class DownloadRequestItem(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: String,
    val url: String,
    val expectedBytes: Long? = null,
)

data class DownloadedSongFile(
    val fileName: String,
    val uri: String?,
)

/**
 * 平台文件写入器。不要在 actual 中再调用 Android 系统 DownloadManager。
 *
 * actual 只做一件事：把 [item.url] 流式写到平台下载目录，并通过 [onProgress] 上报字节数。
 */
expect object SongDownloader {
    suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): DownloadedSongFile
}
