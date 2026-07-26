package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.data.CachedExternalTtml
import com.leejlredstar.redefinencm.kmp.data.LyricCacheStatus
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.AmlldbApi
import com.leejlredstar.redefinencm.kmp.data.api.AmlldbTtmlResult
import com.leejlredstar.redefinencm.kmp.data.api.dto.Lyric
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
    val lines: List<LyricParser.WordLine>,
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
    data object NoMatch : LyricProviderResult
    data class Unavailable(val reason: String) : LyricProviderResult
    data class Malformed(val reason: String) : LyricProviderResult
}

sealed interface LyricResolution {
    data class Found(val document: LyricDocument) : LyricResolution
    data object Empty : LyricResolution
    data class Error(val message: String) : LyricResolution
}

interface LyricSourceProvider {
    val source: LyricSource
    fun load(query: LyricQuery): Flow<LyricProviderResult>
}

class LyricResolver(
    private val providers: List<LyricSourceProvider>,
) {
    constructor(repository: Repository, amlldbApi: AmlldbApi) : this(
        listOf(
            TtmlLyricProvider(repository, amlldbApi),
            BackendLyricProvider(repository),
        ),
    )

    fun resolve(
        query: LyricQuery,
        mode: LyricSourceMode,
    ): Flow<LyricResolution> = flow {
        require(query.songId > 0) { "songId must be positive" }
        val failures = mutableListOf<String>()

        for (source in mode.sourceOrder) {
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
                            found = true
                            emit(LyricResolution.Found(result.document))
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

        if (failures.isEmpty()) {
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
                LyricResolution.Empty -> LyricCacheStatus.NoLyric
                is LyricResolution.Error -> LyricCacheStatus.Failed
            }
        }
        return status
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

private fun ttmlDocument(
    ttml: String,
    lines: List<LyricParser.WordLine>,
    providerItemId: String,
    endpoint: String,
): LyricDocument = LyricDocument(
    source = LyricSource.AMLL_TTML,
    lines = lines,
    rawTtml = ttml,
    rawLineLyric = LyricParser.toLrcText(lines),
    rawTranslatedLyric = lines.toSupplementLrc { it.translatedLyric },
    rawRomanLyric = lines.toSupplementLrc { it.romanLyric },
    providerItemId = providerItemId,
    endpoint = endpoint,
)

private fun Lyric.toDocument(query: LyricQuery): LyricDocument? {
    val lrcText = lrc?.lyric?.takeIf(String::isNotBlank)
    val yrcText = yrc?.lyric?.takeIf(String::isNotBlank)
    val translatedText = tlyric?.lyric?.takeIf(String::isNotBlank).orEmpty()
    val romanText = romalrc?.lyric?.takeIf(String::isNotBlank).orEmpty()

    val wordLines = yrcText
        ?.let { runCatching { LyricParser.parseYrc(it) }.getOrDefault(emptyList()) }
        .orEmpty()
    val parsedLrcLines = lrcText
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
    if (baseLines.isEmpty()) return null

    val lines = baseLines.attachSupplements(
        translations = translatedText,
        romanizations = romanText,
    )
    return LyricDocument(
        source = LyricSource.NCM_BACKEND,
        lines = lines,
        rawLineLyric = lrcText ?: LyricParser.toLrcText(lines),
        rawWordLyric = yrcText.orEmpty(),
        rawTranslatedLyric = translatedText,
        rawRomanLyric = romanText,
        providerItemId = query.songId.toString(),
        endpoint = "configured-ncm-backend",
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
