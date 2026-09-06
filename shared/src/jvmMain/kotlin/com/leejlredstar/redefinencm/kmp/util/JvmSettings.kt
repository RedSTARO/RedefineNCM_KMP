package com.leejlredstar.redefinencm.kmp.util

import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences

internal const val DEFAULT_SETTINGS_NODE = "com.leejlredstar.redefinencm.kmp"

/**
 * [nodeName] exists so tests can hold a throwaway preference node. Production always uses the
 * default: changing it would orphan every value an installed copy has already written.
 *
 * Reads are served from an in-memory copy after the first fetch. `java.util.prefs` on Windows
 * opens and queries a registry key on every `get`, converting the path and the value through
 * fresh byte arrays each time; the playback reporter samples the cookie ten times a second, and
 * that alone was a measurable share of the app's garbage while a lyric page animated. This is
 * the only writer of the node while the app runs, so the copy cannot go stale.
 */
actual class PlatformSettings(nodeName: String = DEFAULT_SETTINGS_NODE) {
    private val prefs = Preferences.userRoot().node(nodeName)
    private val cache = ConcurrentHashMap<String, Any>()

    actual suspend fun awaitLoaded() = Unit

    actual suspend fun flush() = Unit

    actual fun getString(key: String, default: String): String {
        val stored = cache[key] ?: run {
            val fetched: Any = prefs.get(key, null) ?: Absent
            cache.putIfAbsent(key, fetched) ?: fetched
        }
        return if (stored === Absent) default else stored as String
    }

    actual fun setString(key: String, value: String) {
        persistValue(key, value)
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        // The same reading `AbstractPreferences.getBoolean` applies to the stored string.
        return when {
            getString(key, "").equals("true", ignoreCase = true) -> true
            getString(key, "").equals("false", ignoreCase = true) -> false
            else -> default
        }
    }

    actual fun setBoolean(key: String, value: Boolean) {
        persistValue(key, value.toString())
    }

    actual fun getLong(key: String, default: Long): Long {
        return getString(key, "").toLongOrNull() ?: default
    }

    actual fun setLong(key: String, value: Long) {
        persistValue(key, value.toString())
    }

    private fun persistValue(key: String, value: String) {
        val previous = prefs.get(key, null)
        try {
            prefs.put(key, value)
            prefs.flush()
            cache[key] = value
        } catch (error: Exception) {
            if (previous == null) prefs.remove(key) else prefs.put(key, previous)
            runCatching { prefs.flush() }
            cache.remove(key)
            throw error
        }
    }

    /** Marks a key the store has no value for, so the default is not re-queried either. */
    private object Absent
}
