package com.leejlredstar.redefinencm.kmp.util

actual fun isSongDownloaded(songId: Long): Boolean = false

actual fun scanDownloadedSongs(): List<DownloadedSongSnapshot> = emptyList()

actual fun scanDownloadedSongIds(): Set<Long> = emptySet()

actual fun deleteDownloadedSongFile(songId: Long): Boolean = false
