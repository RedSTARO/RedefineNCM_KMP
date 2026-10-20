package com.leejlredstar.redefinencm.kmp.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.leejlredstar.redefinencm.kmp.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * A settings file DataStore cannot parse would fail every read for good, and the app could not
 * start again until the user cleared its data. Starting over with defaults signs the user out but
 * keeps everything stored elsewhere (the database, the downloads), and the app says so once.
 */
internal val SettingsCorruptionHandler = ReplaceFileCorruptionHandler { failure ->
    System.err.println("Android settings were unreadable and are reset: ${failure.message}")
    StartupNotices.post(StartupNotice.SettingsReset)
    emptyPreferences()
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "redefinencm_settings",
    corruptionHandler = SettingsCorruptionHandler,
)

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
 * The synchronous getters are cache-only: DataStore is asynchronous and must never
 * be bridged with `runBlocking` from Compose or Koin construction on the main thread. Callers that
 * require the persisted value before continuing use the suspend getters, which wait for the one
 * background snapshot load. Writes update the cache before returning and are serialized through
 * a single channel, so an older initial snapshot or an out-of-order coroutine cannot overwrite a
 * newer value.
 *
 * A failed read is not kept: the next caller, and the next write, read again. One failure (a full
 * disk, an I/O error) then lasts until it clears instead of as long as the process.
 */
actual class PlatformSettings internal constructor(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.dataStore)

    private val cache = ConcurrentHashMap<String, Any>()
    private val cacheStateLock = Any()
    private val cacheStates = mutableMapOf<String, AndroidSettingsCacheState>()
    private var nextWriteRevision = 0L
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadLock = Mutex()
    @Volatile
    private var loadedPreferences: Preferences? = null
    private val firstLoad = CompletableDeferred<Unit>()
    private val writes = Channel<SettingsWriteCommand>(Channel.UNLIMITED)

    init {
        // Read early, so the first caller rarely waits; a failure here is only logged.
        writeScope.launch { runCatching { load() } }
        writeScope.launch {
            var durableValues: MutableMap<String, Any>? = null
            var pendingWriteFailure: Throwable? = null
            for (command in writes) {
                // Nothing is written over a file that has not been read: the write would replace
                // every value the app could not see.
                val durable = durableValues ?: try {
                    load().asMap().mapKeys { (key, _) -> key.name }.toMutableMap().also { durableValues = it }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    pendingWriteFailure = failure
                    null
                }
                when (command) {
                    is SettingsWriteCommand.Update -> {
                        if (durable == null) {
                            applyWriteResult(
                                command = command,
                                durableValue = null,
                                succeeded = false,
                            )
                        } else {
                            runCatching {
                                dataStore.edit { preferences -> command.transform(preferences) }
                                durable[command.key] = command.newValue
                                applyWriteResult(
                                    command = command,
                                    durableValue = command.newValue,
                                    succeeded = true,
                                )
                            }.onFailure { error ->
                                pendingWriteFailure = error
                                applyWriteResult(
                                    command = command,
                                    durableValue = durable[command.key],
                                    succeeded = false,
                                )
                                System.err.println("Failed to persist Android settings: ${error.message}")
                            }
                        }
                    }

                    is SettingsWriteCommand.Barrier -> {
                        val failure = pendingWriteFailure
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
        load()
    }

    /**
     * Suspends until the stored settings have been read, however many attempts that takes, and
     * never fails. For work that may wait for someone else's retry rather than give up.
     */
    suspend fun awaitFirstLoad() {
        firstLoad.await()
    }

    private suspend fun load(): Preferences {
        loadedPreferences?.let { return it }
        return loadLock.withLock {
            loadedPreferences ?: try {
                dataStore.data.first().also { preferences ->
                    preferences.asMap().forEach { (key, value) ->
                        cache.putIfAbsent(key.name, value)
                    }
                    loadedPreferences = preferences
                    firstLoad.complete(Unit)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                System.err.println("Failed to load Android settings: ${failure.message}")
                throw failure
            }
        }
    }

    /** System theme overrides follow durable writes, not an optimistic Compose preference.
     * Otherwise a configuration recreation can cancel the screen before its save starts. */
    fun themeModeUpdates(): Flow<ThemeMode> = dataStore.data
        .map { ThemeMode.fromWireValue(it[stringPreferencesKey(SettingKeys.THEME_MODE)].orEmpty()) }
        .distinctUntilChanged()

    actual suspend fun flush() {
        val completion = CompletableDeferred<Unit>()
        writes.send(SettingsWriteCommand.Barrier(completion))
        completion.await()
    }

    actual fun getString(key: String, default: String): String {
        return cache[key] as? String ?: default
    }

    actual fun setString(key: String, value: String) {
        persist(key, value) {
            it[stringPreferencesKey(key)] = value
        }
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return cache[key] as? Boolean ?: default
    }

    actual fun setBoolean(key: String, value: Boolean) {
        persist(key, value) {
            it[booleanPreferencesKey(key)] = value
        }
    }

    actual fun getLong(key: String, default: Long): Long {
        return cache[key] as? Long ?: default
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
