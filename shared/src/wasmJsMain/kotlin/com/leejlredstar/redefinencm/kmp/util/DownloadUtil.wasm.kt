package com.leejlredstar.redefinencm.kmp.util

actual suspend fun scanDownloadedSongs(): DownloadScanResult = WebDownloadStorage.scan()

actual suspend fun deleteDownloadedSongFile(songId: Long): Boolean =
    WebDownloadStorage.delete(songId)
