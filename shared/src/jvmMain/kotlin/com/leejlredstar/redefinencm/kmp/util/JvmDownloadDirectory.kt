package com.leejlredstar.redefinencm.kmp.util

import java.io.File

internal fun jvmDownloadDirectory(): File {
    val home = System.getProperty("user.home")
    check(!home.isNullOrBlank()) { "user.home is not set" }
    return File(File(home, "Music"), "RedefineNCM")
}
