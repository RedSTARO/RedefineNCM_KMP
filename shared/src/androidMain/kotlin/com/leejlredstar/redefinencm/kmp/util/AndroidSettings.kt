package com.leejlredstar.redefinencm.kmp.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "redefinencm_settings")

/**
 * DataStore-backed settings with an in-memory cache.
 *
 * The synchronous getters ([getString]/[getBoolean]/[getLong]) are called from Compose
 * composition on the main thread; a naive `runBlocking { dataStore.data.first() }` there does
 * disk I/O on the main thread on every read (DataStore's own docs warn against this → jank/ANR).
 * The cache means only the very first read of each key touches disk; every subsequent read (and
 * every read after a write) is an in-memory hit. Writes update the cache synchronously so a
 * following read observes the new value immediately, then persist to DataStore off the caller.
 */
actual class PlatformSettings(private val context: Context) {

    private val cache = ConcurrentHashMap<String, Any>()
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    actual fun getString(key: String, default: String): String {
        (cache[key] as? String)?.let { return it }
        return runBlocking { getStringAsync(key, default) }
    }

    actual suspend fun getStringAsync(key: String, default: String): String {
        (cache[key] as? String)?.let { return it }
        val value = context.dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(key)] ?: default
        }.first()
        cache[key] = value
        return value
    }

    actual fun setString(key: String, value: String) {
        cache[key] = value
        persist {
            it[stringPreferencesKey(key)] = value
        }
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        (cache[key] as? Boolean)?.let { return it }
        return runBlocking { getBooleanAsync(key, default) }
    }

    actual suspend fun getBooleanAsync(key: String, default: Boolean): Boolean {
        (cache[key] as? Boolean)?.let { return it }
        val value = context.dataStore.data.map { prefs ->
            prefs[booleanPreferencesKey(key)] ?: default
        }.first()
        cache[key] = value
        return value
    }

    actual fun setBoolean(key: String, value: Boolean) {
        cache[key] = value
        persist {
            it[booleanPreferencesKey(key)] = value
        }
    }

    actual fun getLong(key: String, default: Long): Long {
        (cache[key] as? Long)?.let { return it }
        return runBlocking { getLongAsync(key, default) }
    }

    actual suspend fun getLongAsync(key: String, default: Long): Long {
        (cache[key] as? Long)?.let { return it }
        val value = context.dataStore.data.map { prefs ->
            prefs[longPreferencesKey(key)] ?: default
        }.first()
        cache[key] = value
        return value
    }

    actual fun setLong(key: String, value: Long) {
        cache[key] = value
        persist {
            it[longPreferencesKey(key)] = value
        }
    }

    private fun persist(block: (MutablePreferences) -> Unit) {
        writeScope.launch {
            writeMutex.withLock {
                context.dataStore.edit { prefs -> block(prefs) }
            }
        }
    }
}
