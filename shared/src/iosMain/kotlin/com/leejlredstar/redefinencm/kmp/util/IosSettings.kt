package com.leejlredstar.redefinencm.kmp.util

import platform.Foundation.NSUserDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PlatformSettings {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String, default: String): String {
        return defaults.stringForKey(key) ?: default
    }

    actual suspend fun getStringAsync(key: String, default: String): String {
        return withContext(Dispatchers.Main) {
            defaults.stringForKey(key) ?: default
        }
    }

    actual fun setString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            default
        }
    }

    actual suspend fun getBooleanAsync(key: String, default: Boolean): Boolean {
        return withContext(Dispatchers.Main) { getBoolean(key, default) }
    }

    actual fun setBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getLong(key: String, default: Long): Long {
        return if (defaults.objectForKey(key) != null) {
            defaults.stringForKey(key)?.toLongOrNull() ?: default
        } else {
            default
        }
    }

    actual suspend fun getLongAsync(key: String, default: Long): Long {
        return withContext(Dispatchers.Main) { getLong(key, default) }
    }

    actual fun setLong(key: String, value: Long) {
        defaults.setObject(value.toString(), forKey = key)
        defaults.synchronize()
    }
}
