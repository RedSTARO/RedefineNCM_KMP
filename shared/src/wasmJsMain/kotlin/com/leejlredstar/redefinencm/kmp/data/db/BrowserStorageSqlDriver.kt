package com.leejlredstar.redefinencm.kmp.data.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.browser.localStorage

/**
 * Synchronous SQLDelight driver for the browser cache used by this project.
 *
 * The schema contains only JSON value tables addressed by one integer primary key. Browsers do
 * not expose a synchronous SQLite API, while SQLDelight's worker driver would force every other
 * target onto async generated queries. This driver keeps the generated [AppDatabase] API intact
 * and persists the exact current query model in localStorage. Unsupported SQL fails immediately
 * so future schema changes cannot silently lose data.
 */
internal class BrowserStorageSqlDriver : SqlDriver {
    private val listeners = mutableMapOf<String, MutableSet<Query.Listener>>()
    private val volatileStorage = linkedMapOf<String, String>()
    private var persistenceEnabled = loadPersistentStorage()
    private var transaction: BrowserTransaction? = null

    var schemaVersion: Long
        get() = readStorageValue(SCHEMA_VERSION_KEY)?.toLongOrNull() ?: 0L
        set(value) = writeStorageValue(SCHEMA_VERSION_KEY, value.toString())

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        val normalized = sql.normalizedSql()
        if (normalized in SUPPORTED_CREATE_SQL) {
            require(parameters == 0) { "Schema statements cannot bind parameters" }
            return QueryResult.Value(0L)
        }

        val statement = BrowserPreparedStatement(parameters).also { prepared ->
            binders?.invoke(prepared)
        }
        return when (val table = UPSERT_SQL_TO_TABLE[normalized]) {
            null -> when (val deleteTable = DELETE_SQL_TO_TABLE[normalized]) {
                null -> error("Unsupported browser cache statement: $normalized")
                else -> {
                    require(parameters == 0) { "DELETE for $deleteTable cannot bind parameters" }
                    val keys = keysForTable(deleteTable)
                    keys.forEach(::removeStorageValue)
                    QueryResult.Value(keys.size.toLong())
                }
            }
            else -> {
                val expectedParameters = if (table in KEYED_TABLES) 2 else 1
                require(parameters == expectedParameters) {
                    "Expected $expectedParameters parameters for $table, got $parameters"
                }
                val key = keyForTable(table, statement.values)
                val json = statement.values.lastOrNull() as? String
                    ?: error("Missing JSON value for $table")
                writeStorageValue(storageKey(table, key), json)
                QueryResult.Value(1L)
            }
        }
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        val normalized = sql.normalizedSql()
        val table = SELECT_SQL_TO_TABLE[normalized]
            ?: error("Unsupported browser cache query: $normalized")
        val expectedParameters = if (table in KEYED_TABLES) 1 else 0
        require(parameters == expectedParameters) {
            "Expected $expectedParameters parameters for $table, got $parameters"
        }
        val statement = BrowserPreparedStatement(parameters).also { prepared ->
            binders?.invoke(prepared)
        }
        val key = keyForTable(table, statement.values)
        val value = readStorageValue(storageKey(table, key))
        return mapper(BrowserCursor(value))
    }

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        val next = BrowserTransaction(
            enclosing = transaction,
            snapshot = snapshotStorage(),
        )
        transaction = next
        return QueryResult.Value(next)
    }

    override fun currentTransaction(): Transacter.Transaction? = transaction

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        queryKeys.forEach { key -> listeners.getOrPut(key) { linkedSetOf() }.add(listener) }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        queryKeys.forEach { key ->
            listeners[key]?.let { registered ->
                registered.remove(listener)
                if (registered.isEmpty()) listeners.remove(key)
            }
        }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        queryKeys.flatMapTo(linkedSetOf()) { listeners[it].orEmpty() }
            .forEach(Query.Listener::queryResultsChanged)
    }

    override fun close() {
        listeners.clear()
        transaction = null
    }

    private inner class BrowserTransaction(
        private val enclosing: BrowserTransaction?,
        private val snapshot: Map<String, String>,
    ) : Transacter.Transaction() {
        override val enclosingTransaction: Transacter.Transaction?
            get() = enclosing

        override fun endTransaction(successful: Boolean): QueryResult<Unit> {
            check(transaction === this) { "Transactions must end in LIFO order" }
            if (!successful) restoreStorage(snapshot)
            transaction = enclosing
            return QueryResult.Unit
        }
    }

    private class BrowserPreparedStatement(parameterCount: Int) : SqlPreparedStatement {
        val values = MutableList<Any?>(parameterCount) { null }

        override fun bindBytes(index: Int, bytes: ByteArray?) = bind(index, bytes)
        override fun bindLong(index: Int, long: Long?) = bind(index, long)
        override fun bindDouble(index: Int, double: Double?) = bind(index, double)
        override fun bindString(index: Int, string: String?) = bind(index, string)
        override fun bindBoolean(index: Int, boolean: Boolean?) = bind(index, boolean)

        private fun bind(index: Int, value: Any?) {
            require(index in values.indices) { "Parameter index $index is out of bounds" }
            values[index] = value
        }
    }

    private class BrowserCursor(private val value: String?) : SqlCursor {
        private var consumed = false

        override fun next(): QueryResult<Boolean> = QueryResult.Value(
            !consumed && value != null,
        ).also { consumed = true }

        override fun getString(index: Int): String? = valueAt(index)
        override fun getLong(index: Int): Long? = valueAt(index)?.toLongOrNull()
        override fun getBytes(index: Int): ByteArray? = valueAt(index)?.encodeToByteArray()
        override fun getDouble(index: Int): Double? = valueAt(index)?.toDoubleOrNull()
        override fun getBoolean(index: Int): Boolean? = valueAt(index)?.toBooleanStrictOrNull()

        private fun valueAt(index: Int): String? {
            require(index == 0) { "Browser cache queries expose one column" }
            check(consumed) { "Call next() before reading the cursor" }
            return value
        }
    }

    private fun keyForTable(table: String, values: List<Any?>): Long = when (table) {
        "CachedRecommendResource", "CachedRecommendSongs", "DownloadQueue" -> 0L
        "PlayerStatus" -> 1L
        in KEYED_TABLES -> values.firstOrNull() as? Long
            ?: error("Missing primary key for $table")
        else -> error("Unknown browser cache table: $table")
    }

    private fun storageKey(table: String, key: Long): String = "$STORAGE_PREFIX$table:$key"

    private fun keysForTable(table: String): List<String> {
        val prefix = "$STORAGE_PREFIX$table:"
        return storageKeys().filter { it.startsWith(prefix) }
    }

    private fun snapshotStorage(): Map<String, String> = volatileStorage.toMap()

    private fun restoreStorage(snapshot: Map<String, String>) {
        val oldKeys = storageKeys()
        volatileStorage.clear()
        volatileStorage.putAll(snapshot)
        if (!persistenceEnabled) return
        val restored = runCatching {
            oldKeys.forEach(localStorage::removeItem)
            snapshot.forEach { (key, value) -> localStorage.setItem(key, value) }
        }.isSuccess
        if (!restored) persistenceEnabled = false
    }

    private fun storageKeys(): List<String> = volatileStorage.keys.toList()

    private fun readStorageValue(key: String): String? = volatileStorage[key]

    /**
     * Cache persistence is best effort: quota/security failures must never discard a successful
     * network response. Keep the full session cache in memory, evict older persistent cache rows,
     * and fall back to memory-only storage when even the new row cannot fit.
     */
    private fun writeStorageValue(key: String, value: String) {
        volatileStorage[key] = value
        if (!persistenceEnabled || persistValue(key, value)) return

        volatileStorage.entries
            .asSequence()
            .filter { (candidate, _) -> candidate != key && candidate != SCHEMA_VERSION_KEY }
            .sortedByDescending { (_, cachedValue) -> cachedValue.length }
            .forEach { (candidate, _) ->
                runCatching { localStorage.removeItem(candidate) }
                if (persistValue(key, value)) return
            }

        runCatching { localStorage.removeItem(key) }
        persistenceEnabled = false
    }

    private fun removeStorageValue(key: String) {
        volatileStorage.remove(key)
        if (persistenceEnabled) runCatching { localStorage.removeItem(key) }
    }

    private fun persistValue(key: String, value: String): Boolean =
        runCatching { localStorage.setItem(key, value) }.isSuccess

    private fun loadPersistentStorage(): Boolean = runCatching {
        val loaded = linkedMapOf<String, String>()
        repeat(localStorage.length) { index ->
            val key = localStorage.key(index)?.takeIf { it.startsWith(STORAGE_PREFIX) }
                ?: return@repeat
            localStorage.getItem(key)?.let { loaded[key] = it }
        }
        volatileStorage.putAll(loaded)
    }.isSuccess

    private fun String.normalizedSql(): String = trim().replace(SQL_WHITESPACE, " ")

    private companion object {
        const val STORAGE_PREFIX = "redefinencm.db."
        const val SCHEMA_VERSION_KEY = "${STORAGE_PREFIX}schemaVersion"
        val SQL_WHITESPACE = Regex("\\s+")
        val KEYED_TABLES = setOf(
            "CachedCommentMusic",
            "CachedLyric",
            "CachedPlaylistDetail",
            "CachedPlaylistTrackAll",
            "CachedUserDetail",
            "CachedUserLevel",
            "CachedUserPlaylist",
        )

        val SUPPORTED_CREATE_SQL = setOf(
            "CREATE TABLE IF NOT EXISTS CachedCommentMusic ( song_id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS CachedLyric ( song_id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS CachedPlaylistDetail ( playlist_id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS CachedPlaylistTrackAll ( playlist_id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS CachedRecommendResource ( singleton INTEGER NOT NULL PRIMARY KEY DEFAULT 0, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS CachedRecommendSongs ( singleton INTEGER NOT NULL PRIMARY KEY DEFAULT 0, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS CachedUserDetail ( uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS CachedUserLevel ( uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS CachedUserPlaylist ( uid INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS DownloadQueue ( singleton INTEGER NOT NULL PRIMARY KEY DEFAULT 0, json TEXT NOT NULL )",
            "CREATE TABLE IF NOT EXISTS PlayerStatus ( id INTEGER NOT NULL PRIMARY KEY, json TEXT NOT NULL )",
        )

        val UPSERT_SQL_TO_TABLE = mapOf(
            "INSERT OR REPLACE INTO CachedCommentMusic(song_id, json) VALUES (?, ?)" to "CachedCommentMusic",
            "INSERT OR REPLACE INTO CachedLyric(song_id, json) VALUES (?, ?)" to "CachedLyric",
            "INSERT OR REPLACE INTO CachedPlaylistDetail(playlist_id, json) VALUES (?, ?)" to "CachedPlaylistDetail",
            "INSERT OR REPLACE INTO CachedPlaylistTrackAll(playlist_id, json) VALUES (?, ?)" to "CachedPlaylistTrackAll",
            "INSERT OR REPLACE INTO CachedRecommendResource(singleton, json) VALUES (0, ?)" to "CachedRecommendResource",
            "INSERT OR REPLACE INTO CachedRecommendSongs(singleton, json) VALUES (0, ?)" to "CachedRecommendSongs",
            "INSERT OR REPLACE INTO CachedUserDetail(uid, json) VALUES (?, ?)" to "CachedUserDetail",
            "INSERT OR REPLACE INTO CachedUserLevel(uid, json) VALUES (?, ?)" to "CachedUserLevel",
            "INSERT OR REPLACE INTO CachedUserPlaylist(uid, json) VALUES (?, ?)" to "CachedUserPlaylist",
            "INSERT OR REPLACE INTO DownloadQueue(singleton, json) VALUES (0, ?)" to "DownloadQueue",
            "INSERT OR REPLACE INTO PlayerStatus(id, json) VALUES (1, ?)" to "PlayerStatus",
        )

        val SELECT_SQL_TO_TABLE = mapOf(
            "SELECT json FROM CachedCommentMusic WHERE song_id = ?" to "CachedCommentMusic",
            "SELECT json FROM CachedLyric WHERE song_id = ?" to "CachedLyric",
            "SELECT json FROM CachedPlaylistDetail WHERE playlist_id = ?" to "CachedPlaylistDetail",
            "SELECT json FROM CachedPlaylistTrackAll WHERE playlist_id = ?" to "CachedPlaylistTrackAll",
            "SELECT json FROM CachedRecommendResource WHERE singleton = 0" to "CachedRecommendResource",
            "SELECT json FROM CachedRecommendSongs WHERE singleton = 0" to "CachedRecommendSongs",
            "SELECT json FROM CachedUserDetail WHERE uid = ?" to "CachedUserDetail",
            "SELECT json FROM CachedUserLevel WHERE uid = ?" to "CachedUserLevel",
            "SELECT json FROM CachedUserPlaylist WHERE uid = ?" to "CachedUserPlaylist",
            "SELECT json FROM DownloadQueue WHERE singleton = 0" to "DownloadQueue",
            "SELECT json FROM PlayerStatus WHERE id = 1" to "PlayerStatus",
        )

        val DELETE_SQL_TO_TABLE = mapOf(
            "DELETE FROM CachedRecommendResource" to "CachedRecommendResource",
            "DELETE FROM CachedRecommendSongs" to "CachedRecommendSongs",
            "DELETE FROM DownloadQueue" to "DownloadQueue",
        )
    }
}
