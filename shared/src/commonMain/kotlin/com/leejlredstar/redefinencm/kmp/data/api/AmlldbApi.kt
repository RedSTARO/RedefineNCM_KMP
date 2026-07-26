package com.leejlredstar.redefinencm.kmp.data.api

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface AmlldbTtmlResult {
    data class Found(
        val ttml: String,
        val providerItemId: String,
        val endpoint: String,
    ) : AmlldbTtmlResult

    data object NoMatch : AmlldbTtmlResult
    data class Unavailable(val reason: String) : AmlldbTtmlResult
    data class Malformed(val reason: String) : AmlldbTtmlResult
}

@Serializable
private data class AmlldbSearchRequest(
    val query: String,
    val type: String,
)

@Serializable
private data class AmlldbSearchItem(
    val platform: String = "",
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val ncmIds: List<String> = emptyList(),
    val file: String = "",
)

/**
 * AMLL TTML database lookup.
 *
 * NCM already gives us a stable song ID, so an exact direct lookup is used first. The public
 * search API is only a second exact-ID path; title fuzzy matching is deliberately not used
 * because automatically selecting a similarly named song would display incorrect lyrics.
 */
class AmlldbApi(
    externalHttpClient: ExternalHttpClient,
) {
    private val client = externalHttpClient.client
    private val searchJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun findByNcmId(songId: Long): AmlldbTtmlResult {
        require(songId > 0) { "songId must be positive" }

        var fallbackFailure: AmlldbTtmlResult? = null
        val directLookup = withTimeoutOrNull(DIRECT_LOOKUP_TIMEOUT_MILLIS) {
            getTtml("$DIRECT_NCM_ENDPOINT/$songId")
        } ?: TextFetch.Failed("AMLL DB TTML 直取超时")
        when (val direct = directLookup) {
            is TextFetch.Found -> {
                if (direct.text.looksLikeTtml()) {
                    return AmlldbTtmlResult.Found(
                        ttml = direct.text,
                        providerItemId = "ncm:$songId",
                        endpoint = "amll-ttml-db",
                    )
                }
                fallbackFailure = AmlldbTtmlResult.Malformed("TTML 直取响应不是有效 XML")
            }
            TextFetch.NotFound -> Unit
            is TextFetch.Failed ->
                fallbackFailure = AmlldbTtmlResult.Unavailable(direct.reason)
        }

        val searchFetch = try {
            withTimeoutOrNull(SEARCH_LOOKUP_TIMEOUT_MILLIS) {
                val response = client.post(SEARCH_ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    setBody(AmlldbSearchRequest(query = songId.toString(), type = "id"))
                }
                if (!response.status.isSuccess()) {
                    response.cancelBody("AMLL DB search returned ${response.status.value}")
                    SearchFetch.Failed(
                        fallbackFailure ?: AmlldbTtmlResult.Unavailable(
                            "AMLL DB 搜索返回 HTTP ${response.status.value}",
                        ),
                    )
                } else {
                    when (val boundedBody = response.readBodyTextAtMost(MAX_SEARCH_RESPONSE_BYTES)) {
                        BoundedBody.TooLarge ->
                            SearchFetch.Failed(AmlldbTtmlResult.Malformed("AMLL DB 搜索响应过大"))
                        is BoundedBody.Text -> {
                            val items = try {
                                searchJson.decodeFromString<List<AmlldbSearchItem>>(boundedBody.value)
                            } catch (_: Exception) {
                                return@withTimeoutOrNull SearchFetch.Failed(
                                    AmlldbTtmlResult.Malformed("AMLL DB 搜索响应格式无效"),
                                )
                            }
                            SearchFetch.Items(items.take(MAX_SEARCH_RESULTS))
                        }
                    }
                }
            } ?: SearchFetch.Failed(
                fallbackFailure ?: AmlldbTtmlResult.Unavailable("AMLL DB 搜索超时"),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SearchFetch.Failed(
                fallbackFailure ?: AmlldbTtmlResult.Unavailable("AMLL DB 搜索请求失败"),
            )
        }
        when (searchFetch) {
            is SearchFetch.Failed -> return searchFetch.result
            is SearchFetch.Items -> {
                val exact = searchFetch.items.firstOrNull { item ->
                    item.ncmIds.any { it == songId.toString() } && item.safeFileName() != null
                }
                if (exact == null) {
                    return fallbackFailure ?: AmlldbTtmlResult.NoMatch
                }
                val file = exact.safeFileName()!!
                return downloadSearchResult(file, fallbackFailure)
            }
        }
    }

    private suspend fun downloadSearchResult(
        file: String,
        previousFailure: AmlldbTtmlResult?,
    ): AmlldbTtmlResult {
        var failure = previousFailure
        for ((endpointName, url) in listOf(
            "amlldb-mirror" to "$SEARCH_FILE_ENDPOINT/$file",
            "github-raw" to "$GITHUB_RAW_ENDPOINT/$file",
        )) {
            val fetch = withTimeoutOrNull(FILE_LOOKUP_TIMEOUT_MILLIS) {
                getTtml(url)
            } ?: TextFetch.Failed("AMLL DB 歌词下载超时")
            when (val result = fetch) {
                is TextFetch.Found -> {
                    if (result.text.looksLikeTtml()) {
                        return AmlldbTtmlResult.Found(
                            ttml = result.text,
                            providerItemId = file,
                            endpoint = endpointName,
                        )
                    }
                    failure = AmlldbTtmlResult.Malformed("AMLL DB 歌词文件不是有效 TTML")
                }
                TextFetch.NotFound -> Unit
                is TextFetch.Failed ->
                    failure = AmlldbTtmlResult.Unavailable(result.reason)
            }
        }
        return failure ?: AmlldbTtmlResult.NoMatch
    }

    private suspend fun getTtml(url: String): TextFetch = try {
        val response = client.get(url)
        when {
            response.status == HttpStatusCode.NotFound -> {
                response.cancelBody("AMLL DB returned 404")
                TextFetch.NotFound
            }
            !response.status.isSuccess() -> {
                response.cancelBody("AMLL DB returned ${response.status.value}")
                TextFetch.Failed("AMLL DB 返回 HTTP ${response.status.value}")
            }
            else -> {
                when (val boundedBody = response.readBodyTextAtMost(MAX_TTML_BYTES)) {
                    BoundedBody.TooLarge -> TextFetch.Failed("AMLL DB 歌词文件过大")
                    is BoundedBody.Text -> TextFetch.Found(boundedBody.value)
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        TextFetch.Failed("AMLL DB 请求失败")
    }

    private suspend fun HttpResponse.readBodyTextAtMost(maxBytes: Int): BoundedBody {
        val channel = bodyAsChannel()
        if (headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { it > maxBytes } == true) {
            channel.cancel(IllegalStateException("External response exceeded $maxBytes bytes"))
            return BoundedBody.TooLarge
        }
        val bytes = ByteArray(maxBytes + 1)
        var total = 0
        while (total < bytes.size) {
            val read = channel.readAvailable(bytes, total, bytes.size - total)
            if (read < 0) {
                return BoundedBody.Text(bytes.decodeToString(0, total))
            }
            total += read
        }
        channel.cancel(IllegalStateException("External response exceeded $maxBytes bytes"))
        return BoundedBody.TooLarge
    }

    private suspend fun HttpResponse.cancelBody(reason: String) {
        bodyAsChannel().cancel(IllegalStateException(reason))
    }

    private fun AmlldbSearchItem.safeFileName(): String? {
        val candidate = file.trim()
        return candidate.takeIf {
            it.isNotEmpty() &&
                it.length <= MAX_FILE_NAME_LENGTH &&
                '/' !in it &&
                '\\' !in it &&
                ".." !in it &&
                it.endsWith(".ttml", ignoreCase = true)
        }
    }

    private fun String.looksLikeTtml(): Boolean {
        val prefix = trimStart().take(512)
        return "<!doctype" !in prefix.lowercase() && TTML_ROOT_REGEX.containsMatchIn(prefix)
    }

    private sealed interface TextFetch {
        data class Found(val text: String) : TextFetch
        data object NotFound : TextFetch
        data class Failed(val reason: String) : TextFetch
    }

    private sealed interface SearchFetch {
        data class Items(val items: List<AmlldbSearchItem>) : SearchFetch
        data class Failed(val result: AmlldbTtmlResult) : SearchFetch
    }

    private sealed interface BoundedBody {
        data class Text(val value: String) : BoundedBody
        data object TooLarge : BoundedBody
    }

    private companion object {
        const val DIRECT_NCM_ENDPOINT = "https://amll-ttml-db.stevexmh.net/ncm"
        const val SEARCH_ENDPOINT = "https://amlldb.bikonoo.com/api/search-lyrics"
        const val SEARCH_FILE_ENDPOINT = "https://amlldb.bikonoo.com/raw-lyrics"
        const val GITHUB_RAW_ENDPOINT =
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/raw-lyrics"
        const val MAX_TTML_BYTES = 2 * 1024 * 1024
        const val MAX_SEARCH_RESPONSE_BYTES = 512 * 1024
        const val MAX_SEARCH_RESULTS = 100
        const val MAX_FILE_NAME_LENGTH = 160
        const val DIRECT_LOOKUP_TIMEOUT_MILLIS = 2_500L
        const val SEARCH_LOOKUP_TIMEOUT_MILLIS = 3_000L
        const val FILE_LOOKUP_TIMEOUT_MILLIS = 2_500L
        val TTML_ROOT_REGEX = Regex("""<tt(?:\s|>)""", RegexOption.IGNORE_CASE)
    }
}
