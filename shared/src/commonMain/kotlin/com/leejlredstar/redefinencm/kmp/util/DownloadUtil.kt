package com.leejlredstar.redefinencm.kmp.util

/** Returns true if a local audio file for [songId] exists in the RedefineNCM download folder. */
expect fun isSongDownloaded(songId: Long): Boolean
