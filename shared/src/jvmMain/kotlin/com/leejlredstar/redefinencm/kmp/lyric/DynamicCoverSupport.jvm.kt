package com.leejlredstar.redefinencm.kmp.lyric

actual val supportsDynamicNowPlayingCover: Boolean =
    run {
        val osName = System.getProperty("os.name").lowercase()
        val architecture = System.getProperty("os.arch").lowercase()
        val isX64 = architecture == "amd64" || architecture == "x86_64"
        val isArm64 = architecture == "aarch64" || architecture == "arm64"
        (osName.contains("windows") && isX64) ||
            (osName.contains("linux") && (isX64 || isArm64)) ||
            ((osName.contains("mac") || osName.contains("darwin")) && (isX64 || isArm64))
    }
