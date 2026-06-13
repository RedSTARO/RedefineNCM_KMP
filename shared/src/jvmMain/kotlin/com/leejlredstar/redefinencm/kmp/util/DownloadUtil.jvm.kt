package com.leejlredstar.redefinencm.kmp.util

import java.io.File

actual fun isSongDownloaded(songId: Long): Boolean {
    val dir = File(System.getProperty("user.home"), "Downloads/RedefineNCM")
    if (!dir.exists() || !dir.isDirectory) return false
    return dir.listFiles()?.any { it.nameWithoutExtension == songId.toString() } ?: false
}

actual fun scanDownloadedSongIds(): Set<Long> {
    val dir = File(System.getProperty("user.home"), "Downloads/RedefineNCM")
    if (!dir.exists() || !dir.isDirectory) return emptySet()
    return dir.listFiles()?.mapNotNull { it.nameWithoutExtension.toLongOrNull() }?.toSet() ?: emptySet()
}
