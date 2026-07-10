package com.leejlredstar.redefinencm.kmp.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "redefinencm_settings")

private sealed interface SettingsWriteCommand {
    data class Update(
        val key: String,
        val revision: Long,
        val newValue: Any,
        val transform: (MutablePreferences) -> Unit,
    ) : SettingsWriteCommand

    data class Barrier(
        val completion: CompletableDeferred<Unit>,
    ) : SettingsWriteCommand
}

/**
 * DataStore-backed settings with an in-memory cache.
 *
 * The synchronous getters are deliberately cache-only: DataStore is asynchronous and must never
 * be bridged with `runBlocking` from Compose or Koin construction on the main thread. Callers that
 * require the persisted value before continuing use the suspend getters, which wait for the one
 * background snapshot load. Writes update the cache before returning and are serialized through
 * a single channel, so an older initial snapshot or an out-of-order coroutine cannot overwrite a
 * newer value.
 */
actual class PlatformSettings(private val context: Context) {

    private val cache = ConcurrentHashMap<String, Any>()
    private val cacheStateLock = Any()
    private val cacheStates = mutableMapOf<String, AndroidSettingsCacheState>()
    private var nextWriteRevision = 0L
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialPreferences = CompletableDeferred<Result<Preferences>>()
    private val writes = Channel<SettingsWriteCommand>(Channel.UNLIMITED)

    init {
        writeScope.launch {
            val result = runCatching { context.dataStore.data.first() }
            result.onSuccess { preferences ->
                preferences.asMap().forEach { (key, value) ->
                    cache.putIfAbsent(key.name, value)
                }
            }.onFailure { error ->
                System.err.println("Failed to load Android settings: ${error.message}")
            }
            initialPreferences.complete(result)
        }
        writeScope.launch {
            val initialResult = initialPreferences.await()
            val loadFailure = initialResult.exceptionOrNull()
            val durableValues = initialResult.getOrNull()
                ?.asMap()
                ?.mapKeys { (key, _) -> key.name }
                ?.toMutableMap()
                ?: mutableMapOf()
            var pendingWriteFailure: Throwable? = null
            for (command in writes) {
                when (command) {
                    is SettingsWriteCommand.Update -> {
                        if (loadFailure != null) {
                            pendingWriteFailure = loadFailure
                            applyWriteResult(
                                command = command,
                                durableValue = durableValues[command.key],
                                succeeded = false,
                            )
                        } else {
                            runCatching {
                                context.dataStore.edit { preferences -> command.transform(preferences) }
                                durableValues[command.key] = command.newValue
                                applyWriteResult(
                                    command = command,
                                    durableValue = command.newValue,
                                    succeeded = true,
                                )
                            }.onFailure { error ->
                                pendingWriteFailure = error
                                applyWriteResult(
                                    command = command,
                                    durableValue = durableValues[command.key],
                                    succeeded = false,
                                )
                                System.err.println("Failed to persist Android settings: ${error.message}")
                            }
                        }
                    }

                    is SettingsWriteCommand.Barrier -> {
                        val failure = pendingWriteFailure ?: loadFailure
                        pendingWriteFailure = null
                        if (failure == null) {
                            command.completion.complete(Unit)
                        } else {
                            command.completion.completeExceptionally(failure)
                        }
                    }
                }
            }
        }
    }

    actual suspend fun awaitLoaded() {
        initialPreferences.await().getOrThrow()
    }

    actual suspend fun flush() {
        val completion = CompletableDeferred<Unit>()
        writes.send(SettingsWriteCommand.Barrier(completion))
        completion.await()
    }

    actual fun getString(key: String, default: String): String {
        return cache[key] as? String ?: default
    }

    actual suspend fun getStringAsync(key: String, default: String): String {
        (cache[key] as? String)?.let { return it }
        val value = initialPreferences.await().getOrThrow()[stringPreferencesKey(key)] ?: default
        return cache.putIfAbsent(key, value) as? String ?: value
    }

    actual fun setString(key: String, value: String) {
        persist(key, value) {
            it[stringPreferencesKey(key)] = value
        }
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return cache[key] as? Boolean ?: default
    }

    actual suspend fun getBooleanAsync(key: String, default: Boolean): Boolean {
        (cache[key] as? Boolean)?.let { return it }
        val value = initialPreferences.await().getOrThrow()[booleanPreferencesKey(key)] ?: default
        return cache.putIfAbsent(key, value) as? Boolean ?: value
    }

    actual fun setBoolean(key: String, value: Boolean) {
        persist(key, value) {
            it[booleanPreferencesKey(key)] = value
        }
    }

    actual fun getLong(key: String, default: Long): Long {
        return cache[key] as? Long ?: default
    }

    actual suspend fun getLongAsync(key: String, default: Long): Long {
        (cache[key] as? Long)?.let { return it }
        val value = initialPreferences.await().getOrThrow()[longPreferencesKey(key)] ?: default
        return cache.putIfAbsent(key, value) as? Long ?: value
    }

    actual fun setLong(key: String, value: Long) {
        persist(key, value) {
            it[longPreferencesKey(key)] = value
        }
    }

    private fun persist(
        key: String,
        newValue: Any,
        block: (MutablePreferences) -> Unit,
    ) {
        synchronized(cacheStateLock) {
            val previousValue = cache[key]
            check(nextWriteRevision != Long.MAX_VALUE) { "Android settings write revision exhausted" }
            val revision = ++nextWriteRevision
            val current = cacheStates[key] ?: AndroidSettingsCacheState(
                latestRevision = 0L,
                cachedValue = previousValue,
            )
            val next = beginAndroidSettingsWrite(current, revision, newValue)
            cacheStates[key] = next
            setCachedValue(key, next.cachedValue)

            val command = SettingsWriteCommand.Update(
                key = key,
                revision = revision,
                newValue = newValue,
                transform = block,
            )
            val result = writes.trySend(command)
            if (!result.isSuccess) {
                val rolledBack = reduceAndroidSettingsWriteResult(
                    current = next,
                    commandRevision = revision,
                    commandValue = newValue,
                    durableValue = previousValue,
                    succeeded = false,
                )
                cacheStates[key] = rolledBack
                setCachedValue(key, rolledBack.cachedValue)
                throw IllegalStateException(
                    "Failed to enqueue Android settings write",
                    result.exceptionOrNull(),
                )
            }
        }
    }

    private fun applyWriteResult(
        command: SettingsWriteCommand.Update,
        durableValue: Any?,
        succeeded: Boolean,
    ) {
        synchronized(cacheStateLock) {
            val current = cacheStates[command.key] ?: return
            val next = reduceAndroidSettingsWriteResult(
                current = current,
                commandRevision = command.revision,
                commandValue = command.newValue,
                durableValue = durableValue,
                succeeded = succeeded,
            )
            cacheStates[command.key] = next
            setCachedValue(command.key, next.cachedValue)
        }
    }

    private fun setCachedValue(key: String, value: Any?) {
        if (value == null) cache.remove(key) else cache[key] = value
    }
}
