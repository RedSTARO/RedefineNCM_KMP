package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.data.db.BrowserDatabaseStorage
import com.leejlredstar.redefinencm.kmp.data.db.BrowserStorageSqlDriver
import com.leejlredstar.redefinencm.kmp.data.db.LocalDatabaseStorage
import com.leejlredstar.redefinencm.kmp.i18n.strings

/**
 * [keyPrefix] exists so tests can hold a throwaway corner of localStorage. Production always
 * uses the empty prefix: prefixing would orphan every value an installed copy has written.
 *
 * localStorage refuses in two ways, and neither may stop the app. With site data blocked every
 * access throws: settings then live in memory for the session, so the app starts and a sign-in
 * works until the page closes. When it is full, the database's stored cache rows, which its
 * driver also keeps in memory, are dropped to make room. If the write still fails, the stored
 * value stays and [flush] reports it, so a caller that saves through flush (credentials, an
 * import) rolls back and says why.
 */
actual class PlatformSettings internal constructor(
    private val keyPrefix: String,
    private val storage: BrowserDatabaseStorage,
) {
    constructor(keyPrefix: String = "") : this(keyPrefix, LocalDatabaseStorage)

    /** Null while localStorage answers; this session's values once it has refused access. */
    private var sessionOnly: MutableMap<String, String>? = null

    /** A write that could not be stored, reported once by [flush]. */
    private var unsavedWrite: String? = null

    private fun storageKey(key: String): String = keyPrefix + key

    actual suspend fun awaitLoaded() = Unit

    actual suspend fun flush() {
        val failure = unsavedWrite ?: return
        unsavedWrite = null
        throw IllegalStateException(failure)
    }

    private fun read(key: String): String? {
        val storedKey = storageKey(key)
        sessionOnly?.let { return it[storedKey] }
        return try {
            storage.get(storedKey)
        } catch (_: Throwable) {
            sessionOnly = mutableMapOf()
            null
        }
    }

    private fun write(key: String, value: String) {
        val storedKey = storageKey(key)
        sessionOnly?.let {
            it[storedKey] = value
            return
        }
        if (tryStore(storedKey, value)) return
        if (runCatching { storage.get(storedKey) }.isFailure) {
            sessionOnly = mutableMapOf(storedKey to value)
            return
        }
        val caches = runCatching { storage.keys() }.getOrDefault(emptyList())
            .filter(BrowserStorageSqlDriver::isCacheStorageKey)
            .sortedByDescending { runCatching { storage.get(it)?.length ?: 0 }.getOrDefault(0) }
        for (cacheKey in caches) {
            runCatching { storage.remove(cacheKey) }
            if (tryStore(storedKey, value)) return
        }
        unsavedWrite = strings.browserDataSaveFailed
    }

    private fun tryStore(key: String, value: String): Boolean =
        runCatching { storage.set(key, value) }.isSuccess

    actual fun getString(key: String, default: String): String = read(key) ?: default

    actual fun setString(key: String, value: String) = write(key, value)

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        val value = read(key) ?: return default
        // setBoolean only ever writes "true"/"false"; anything else is a value this app did not
        // write, and the caller's default is a better answer than reporting it as false.
        return value.toBooleanStrictOrNull() ?: default
    }

    actual fun setBoolean(key: String, value: Boolean) = write(key, value.toString())

    actual fun getLong(key: String, default: Long): Long {
        val value = read(key) ?: return default
        return value.toLongOrNull() ?: default
    }

    actual fun setLong(key: String, value: Long) = write(key, value.toString())
}
