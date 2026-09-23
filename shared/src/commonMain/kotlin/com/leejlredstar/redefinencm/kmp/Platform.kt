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
}

expect fun getPlatform(): Platform
