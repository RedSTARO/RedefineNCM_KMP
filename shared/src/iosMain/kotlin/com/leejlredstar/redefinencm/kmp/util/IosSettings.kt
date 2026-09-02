package com.leejlredstar.redefinencm.kmp.util

import platform.Foundation.NSUserDefaults

/**
 * [suiteName] exists so tests can hold a throwaway defaults suite. Production always uses the
 * standard defaults: a suite change would orphan every value an installed copy has written.
 */
actual class PlatformSettings(suiteName: String? = null) {
    private val defaults = suiteName
        ?.let { NSUserDefaults(suiteName = it) }
        ?: NSUserDefaults.standardUserDefaults

    actual suspend fun awaitLoaded() = Unit

    actual suspend fun flush() = Unit

    actual fun getString(key: String, default: String): String {
        return defaults.stringForKey(key) ?: default
    }

    actual fun setString(key: String, value: String) {
        persist(key) { defaults.setObject(value, forKey = key) }
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            default
        }
    }

    actual fun setBoolean(key: String, value: Boolean) {
        persist(key) { defaults.setBool(value, forKey = key) }
    }

    actual fun getLong(key: String, default: Long): Long {
        return if (defaults.objectForKey(key) != null) {
            defaults.stringForKey(key)?.toLongOrNull() ?: default
        } else {
            default
        }
    }

    actual fun setLong(key: String, value: Long) {
        persist(key) { defaults.setObject(value.toString(), forKey = key) }
    }

    private fun persist(key: String, write: () -> Unit) {
        val previous = defaults.objectForKey(key)
        write()
        if (defaults.synchronize()) return
        if (previous == null) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(previous, forKey = key)
        }
        defaults.synchronize()
        error("Failed to persist iOS setting: $key")
    }
}
