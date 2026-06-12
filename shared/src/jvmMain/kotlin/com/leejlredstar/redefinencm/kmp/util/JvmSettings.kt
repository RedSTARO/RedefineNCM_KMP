package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences

actual class PlatformSettings {
    private val prefs = Preferences.userRoot().node("com.leejlredstar.redefinencm.kmp")

    actual fun getString(key: String, default: String): String {
        return prefs.get(key, default)
    }

    actual suspend fun getStringAsync(key: String, default: String): String {
        return withContext(Dispatchers.IO) { prefs.get(key, default) }
    }

    actual fun setString(key: String, value: String) {
        prefs.put(key, value)
        prefs.flush()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    actual suspend fun getBooleanAsync(key: String, default: Boolean): Boolean {
        return withContext(Dispatchers.IO) { prefs.getBoolean(key, default) }
    }

    actual fun setBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
        prefs.flush()
    }

    actual fun getLong(key: String, default: Long): Long {
        return prefs.getLong(key, default)
    }

    actual suspend fun getLongAsync(key: String, default: Long): Long {
        return withContext(Dispatchers.IO) { prefs.getLong(key, default) }
    }

    actual fun setLong(key: String, value: Long) {
        prefs.putLong(key, value)
        prefs.flush()
    }
}
