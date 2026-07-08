package com.leejlredstar.redefinencm.kmp

interface Platform {
    val name: String
    val isDesktop: Boolean get() = false
    val isMobile: Boolean get() = false
}

expect fun getPlatform(): Platform
