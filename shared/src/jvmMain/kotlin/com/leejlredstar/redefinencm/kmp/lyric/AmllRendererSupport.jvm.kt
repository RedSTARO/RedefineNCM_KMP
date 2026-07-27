package com.leejlredstar.redefinencm.kmp.lyric

actual val supportsLegacyAmllWebView: Boolean = run {
    val osName = System.getProperty("os.name").lowercase()
    val architecture = System.getProperty("os.arch").lowercase()
    val isX64 = architecture == "amd64" || architecture == "x86_64"
    osName.contains("windows") && isX64
}
