package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.data.CachedExternalTtml
import com.leejlredstar.redefinencm.kmp.data.LyricCacheStatus
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.AmlldbApi
import com.leejlredstar.redefinencm.kmp.data.api.AmlldbTtmlResult
import com.leejlredstar.redefinencm.kmp.data.api.dto.Lyric
import com.leejlredstar.redefinencm.kmp.download.LocalMediaAssets
import com.leejlredstar.redefinencm.kmp.util.LyricParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

data class LyricQuery(
    val songId: Long,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
)

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
    constructor(
        repository: Repository,
        amlldbApi: AmlldbApi,
        localMediaAssets: LocalMediaAssets,
    ) : this(
        listOf(
            TtmlLyricProvider(repository, amlldbApi),
            BackendLyricProvider(repository),
        ),
        localMediaAssets::loadLyrics,
    )

    fun resolve(
        query: LyricQuery,
        mode: LyricSourceMode,
        preferLocal: Boolean = false,
    ): Flow<LyricResolution> = flow {
        require(query.songId > 0) { "songId must be positive" }
        val failures = mutableListOf<String>()
        var untimedCandidate: LyricDocument? = null

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
                    } else {
                        emit(LyricResolution.Found(localDocument))
                        return@flow
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
                                emit(LyricResolution.Found(result.document))
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
                failures += "$source 歌词请求失败"
            }
            if (found) return@flow
        }

        val fallbackDocument = untimedCandidate
        if (fallbackDocument != null) {
            emit(LyricResolution.Untimed(fallbackDocument))
        } else if (failures.isEmpty()) {
            emit(LyricResolution.Empty)
        } else {
            emit(LyricResolution.Error(failures.distinct().joinToString("；")))
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
}

internal class BackendLyricProvider(
    private val lyricFlow: (Long) -> Flow<Lyric?>,
    private val retryDelayMillis: Long = BACKEND_RETRY_DELAY_MILLIS,
) : LyricSourceProvider {
    constructor(repository: Repository) : this(repository::getLyric)

    override val source = LyricSource.NCM_BACKEND

    override fun load(query: LyricQuery): Flow<LyricProviderResult> = flow {
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
                    ?: "现有歌词后端请求失败",
            ),
        )
    }

    private companion object {
        const val BACKEND_MAX_ATTEMPTS = 4
        const val BACKEND_RETRY_DELAY_MILLIS = 2_000L
    }
}

private class TtmlLyricProvider(
    private val repository: Repository,
    private val amlldbApi: AmlldbApi,
) : LyricSourceProvider {
    override val source = LyricSource.AMLL_TTML

    override fun load(query: LyricQuery): Flow<LyricProviderResult> = flow {
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

internal fun List<LyricParser.WordLine>.hasPrimaryTimedLine(): Boolean =
    any { line ->
        !line.isBackground &&
            line.text.isNotBlank() &&
            line.endTimeMs >= line.startTimeMs
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
        ?.let { runCatching { LyricParser.parse(it) }.getOrDefault(linkedMapOf()) }
        .orEmpty()
        .entries
        .mapNotNull { (time, text) ->
            val start = time ?: return@mapNotNull null
            val value = text?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            start to value
        }
    val baseLines = if (wordLines.isNotEmpty()) {
        wordLines
    } else {
        parsedLrcLines
            .mapIndexed { index, (start, text) ->
                val end = parsedLrcLines
                    .getOrNull(index + 1)
                    ?.first
                    ?: query.durationMs.takeIf { it > start }
                    ?: start
                LyricParser.WordLine(
                    startTimeMs = start,
                    endTimeMs = end.coerceAtLeast(start),
                    words = listOf(
                        LyricParser.Word(
                            startTimeMs = start,
                            endTimeMs = end.coerceAtLeast(start),
                            text = text,
                        ),
                    ),
                )
            }
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
            providerItemId = query.songId.toString(),
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
        providerItemId = query.songId.toString(),
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
