package com.leejlredstar.redefinencm.kmp.data.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import kotlinx.browser.localStorage
import com.leejlredstar.redefinencm.kmp.i18n.strings

/** Small storage boundary so quota and permission failures can be exercised deterministically. */
internal interface BrowserDatabaseStorage {
    fun keys(): List<String>
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}

private object LocalDatabaseStorage : BrowserDatabaseStorage {
    override fun keys(): List<String> = buildList {
        repeat(localStorage.length) { index -> localStorage.key(index)?.let(::add) }
    }
    override fun get(key: String): String? = localStorage.getItem(key)
    override fun set(key: String, value: String) = localStorage.setItem(key, value)
    override fun remove(key: String) = localStorage.removeItem(key)
}

/**
 * Synchronous SQLDelight driver for the browser cache used by this project.
 *
 * The schema contains only JSON value tables addressed by one integer primary key. That includes
 * the local library, which is why it is one JSON document rather than relational tables. Browsers
 * do not expose a synchronous SQLite API, while SQLDelight's worker driver would force every
 * other target onto async generated queries. This driver keeps the generated [AppDatabase] API
 * intact and persists the exact current query model in localStorage. Unsupported SQL fails
 * immediately so future schema changes cannot silently lose data.
 */
internal class BrowserStorageSqlDriver(
    private val storage: BrowserDatabaseStorage = LocalDatabaseStorage,
) : SqlDriver {
    private val listeners = mutableMapOf<String, MutableSet<Query.Listener>>()
    private val volatileStorage = linkedMapOf<String, String>()
    private val persistenceEnabled = loadPersistentStorage()
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
        CACHE_ROW_DELETES[normalized]?.let { table ->
            require(parameters == 1)
            val id = statement.values.single() as? Long ?: error("Missing cache entry id")
            val key = storageKey(table, id)
            val existed = readStorageValue(key) != null
            removeStorageValue(key)
            return QueryResult.Value(if (existed) 1L else 0L)
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
        if (normalized == CACHE_ENTRIES_SQL) {
            require(parameters == 0)
            val rows = CACHE_COLUMNS.keys.flatMap { table ->
                keysForTable(table).mapNotNull { key ->
                    val id = key.substringAfterLast(':').toLongOrNull() ?: return@mapNotNull null
                    val json = readStorageValue(key) ?: return@mapNotNull null
                    listOf<Any?>(table, id, json.encodeToByteArray().size.toLong())
                }
            }
            return mapper(BrowserCursor(rows))
        }
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
            try {
                if (!successful) restoreStorage(snapshot)
            } finally {
                transaction = enclosing
            }
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

    private class BrowserCursor(private val rows: List<List<Any?>>) : SqlCursor {
        constructor(value: String?) : this(value?.let { listOf(listOf(it)) } ?: emptyList())
        private var rowIndex = -1

        override fun next(): QueryResult<Boolean> = QueryResult.Value(++rowIndex < rows.size)

        override fun getString(index: Int): String? = valueAt(index) as? String
        override fun getLong(index: Int): Long? = (valueAt(index) as? Long) ?: getString(index)?.toLongOrNull()
        override fun getBytes(index: Int): ByteArray? = getString(index)?.encodeToByteArray()
        override fun getDouble(index: Int): Double? = getString(index)?.toDoubleOrNull()
        override fun getBoolean(index: Int): Boolean? = getString(index)?.toBooleanStrictOrNull()

        private fun valueAt(index: Int): Any? {
            check(rowIndex in rows.indices) { "Cursor is not on a row" }
            require(index in rows[rowIndex].indices) { "Column index is out of bounds" }
            return rows[rowIndex][index]
        }
    }

    private fun keyForTable(table: String, values: List<Any?>): Long = when (table) {
        "CachedRecommendResource", "CachedRecommendSongs", "DownloadQueue", "LocalLibrary" -> 0L
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
        // Do not erase the whole database to roll back a cache update. Under quota pressure
        // rewriting that snapshot could fail after deleting unrelated playlists and queues.
        (storageKeys() - snapshot.keys).forEach(::removeStorageValue)
        snapshot.forEach { (key, value) ->
            if (volatileStorage[key] != value) writeStorageValue(key, value)
        }
    }

    private fun storageKeys(): List<String> = volatileStorage.keys.toList()

    private fun readStorageValue(key: String): String? = volatileStorage[key]

    /**
     * Only Cached* rows are expendable. A cache too large for localStorage stays in memory;
     * user data must reach storage before it is published, or its caller receives a failure.
     * A failed setItem leaves the previous stored value intact. Do not remove it or disable
     * persistence globally: smaller writes can still succeed after a quota failure.
     */
    private fun writeStorageValue(key: String, value: String) {
        val mustPersist = key != SCHEMA_VERSION_KEY && !isCacheKey(key)
        if (!mustPersist) volatileStorage[key] = value
        if (persistenceEnabled) {
            if (persistValue(key, value)) {
                volatileStorage[key] = value
                return
            }
            val candidates = volatileStorage.entries
                .filter { (candidate, _) -> candidate != key && isCacheKey(candidate) }
                .sortedByDescending { (_, cachedValue) -> cachedValue.length }
            for ((candidate, _) in candidates) {
                runCatching { storage.remove(candidate) }
                if (persistValue(key, value)) {
                    volatileStorage[key] = value
                    return
                }
            }
            // An oversized cache may replace an older row; do not reload that stale row later.
            if (isCacheKey(key)) runCatching { storage.remove(key) }
        }
        check(!mustPersist) { strings.browserDataSaveFailed }
    }

    private fun removeStorageValue(key: String) {
        val removed = persistenceEnabled && runCatching { storage.remove(key) }.isSuccess
        check(removed || (!persistenceEnabled && isCacheKey(key))) { strings.browserDataSaveFailed }
        volatileStorage.remove(key)
    }

    private fun persistValue(key: String, value: String): Boolean =
        runCatching { storage.set(key, value) }.isSuccess

    private fun loadPersistentStorage(): Boolean = runCatching {
        val loaded = linkedMapOf<String, String>()
        storage.keys().filter { it.startsWith(STORAGE_PREFIX) }.forEach { key ->
            storage.get(key)?.let { loaded[key] = it }
        }
        volatileStorage.putAll(loaded)
    }.isSuccess

    private fun isCacheKey(key: String): Boolean = key.removePrefix(STORAGE_PREFIX)
        .substringBefore(':') in CACHE_TABLES

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
        val CACHE_TABLES = KEYED_TABLES + setOf("CachedRecommendResource", "CachedRecommendSongs")
        val CACHE_COLUMNS = linkedMapOf(
            "CachedCommentMusic" to "song_id", "CachedLyric" to "song_id",
            "CachedPlaylistDetail" to "playlist_id", "CachedPlaylistTrackAll" to "playlist_id",
            "CachedRecommendResource" to "singleton", "CachedRecommendSongs" to "singleton",
            "CachedUserDetail" to "uid", "CachedUserLevel" to "uid", "CachedUserPlaylist" to "uid",
        )
        val CACHE_ROW_DELETES = CACHE_COLUMNS.map { (table, column) ->
            "DELETE FROM $table WHERE $column = ?" to table
        }.toMap()
        val CACHE_ENTRIES_SQL = CACHE_COLUMNS.map { (table, column) ->
            "SELECT '$table' AS table_name, $column AS entry_id, length(CAST(json AS BLOB)) AS bytes FROM $table"
        }.joinToString(" UNION ALL ")

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
            "CREATE TABLE IF NOT EXISTS LocalLibrary ( singleton INTEGER NOT NULL PRIMARY KEY DEFAULT 0, json TEXT NOT NULL )",
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
            "INSERT OR REPLACE INTO LocalLibrary(singleton, json) VALUES (0, ?)" to "LocalLibrary",
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
            "SELECT json FROM LocalLibrary WHERE singleton = 0" to "LocalLibrary",
            "SELECT json FROM PlayerStatus WHERE id = 1" to "PlayerStatus",
        )

        val DELETE_SQL_TO_TABLE = mapOf(
            "DELETE FROM CachedRecommendResource" to "CachedRecommendResource",
            "DELETE FROM CachedRecommendSongs" to "CachedRecommendSongs",
            "DELETE FROM DownloadQueue" to "DownloadQueue",
        )
    }
}
