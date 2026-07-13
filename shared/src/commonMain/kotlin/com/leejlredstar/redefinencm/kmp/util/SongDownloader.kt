package com.leejlredstar.redefinencm.kmp.util

/** 一条待写入本地文件的歌曲。队列调度、URL 解析和状态展示由 common 层负责。 */
data class DownloadRequestItem(
    val id: Long,
    /** Stable, normalized requested-quality key used only for resumable partial identity. */
    val resumeKey: String,
    /** Actual server representation (for example `standard` after a quality fallback). */
    val representationKey: String,
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

internal fun extensionFromUrl(url: String): String =
    url.substringBefore('?')
        .substringAfterLast('/')
        .substringAfterLast('.', "mp3")
        .lowercase()
        .filter { it in 'a'..'z' || it in '0'..'9' }
        .take(12)
        .ifBlank { "mp3" }

/**
 * 平台文件写入器。不要在 actual 中再调用 Android 系统 DownloadManager。
 *
 * actual 只做一件事：把 [item.url] 流式写到平台下载目录，并通过 [onProgress] 上报字节数。
 */
expect object SongDownloader {
    /** Discards unpublished platform-private bytes for [songId] without deleting a published song. */
    fun discardPartial(songId: Long)

    suspend fun download(
        item: DownloadRequestItem,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
        /** Atomically claims the task's publish phase; false leaves the completed partial unpublished. */
        onReadyToPublish: () -> Boolean,
    ): DownloadedSongFile
}
