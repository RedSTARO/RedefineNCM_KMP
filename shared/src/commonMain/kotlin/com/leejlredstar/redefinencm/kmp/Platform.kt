package com.leejlredstar.redefinencm.kmp

interface Platform {
    val name: String
    val isDesktop: Boolean get() = false
    val isMobile: Boolean get() = false
    val isAndroid: Boolean get() = false

    /**
     * Whether a request may carry a `Cookie` header the app sets. Browsers forbid it from `fetch`,
     * which is why Web sends the NetEase cookie as a query parameter and cannot sign in to a
     * backend that reads the header only.
     */
    val canSendCookieHeader: Boolean get() = true

    /**
     * Whether the desktop offers a tray (notification-area) icon. GNOME without an AppIndicator
     * extension has none: closing to the tray there would leave the music playing behind no
     * window, no icon and no way to quit.
     */
    val hasSystemTray: Boolean get() = false
}

expect fun getPlatform(): Platform
