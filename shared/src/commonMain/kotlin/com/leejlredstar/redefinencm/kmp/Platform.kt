package com.leejlredstar.redefinencm.kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform