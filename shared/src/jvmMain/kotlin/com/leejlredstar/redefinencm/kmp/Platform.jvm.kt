package com.leejlredstar.redefinencm.kmp

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val isDesktop: Boolean = true
    override val hasSystemTray: Boolean
        get() = runCatching { java.awt.SystemTray.isSupported() }.getOrDefault(false)
}

actual fun getPlatform(): Platform = JVMPlatform()
