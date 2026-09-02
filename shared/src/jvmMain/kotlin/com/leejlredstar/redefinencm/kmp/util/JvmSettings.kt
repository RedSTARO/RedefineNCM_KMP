package com.leejlredstar.redefinencm.kmp.util

import java.util.prefs.Preferences

internal const val DEFAULT_SETTINGS_NODE = "com.leejlredstar.redefinencm.kmp"

/**
 * [nodeName] exists so tests can hold a throwaway preference node. Production always uses the
 * default: changing it would orphan every value an installed copy has already written.
 */
actual class PlatformSettings(nodeName: String = DEFAULT_SETTINGS_NODE) {
    private val prefs = Preferences.userRoot().node(nodeName)

    actual suspend fun awaitLoaded() = Unit

    actual suspend fun flush() = Unit

    actual fun getString(key: String, default: String): String {
        return prefs.get(key, default)
    }

    actual fun setString(key: String, value: String) {
        persistValue(key, value)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    actual fun setBoolean(key: String, value: Boolean) {
        persistValue(key, value.toString())
    }

    actual fun getLong(key: String, default: Long): Long {
        return prefs.getLong(key, default)
    }

    actual fun setLong(key: String, value: Long) {
        persistValue(key, value.toString())
    }

    private fun persistValue(key: String, value: String) {
        val previous = prefs.get(key, null)
        try {
            prefs.put(key, value)
            prefs.flush()
        } catch (error: Exception) {
            if (previous == null) prefs.remove(key) else prefs.put(key, previous)
            runCatching { prefs.flush() }
            throw error
        }
    }
}
