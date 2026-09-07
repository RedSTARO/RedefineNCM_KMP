package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.DesktopArch
import com.leejlredstar.redefinencm.kmp.DesktopOs

/**
 * Whether the bundled FFmpeg natives exist for this host. JavaCPP ships no Windows arm64 build,
 * so Windows is x64 only; Linux and macOS have both.
 */
actual val supportsDynamicNowPlayingCover: Boolean =
    when (DesktopOs.current) {
        DesktopOs.Windows -> DesktopArch.current == DesktopArch.X64
        DesktopOs.Linux, DesktopOs.MacOs -> DesktopArch.current != DesktopArch.Other
        DesktopOs.Other -> false
    }
