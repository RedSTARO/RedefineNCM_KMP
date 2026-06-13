package com.leejlredstar.redefinencm.kmp.util

import android.os.Environment

actual fun isSongDownloaded(songId: Long): Boolean {
    val dir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS + "/RedefineNCM"
    )
    if (!dir.exists() || !dir.isDirectory) return false
    return dir.listFiles()?.any { it.nameWithoutExtension == songId.toString() } ?: false
}
