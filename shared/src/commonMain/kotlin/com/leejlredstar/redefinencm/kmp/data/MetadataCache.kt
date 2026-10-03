package com.leejlredstar.redefinencm.kmp.data

import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.getLongAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class MetadataCacheTable {
    CachedCommentMusic, CachedLyric, CachedPlaylistDetail, CachedPlaylistTrackAll,
    CachedRecommendResource, CachedRecommendSongs, CachedUserDetail, CachedUserLevel, CachedUserPlaylist,
}

/** Bounds disposable JSON responses across all nine tables; owned data is not addressable here.
 * The reported size counts UTF-8 payloads, not SQLite pages or downloaded media. Access order is
 * tracked for this process; pre-existing rows have no timestamps and start in query order. */
class MetadataCache(
    private val database: AppDatabase,
    private val settings: PlatformSettings? = null,
    private val budgetBytes: (suspend () -> Long)? = null,
    private val maxEntries: Int = 4_096,
) {
    private data class Key(val table: MetadataCacheTable, val id: Long)
    private val mutex = Mutex()
    private var entries: LinkedHashMap<Key, Long>? = null
    private val _bytes = MutableStateFlow<Long?>(null)
    val bytes: StateFlow<Long?> = _bytes.asStateFlow()

    init { require(maxEntries > 0) }

    private suspend fun budget(): Long {
        // Keep suspension and nullable Long fallback in separate branches. A chained safe-call
        // emits dup2_x1 bytecode that the desktop ProGuard 7.7 optimizer cannot evaluate.
        val override = budgetBytes
        if (override != null) return override().coerceAtLeast(0L)
        val stored = if (settings == null) DefaultLimitMib
            else settings.getLongAsync(SettingKeys.METADATA_CACHE_LIMIT_MIB, DefaultLimitMib)
        return normalizeLimit(stored) * Mebibyte
    }

    private fun load(force: Boolean = false): LinkedHashMap<Key, Long> {
        val previous = entries
        if (!force && previous != null) return previous
        val loaded = linkedMapOf<Key, Long>()
        database.cacheMaintenanceQueries.entries().executeAsList().forEach { row ->
            val table = MetadataCacheTable.entries.first { it.name == row.table_name }
            loaded[Key(table, row.entry_id)] = (row.bytes ?: 0L).coerceAtLeast(0)
        }
        previous?.keys?.forEach { key -> loaded.remove(key)?.let { loaded[key] = it } }
        entries = loaded
        publish()
        return loaded
    }

    private fun publish() { _bytes.value = entries?.values?.sum() ?: 0 }

    internal suspend fun touch(table: MetadataCacheTable, id: Long) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val rows = load()
            val key = Key(table, id)
            rows.remove(key)?.let { rows[key] = it }
        }
    }

    internal suspend fun write(table: MetadataCacheTable, id: Long, json: String) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val limit = budget()
            val size = json.encodeToByteArray().size.toLong()
            check(size <= limit) { "Response exceeds the text cache budget" }
            val rows = load()
            val key = Key(table, id)
            val retained = LinkedHashMap(rows).apply { remove(key) }
            val evicted = mutableListOf<Key>()
            var projectedBytes = retained.values.sum() + size
            while (projectedBytes > limit || retained.size >= maxEntries) {
                val oldest = retained.entries.first()
                projectedBytes -= oldest.value
                evicted += oldest.key
                retained.remove(oldest.key)
            }
            database.transaction {
                evicted.forEach(::delete)
                when (table) {
                    MetadataCacheTable.CachedCommentMusic -> database.cachedCommentMusicQueries.upsert(id, json)
                    MetadataCacheTable.CachedLyric -> database.cachedLyricQueries.upsert(id, json)
                    MetadataCacheTable.CachedPlaylistDetail -> database.cachedPlaylistDetailQueries.upsert(id, json)
                    MetadataCacheTable.CachedPlaylistTrackAll -> database.cachedPlaylistTrackAllQueries.upsert(id, json)
                    MetadataCacheTable.CachedRecommendResource -> database.cachedRecommendResourceQueries.upsert(json)
                    MetadataCacheTable.CachedRecommendSongs -> database.cachedRecommendSongsQueries.upsert(json)
                    MetadataCacheTable.CachedUserDetail -> database.cachedUserDetailQueries.upsert(id, json)
                    MetadataCacheTable.CachedUserLevel -> database.cachedUserLevelQueries.upsert(id, json)
                    MetadataCacheTable.CachedUserPlaylist -> database.cachedUserPlaylistQueries.upsert(id, json)
                }
            }
            retained[key] = size
            entries = retained
            publish()
        }
    }

    /** Recount existing data and enforce a changed budget without reading JSON into DTOs. */
    suspend fun refresh() = withContext(Dispatchers.Default) {
        mutex.withLock {
            val limit = budget()
            val retained = LinkedHashMap(load(force = true))
            val evicted = mutableListOf<Key>()
            var size = retained.values.sum()
            while (size > limit || retained.size > maxEntries) {
                val oldest = retained.entries.first()
                size -= oldest.value
                evicted += oldest.key
                retained.remove(oldest.key)
            }
            database.transaction { evicted.forEach(::delete) }
            entries = retained
            publish()
        }
    }

    suspend fun clear() = clear(MetadataCacheTable.entries.toSet())

    internal suspend fun clear(tables: Set<MetadataCacheTable>) = withContext(Dispatchers.Default) {
        mutex.withLock {
            val retained = LinkedHashMap(load(force = true))
            val removed = retained.keys.filter { it.table in tables }
            database.transaction { removed.forEach(::delete) }
            removed.forEach(retained::remove)
            entries = retained
            publish()
        }
    }

    private fun delete(key: Key) = with(database.cacheMaintenanceQueries) {
        when (key.table) {
            MetadataCacheTable.CachedCommentMusic -> deleteComment(key.id)
            MetadataCacheTable.CachedLyric -> deleteLyric(key.id)
            MetadataCacheTable.CachedPlaylistDetail -> deletePlaylistDetail(key.id)
            MetadataCacheTable.CachedPlaylistTrackAll -> deletePlaylistTracks(key.id)
            MetadataCacheTable.CachedRecommendResource -> deleteRecommendResource(key.id)
            MetadataCacheTable.CachedRecommendSongs -> deleteRecommendSongs(key.id)
            MetadataCacheTable.CachedUserDetail -> deleteUserDetail(key.id)
            MetadataCacheTable.CachedUserLevel -> deleteUserLevel(key.id)
            MetadataCacheTable.CachedUserPlaylist -> deleteUserPlaylist(key.id)
        }
    }

    companion object {
        const val Mebibyte = 1_048_576L
        const val DefaultLimitMib = 128L
        val LimitChoicesMib = listOf(64L, 128L, 256L, 512L)
        fun normalizeLimit(value: Long): Long = value.takeIf { it in LimitChoicesMib } ?: DefaultLimitMib
    }
}
