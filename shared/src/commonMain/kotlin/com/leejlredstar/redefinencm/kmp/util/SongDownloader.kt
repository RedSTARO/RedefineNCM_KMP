package com.leejlredstar.redefinencm.kmp.util

/** 一条待下载歌曲：id 决定文件名（<id>.<扩展名>），url 为解析出的直链。 */
data class DownloadRequestItem(val id: Long, val url: String)

/**
 * 平台歌曲下载器（对应原版 DownloadWorker + 系统 DownloadManager）。
 *
 * - Android actual：系统 DownloadManager 下载到 Downloads/RedefineNCM/，完成后系统通知；
 * - 其他平台：迁移期占位实现（不下载）。
 */
expect object SongDownloader {
    /** 将 [items] 逐首加入下载队列，已存在的文件由调用方预先过滤。 */
    fun enqueue(items: List<DownloadRequestItem>)
}
