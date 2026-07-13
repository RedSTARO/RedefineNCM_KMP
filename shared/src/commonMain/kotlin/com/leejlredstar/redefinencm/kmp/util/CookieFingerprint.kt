package com.leejlredstar.redefinencm.kmp.util

internal fun cookieFingerprint(cookie: String): Long {
    var hash = -3750763034362895579L
    cookie.forEach { character ->
        hash = hash xor character.code.toLong()
        hash *= 1099511628211L
    }
    return hash
}
