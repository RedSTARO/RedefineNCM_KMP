package com.leejlredstar.redefinencm.kmp.util

import kotlinx.browser.localStorage

actual class PlatformSettings {

    actual suspend fun awaitLoaded() = Unit

    actual suspend fun flush() = Unit

    actual fun getString(key: String, default: String): String {
        return localStorage.getItem(key) ?: default
    }

    actual fun setString(key: String, value: String) {
        localStorage.setItem(key, value)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        val value = localStorage.getItem(key) ?: return default
        return value == "true"
    }

    actual fun setBoolean(key: String, value: Boolean) {
        localStorage.setItem(key, value.toString())
    }

    actual fun getLong(key: String, default: Long): Long {
        val value = localStorage.getItem(key) ?: return default
        return value.toLongOrNull() ?: default
    }

    actual fun setLong(key: String, value: Long) {
        localStorage.setItem(key, value.toString())
    }
}
