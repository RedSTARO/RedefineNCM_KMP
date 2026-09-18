package com.leejlredstar.redefinencm.kmp

/** What the desktop app shell needs to know about the host, read from [DesktopOs]. */
object DesktopHost {
    /** macOS draws its own title bar and traffic lights; the app's Windows-style chrome is not for it. */
    val usesSystemWindowChrome: Boolean get() = DesktopOs.current == DesktopOs.MacOs
}
