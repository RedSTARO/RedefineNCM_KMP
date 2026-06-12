package com.leejlredstar.redefinencm.kmp.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "redefinencm_settings")

actual class PlatformSettings(private val context: Context) {

    actual fun getString(key: String, default: String): String {
        return runBlocking { getStringAsync(key, default) }
    }

    actual suspend fun getStringAsync(key: String, default: String): String {
        return context.dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(key)] ?: default
        }.first()
    }

    actual fun setString(key: String, value: String) {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey(key)] = value
            }
        }
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return runBlocking { getBooleanAsync(key, default) }
    }

    actual suspend fun getBooleanAsync(key: String, default: Boolean): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[booleanPreferencesKey(key)] ?: default
        }.first()
    }

    actual fun setBoolean(key: String, value: Boolean) {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs[booleanPreferencesKey(key)] = value
            }
        }
    }

    actual fun getLong(key: String, default: Long): Long {
        return runBlocking { getLongAsync(key, default) }
    }

    actual suspend fun getLongAsync(key: String, default: Long): Long {
        return context.dataStore.data.map { prefs ->
            prefs[longPreferencesKey(key)] ?: default
        }.first()
    }

    actual fun setLong(key: String, value: Long) {
        runBlocking {
            context.dataStore.edit { prefs ->
                prefs[longPreferencesKey(key)] = value
            }
        }
    }
}
