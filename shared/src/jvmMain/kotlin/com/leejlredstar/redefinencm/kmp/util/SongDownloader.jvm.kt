package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

/**
 * JVM/Desktop actual：后台协程直接流式下载到 `~/Downloads/RedefineNCM/<id>.<ext>`
 * （与 Android DownloadManager 的目标目录语义一致），跳过已存在文件，
 * 先写 `.part` 再原子改名避免半成品被离线扫描误认。
 */
actual object SongDownloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    actual fun enqueue(items: List<DownloadRequestItem>) {
        if (items.isEmpty()) return
        val dir = File(System.getProperty("user.home"), "Downloads/RedefineNCM")

        for (item in items) {
            if (item.url.isEmpty()) continue
            scope.launch {
                try {
                    if (!dir.exists()) dir.mkdirs()
                    val ext = item.url.substringBefore('?')
                        .substringAfterLast('/')
                        .substringAfterLast('.', "mp3")
                        .ifEmpty { "mp3" }
                    val target = File(dir, "${item.id}.$ext")
                    if (target.exists()) return@launch

                    val partFile = File(dir, "${item.id}.$ext.part")
                    URI(item.url).toURL().openStream().use { input ->
                        partFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!partFile.renameTo(target)) partFile.delete()
                    DownloadedSongsCache.refresh()
                } catch (e: Exception) {
                    // 单曲失败不影响批次其余下载
                }
            }
        }
    }
}
