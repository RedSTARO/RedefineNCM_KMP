package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.data.CachedExternalTtml
import com.leejlredstar.redefinencm.kmp.data.LyricCacheStatus
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.AmlldbApi
import com.leejlredstar.redefinencm.kmp.data.api.AmlldbTtmlResult
import com.leejlredstar.redefinencm.kmp.data.api.dto.Lyric
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderLyric
import com.leejlredstar.redefinencm.kmp.data.provider.mediaId
import com.leejlredstar.redefinencm.kmp.download.LocalMediaAssets
import com.leejlredstar.amll.compose.lyric.LyricParser
import com.leejlredstar.amll.compose.lyric.TtmlLyricParser
import com.leejlredstar.amll.compose.lyric.hasPrimaryTimedLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/**
 * The track lyrics are asked for. [itemId] names it with its provider; the TTML source and the
 * local sidecars are NetEase's and read [songId], which is 0 for another provider's track.
 */
data class LyricQuery(
    val itemId: ProviderItemId,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
) {
    constructor(
        songId: Long,
        title: String = "",
        artist: String = "",
        album: String = "",
        durationMs: Long = 0L,
    ) : this(ProviderItemId.netease(songId), title, artist, album, durationMs)

    val songId: Long get() = itemId.neteaseIdOrNull ?: 0L

    /** Whether the track is NetEase's, which the TTML source and the local sidecars need. */
    val isNetease: Boolean get() = itemId.provider == MusicProviderId.NETEASE
}

data class LyricDocument(
    val source: LyricSource,
    val capabilityLevel: LyricCapabilityLevel,
    val lines: List<LyricParser.WordLine>,
    val untimedLines: List<String> = emptyList(),
    val rawTtml: String = "",
    val rawLineLyric: String = "",
    val rawWordLyric: String = "",
    val rawTranslatedLyric: String = "",
    val rawRomanLyric: String = "",
    val providerItemId: String = "",
    val endpoint: String = "",
)

sealed interface LyricProviderResult {
    data class Found(val document: LyricDocument) : LyricProviderResult
    data class Untimed(val document: LyricDocument) : LyricProviderResult
    data object NoMatch : LyricProviderResult
    data class Unavailable(val reason: String) : LyricProviderResult
    data class Malformed(val reason: String) : LyricProviderResult
}

sealed interface LyricResolution {
    data class Found(val document: LyricDocument) : LyricResolution
    data class Untimed(val document: LyricDocument) : LyricResolution
    data object Empty : LyricResolution
    data class Error(val message: String) : LyricResolution
}

interface LyricSourceProvider {
    val source: LyricSource
    fun load(query: LyricQuery): Flow<LyricProviderResult>
}

class LyricResolver(
    private val providers: List<LyricSourceProvider>,
    private val localLyricLoader: (suspend (LyricQuery, LyricSource) -> LyricDocument?)? = null,
) {
    private val memoryCache =
        MutableStateFlow<Map<LyricMemoryCacheKey, LyricResolution>>(emptyMap())

    constructor(
        repository: Repository,
        amlldbApi: AmlldbApi,
        localMediaAssets: LocalMediaAssets,
        providers: MusicProviderRegistry,
    ) : this(
        listOf(
            TtmlLyricProvider(repository, amlldbApi),
            BackendLyricProvider(repository::getLyric, foreignLyric = providers::lyric),
        ),
        localMediaAssets::loadLyrics,
    )

    fun resolve(
        query: LyricQuery,
        mode: LyricSourceMode,
        preferLocal: Boolean = false,
    ): Flow<LyricResolution> = flow {
        require(!query.isNetease || query.songId > 0) { "songId must be positive" }
        val cacheKey = LyricMemoryCacheKey(
            item = query.itemId.toString(),
            durationMs = query.durationMs,
            mode = mode,
            preferLocal = preferLocal,
        )
        val retainedResolution = memoryCache.value[cacheKey]
        retainedResolution?.let { emit(it) }
        val failures = mutableListOf<String>()
        var untimedCandidate: LyricDocument? = null
        // Best timed document emitted so far. Cache-then-network keeps resolving after a local
        // hit, so this is what later results must beat before they are allowed to replace it.
        var emittedDocument: LyricDocument? =
            (retainedResolution as? LyricResolution.Found)?.document

        for (source in mode.sourceOrder) {
            if (preferLocal) {
                val localDocument = try {
                    localLyricLoader?.invoke(query, source)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (localDocument != null) {
                    if (localDocument.capabilityLevel == LyricCapabilityLevel.UNSYNCED) {
                        if (untimedCandidate == null) untimedCandidate = localDocument
                    } else if (localDocument.outranks(emittedDocument)) {
                        // Cache-then-network: show the on-disk lyric immediately, then keep going
                        // so the upstream copy can still upgrade it. Returning here would make the
                        // local sidecar authoritative and skip the refresh.
                        emittedDocument = localDocument
                        val resolution = LyricResolution.Found(localDocument)
                        remember(cacheKey, resolution)
                        if (resolution != retainedResolution) emit(resolution)
                    }
                }
            }
            val provider = providers.firstOrNull { it.source == source }
            if (provider == null) {
                failures += "$source provider is unavailable"
                continue
            }

            var found = false
            try {
                provider.load(query).collect { result ->
                    when (result) {
                        is LyricProviderResult.Found -> {
                            if (result.document.capabilityLevel == LyricCapabilityLevel.UNSYNCED) {
                                if (untimedCandidate == null) untimedCandidate = result.document
                            } else {
                                found = true
                                if (result.document.outranks(emittedDocument)) {
                                    emittedDocument = result.document
                                    val resolution = LyricResolution.Found(result.document)
                                    remember(cacheKey, resolution)
                                    if (resolution != retainedResolution) emit(resolution)
                                }
                            }
                        }
                        is LyricProviderResult.Untimed -> {
                            if (untimedCandidate == null) untimedCandidate = result.document
                        }
                        LyricProviderResult.NoMatch -> Unit
                        is LyricProviderResult.Malformed -> failures += result.reason
                        is LyricProviderResult.Unavailable -> failures += result.reason
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures += "$source lyric request failed"
            }
            if (found) return@flow
        }

        // A local hit has already been emitted at this point, so the terminal states below must
        // not fire and wipe it: every provider missing is not an error once the disk copy is up.
        if (emittedDocument != null) return@flow

        val fallbackDocument = untimedCandidate
        if (fallbackDocument != null && retainedResolution !is LyricResolution.Found) {
            val resolution = LyricResolution.Untimed(fallbackDocument)
            remember(cacheKey, resolution)
            if (resolution != retainedResolution) emit(resolution)
        } else if (retainedResolution == null) {
            if (failures.isEmpty()) {
                emit(LyricResolution.Empty)
            } else {
                emit(LyricResolution.Error(failures.distinct().joinToString("; ")))
            }
        }
    }

    /**
     * Synchronous media-scoped snapshot used by the player UI before launching a refresh.
     *
     * Source mode and local preference are part of the key so a cached fallback can never bypass
     * TTML-only/backend-only or the downloaded-sidecar boundary.
     */
    fun cachedResolution(
        query: LyricQuery,
        mode: LyricSourceMode,
        preferLocal: Boolean = false,
    ): LyricResolution? = memoryCache.value[
        LyricMemoryCacheKey(
            item = query.itemId.toString(),
            durationMs = query.durationMs,
            mode = mode,
            preferLocal = preferLocal,
        )
    ]

    private fun remember(
        key: LyricMemoryCacheKey,
        resolution: LyricResolution,
    ) {
        if (resolution !is LyricResolution.Found && resolution !is LyricResolution.Untimed) return
        memoryCache.update { current ->
            val next = LinkedHashMap<LyricMemoryCacheKey, LyricResolution>(
                minOf(current.size + 1, MEMORY_CACHE_MAX_ENTRIES),
            )
            current.forEach { (existingKey, existingResolution) ->
                if (existingKey != key) next[existingKey] = existingResolution
            }
            while (next.size >= MEMORY_CACHE_MAX_ENTRIES) {
                val oldestKey = next.keys.firstOrNull() ?: break
                next.remove(oldestKey)
            }
            next[key] = resolution
            next
        }
    }

    suspend fun cache(
        query: LyricQuery,
        mode: LyricSourceMode,
    ): LyricCacheStatus {
        var status = LyricCacheStatus.NoLyric
        resolve(query, mode).collect { resolution ->
            status = when (resolution) {
                is LyricResolution.Found -> LyricCacheStatus.Saved
                is LyricResolution.Untimed -> LyricCacheStatus.Saved
                LyricResolution.Empty -> LyricCacheStatus.NoLyric
                is LyricResolution.Error -> LyricCacheStatus.Failed
            }
        }
        return status
    }

    /** Collects the complete provider flow and returns its latest durable candidate. */
    suspend fun resolveLatest(
        query: LyricQuery,
        mode: LyricSourceMode,
        preferLocal: Boolean = false,
    ): LyricResolution {
        var latest: LyricResolution = LyricResolution.Empty
        resolve(query, mode, preferLocal).collect { resolution ->
            latest = resolution
        }
        return latest
    }

    private companion object {
        const val MEMORY_CACHE_MAX_ENTRIES = 24
    }
}

/**
 * Whether [this] is a strict capability upgrade over [current].
 *
 * [LyricCapabilityLevel] is declared in ascending capability order, so the ordinal is the
 * ranking. Only a strict improvement is allowed to replace what the user is already reading:
 * re-emitting an equal-ranked document would restart the lyric surface for no visible gain.
 */
private fun LyricDocument.outranks(current: LyricDocument?): Boolean =
    current == null || capabilityLevel.ordinal > current.capabilityLevel.ordinal

private data class LyricMemoryCacheKey(
    /** The provider-qualified id, so two providers' tracks never share an entry. */
    val item: String,
    val durationMs: Long,
    val mode: LyricSourceMode,
    val preferLocal: Boolean,
)

/**
 * The "backend" source: the lyrics of the service the track came from.
 *
 * NetEase's come from the configured NeteaseCloudMusicApi server through the SQLDelight cache.
 * Another provider's come from that provider once per request and are never cached, because the
 * cache tables are keyed by a NetEase song id (AGENTS.md D6).
 */
internal class BackendLyricProvider(
    private val lyricFlow: (Long) -> Flow<Lyric?>,
    private val retryDelayMillis: Long = BACKEND_RETRY_DELAY_MILLIS,
    private val foreignLyric: (suspend (ProviderItemId) -> Result<ProviderLyric?>)? = null,
) : LyricSourceProvider {
    override val source = LyricSource.NCM_BACKEND

    override fun load(query: LyricQuery): Flow<LyricProviderResult> = flow {
        if (!query.isNetease) {
            emit(loadForeign(query))
            return@flow
        }
        var lastFailureReason: String? = null
        repeat(BACKEND_MAX_ATTEMPTS) { attempt ->
            var emitted = false
            try {
                lyricFlow(query.songId).collect { lyric ->
                    emitted = true
                    val document = lyric?.toDocument(query)
                    emit(
                        if (document == null) {
                            LyricProviderResult.NoMatch
                        } else if (document.capabilityLevel == LyricCapabilityLevel.UNSYNCED) {
                            LyricProviderResult.Untimed(document)
                        } else {
                            LyricProviderResult.Found(document)
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                lastFailureReason = failure.message
            }
            if (emitted) return@flow
            if (attempt < BACKEND_MAX_ATTEMPTS - 1) {
                delay(retryDelayMillis)
            }
        }
        emit(
            LyricProviderResult.Unavailable(
                lastFailureReason?.takeIf(String::isNotBlank)
                    ?: "网易云音乐歌词请求失败",
            ),
        )
    }

    private suspend fun loadForeign(query: LyricQuery): LyricProviderResult {
        val answer = foreignLyric?.invoke(query.itemId) ?: return LyricProviderResult.NoMatch
        return answer.fold(
            onSuccess = { lyric ->
                val document = lyric?.let {
                    backendLyricDocument(
                        query = query,
                        lrcText = it.plain.orEmpty(),
                        yrcText = it.wordByWord.orEmpty(),
                        translatedText = it.translation.orEmpty(),
                        romanText = it.romanization.orEmpty(),
                        endpoint = providerLyricEndpoint(query.itemId.provider),
                    )
                }
                when {
                    document == null -> LyricProviderResult.NoMatch
                    document.capabilityLevel == LyricCapabilityLevel.UNSYNCED ->
                        LyricProviderResult.Untimed(document)
                    else -> LyricProviderResult.Found(document)
                }
            },
            onFailure = { failure ->
                LyricProviderResult.Unavailable(
                    failure.message?.takeIf(String::isNotBlank)
                        ?: "${query.itemId.provider.displayName}歌词请求失败",
                )
            },
        )
    }

    private companion object {
        const val BACKEND_MAX_ATTEMPTS = 4
        const val BACKEND_RETRY_DELAY_MILLIS = 2_000L
    }
}

/**
 * The endpoint a provider's own lyrics are labelled with, so the lyric badge can name the service
 * rather than "网易云音乐歌词".
 */
internal fun providerLyricEndpoint(provider: MusicProviderId): String = "provider:${provider.key}"

internal class TtmlLyricProvider(
    private val repository: Repository,
    private val amlldbApi: AmlldbApi,
) : LyricSourceProvider {
    override val source = LyricSource.AMLL_TTML

    override fun load(query: LyricQuery): Flow<LyricProviderResult> = flow {
        // The AMLL TTML database is indexed by NetEase song id: another provider's track is never
        // looked up, so no id of it leaves the app.
        if (!query.isNetease) {
            emit(LyricProviderResult.NoMatch)
            return@flow
        }
        val cached = repository.cachedExternalTtml(query.songId)
        if (cached != null) {
            val cachedDocument = cached.toDocumentOrNull()
            if (cachedDocument != null) {
                emit(LyricProviderResult.Found(cachedDocument))
                if (cached.isFresh()) return@flow
            } else {
                try {
                    repository.clearExternalTtml(query.songId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A damaged entry must not prevent the bounded network refresh.
                }
            }
        }

        val lookup = withTimeoutOrNull(TTML_LOOKUP_TIMEOUT_MILLIS) {
            amlldbApi.findByNcmId(query.songId)
        } ?: AmlldbTtmlResult.Unavailable("AMLL DB 查询超时")
        when (val result = lookup) {
            is AmlldbTtmlResult.Found -> {
                val lines = runCatching { TtmlLyricParser.parse(result.ttml) }.getOrNull()
                if (lines == null || !lines.hasPrimaryTimedLine()) {
                    emit(LyricProviderResult.Malformed("AMLL DB 的 TTML 无法解析"))
                } else {
                    try {
                        repository.cacheExternalTtml(
                            query.songId,
                            CachedExternalTtml(
                                content = result.ttml,
                                providerItemId = result.providerItemId,
                                endpoint = result.endpoint,
                                fetchedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A cache write failure must not hide a valid live lyric result.
                    }
                    emit(
                        LyricProviderResult.Found(
                            ttmlDocument(
                                ttml = result.ttml,
                                lines = lines,
                                providerItemId = result.providerItemId,
                                endpoint = result.endpoint,
                            ),
                        ),
                    )
                }
            }
            AmlldbTtmlResult.NoMatch -> emit(LyricProviderResult.NoMatch)
            is AmlldbTtmlResult.Malformed ->
                emit(LyricProviderResult.Malformed(result.reason))
            is AmlldbTtmlResult.Unavailable ->
                emit(LyricProviderResult.Unavailable(result.reason))
        }
    }

    private fun CachedExternalTtml.toDocumentOrNull(): LyricDocument? {
        val lines = runCatching { TtmlLyricParser.parse(content) }.getOrNull()
            ?.takeIf { it.hasPrimaryTimedLine() }
            ?: return null
        return ttmlDocument(
            ttml = content,
            lines = lines,
            providerItemId = providerItemId,
            endpoint = endpoint,
        )
    }

    private fun CachedExternalTtml.isFresh(): Boolean {
        val age = Clock.System.now().toEpochMilliseconds() - fetchedAtEpochMillis
        return age in 0..TTML_CACHE_TTL_MILLIS
    }

    private companion object {
        const val TTML_LOOKUP_TIMEOUT_MILLIS = 12_000L
        const val TTML_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

internal fun ttmlDocument(
    ttml: String,
    lines: List<LyricParser.WordLine>,
    providerItemId: String,
    endpoint: String,
): LyricDocument = LyricDocument(
    source = LyricSource.AMLL_TTML,
    capabilityLevel = LyricCapabilityLevel.TTML_FULL,
    lines = lines,
    rawTtml = ttml,
    rawLineLyric = LyricParser.toLrcText(lines),
    rawTranslatedLyric = lines.toSupplementLrc { it.translatedLyric },
    rawRomanLyric = lines.toSupplementLrc { it.romanLyric },
    providerItemId = providerItemId,
    endpoint = endpoint,
)

private fun Lyric.toDocument(query: LyricQuery): LyricDocument? =
    backendLyricDocument(
        query = query,
        lrcText = lrc?.lyric.orEmpty(),
        yrcText = yrc?.lyric.orEmpty(),
        translatedText = tlyric?.lyric.orEmpty(),
        romanText = romalrc?.lyric.orEmpty(),
    )

internal fun localTtmlDocument(ttml: String): LyricDocument? {
    val lines = runCatching { TtmlLyricParser.parse(ttml) }.getOrNull()
        ?.takeIf { it.hasPrimaryTimedLine() }
        ?: return null
    return ttmlDocument(
        ttml = ttml,
        lines = lines,
        providerItemId = "",
        endpoint = "local-sidecar",
    )
}

internal fun backendLyricDocument(
    query: LyricQuery,
    lrcText: String,
    yrcText: String,
    translatedText: String,
    romanText: String,
    endpoint: String = "configured-ncm-backend",
): LyricDocument? {
    val normalizedLrcText = lrcText.takeIf(String::isNotBlank)
    val normalizedYrcText = yrcText.takeIf(String::isNotBlank)
    val normalizedTranslatedText = translatedText.takeIf(String::isNotBlank).orEmpty()
    val normalizedRomanText = romanText.takeIf(String::isNotBlank).orEmpty()

    val wordLines = normalizedYrcText
        ?.let { runCatching { LyricParser.parseYrc(it) }.getOrDefault(emptyList()) }
        .orEmpty()
    val parsedLrcLines = normalizedLrcText
        ?.let { runCatching { LyricParser.parseLrcLines(it) }.getOrDefault(emptyList()) }
        .orEmpty()
        .withBoundedFinalLrcLine(query.durationMs)
    val baseLines = if (wordLines.isNotEmpty()) {
        wordLines
    } else {
        parsedLrcLines
    }
    if (baseLines.isEmpty()) {
        val untimedLines = normalizedLrcText
            ?.let(::extractUntimedPrimaryLines)
            .orEmpty()
        if (untimedLines.isEmpty()) return null
        return LyricDocument(
            source = LyricSource.NCM_BACKEND,
            capabilityLevel = LyricCapabilityLevel.UNSYNCED,
            lines = emptyList(),
            untimedLines = untimedLines,
            rawLineLyric = normalizedLrcText.orEmpty(),
            rawWordLyric = normalizedYrcText.orEmpty(),
            rawTranslatedLyric = normalizedTranslatedText,
            rawRomanLyric = normalizedRomanText,
            providerItemId = query.itemId.mediaId,
            endpoint = endpoint,
        )
    }

    val lines = baseLines.attachSupplements(
        translations = normalizedTranslatedText,
        romanizations = normalizedRomanText,
    )
    return LyricDocument(
        source = LyricSource.NCM_BACKEND,
        capabilityLevel = if (wordLines.isNotEmpty()) {
            LyricCapabilityLevel.NCM_YRC
        } else {
            LyricCapabilityLevel.LINE_SYNCED
        },
        lines = lines,
        rawLineLyric = normalizedLrcText ?: LyricParser.toLrcText(lines),
        rawWordLyric = normalizedYrcText.orEmpty(),
        rawTranslatedLyric = normalizedTranslatedText,
        rawRomanLyric = normalizedRomanText,
        providerItemId = query.itemId.mediaId,
        endpoint = endpoint,
    )
}

internal fun extractUntimedPrimaryLines(text: String): List<String> =
    text.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { line ->
            val withoutTags = line.replace(LRC_BRACKET_TAG, "").trim()
            when {
                withoutTags.isNotEmpty() -> withoutTags
                !line.startsWith("[") -> line
                else -> null
            }
        }
        .toList()

private val LRC_BRACKET_TAG = Regex("""\[[^\]]*]""")

/**
 * `parseLrcLines()` deliberately keeps duplicate timestamps and background-vocal metadata.
 * Its final line uses [LyricParser.MAX_LRC_TIMESTAMP_MS] because plain LRC has no explicit end;
 * when the backend supplied a usable song duration, retain the ordered lines and only replace
 * that open-ended boundary.
 */
private fun List<LyricParser.WordLine>.withBoundedFinalLrcLine(
    durationMs: Long,
): List<LyricParser.WordLine> {
    if (isEmpty()) return this
    val finalLine = last()
    if (
        finalLine.endTimeMs != LyricParser.MAX_LRC_TIMESTAMP_MS ||
        durationMs <= finalLine.startTimeMs
    ) {
        return this
    }
    val boundedWords = finalLine.words.mapIndexed { index, word ->
        if (index == finalLine.words.lastIndex) {
            word.copy(
                endTimeMs = durationMs,
                exactEndTimeMs = durationMs.toDouble(),
            )
        } else {
            word
        }
    }
    return dropLast(1) + finalLine.copy(
        endTimeMs = durationMs,
        words = boundedWords,
        exactEndTimeMs = durationMs.toDouble(),
    )
}

private fun List<LyricParser.WordLine>.attachSupplements(
    translations: String,
    romanizations: String,
): List<LyricParser.WordLine> {
    val translationMap = parseSupplement(translations)
    val romanMap = parseSupplement(romanizations)
    return map { line ->
        line.copy(
            translatedLyric = translationMap.nearest(line.startTimeMs),
            romanLyric = romanMap.nearest(line.startTimeMs),
        )
    }
}

private fun parseSupplement(text: String): List<Pair<Long, String>> =
    runCatching { LyricParser.parse(text) }
        .getOrDefault(linkedMapOf())
        .mapNotNull { (time, value) ->
            val key = time ?: return@mapNotNull null
            val lyric = value?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            key to lyric
        }

private fun List<Pair<Long, String>>.nearest(timeMs: Long): String =
    minByOrNull { (time, _) -> kotlin.math.abs(time - timeMs) }
        ?.takeIf { (time, _) -> kotlin.math.abs(time - timeMs) <= 850L }
        ?.second
        .orEmpty()

private fun List<LyricParser.WordLine>.toSupplementLrc(
    text: (LyricParser.WordLine) -> String,
): String = filterNot { it.isBackground }
    .mapNotNull { line ->
        text(line).takeIf(String::isNotBlank)?.let { value ->
            "${LyricParser.formatLrcTimestamp(line.startTimeMs)}$value"
        }
    }
    .joinToString("\n")
