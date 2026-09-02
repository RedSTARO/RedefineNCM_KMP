package com.leejlredstar.redefinencm.kmp.util

import kotlinx.browser.localStorage

/**
 * [keyPrefix] exists so tests can hold a throwaway corner of localStorage. Production always
 * uses the empty prefix: prefixing would orphan every value an installed copy has written.
 */
actual class PlatformSettings(private val keyPrefix: String = "") {

    private fun storageKey(key: String): String = keyPrefix + key

    actual suspend fun awaitLoaded() = Unit

    actual suspend fun flush() = Unit

    actual fun getString(key: String, default: String): String {
        return localStorage.getItem(storageKey(key)) ?: default
    }

    actual fun setString(key: String, value: String) {
        localStorage.setItem(storageKey(key), value)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        val value = localStorage.getItem(storageKey(key)) ?: return default
        // setBoolean only ever writes "true"/"false"; anything else is a value this app did not
        // write, and the caller's default is a better answer than reporting it as false.
        return value.toBooleanStrictOrNull() ?: default
    }

    actual fun setBoolean(key: String, value: Boolean) {
        localStorage.setItem(storageKey(key), value.toString())
    }

    actual fun getLong(key: String, default: Long): Long {
        val value = localStorage.getItem(storageKey(key)) ?: return default
        return value.toLongOrNull() ?: default
    }

    actual fun setLong(key: String, value: Long) {
        localStorage.setItem(storageKey(key), value.toString())
    }
}
