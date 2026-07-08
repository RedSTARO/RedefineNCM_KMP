package com.leejlredstar.redefinencm.kmp

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val isDesktop: Boolean = true
}

actual fun getPlatform(): Platform = JVMPlatform()
