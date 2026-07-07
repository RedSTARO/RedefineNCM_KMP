package com.leejlredstar.redefinencm.kmp.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.koin.mp.KoinPlatform

/**
 * Android actual：系统 DownloadManager，目标目录 Downloads/RedefineNCM/<id>.<ext>，
 * 与原版 DownloadWorker.enqueueDownload 相同（含完成通知、跳过已存在文件）。
 */
actual object SongDownloader {
    actual fun enqueue(items: List<DownloadRequestItem>) {
        if (items.isEmpty()) return
        val context = KoinPlatform.getKoin().get<Context>()
        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        for (item in items) {
            if (item.url.isEmpty()) continue
            val uri = Uri.parse(item.url)
            val ext = uri.lastPathSegment
                ?.substringAfterLast('.', "mp3")
                ?.takeIf { it.isNotBlank() } ?: "mp3"
            val fileName = "${item.id}.$ext"
            if (findDownloadedSongUri(item.id) != null) continue

            val request = DownloadManager.Request(uri).apply {
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "/RedefineNCM/$fileName",
                )
            }
            downloadManager.enqueue(request)
        }
    }
}
