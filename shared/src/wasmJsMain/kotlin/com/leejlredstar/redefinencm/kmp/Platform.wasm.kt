package com.leejlredstar.redefinencm.kmp

actual fun getPlatform(): Platform = object : Platform {
    override val name = "Web Browser"
    override val isDesktop = false
    override val isMobile = false
}
