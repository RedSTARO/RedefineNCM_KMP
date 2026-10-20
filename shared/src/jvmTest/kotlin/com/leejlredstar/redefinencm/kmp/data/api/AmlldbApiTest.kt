package com.leejlredstar.redefinencm.kmp.data.api

import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.LanguageSetting
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AmlldbApiTest {
    /** The expected copy below is the Chinese, so the test pins it whatever the machine's language. */
    @BeforeTest
    fun showChinese() {
        I18n.apply(LanguageSetting.ZH)
    }

    @AfterTest
    fun followTheSystemAgain() {
        I18n.apply(LanguageSetting.SYSTEM)
    }

    @Test
    fun rejectsOversizedBodyWithoutRelyingOnContentLength() {
        runBlocking {
            val oversized = ByteArray(2 * 1024 * 1024 + 1) { 'x'.code.toByte() }
            val api = apiWithEngine { requestUrl ->
                if (requestUrl.contains("stevexmh.net")) {
                    respond(
                        content = ByteReadChannel(oversized),
                        status = HttpStatusCode.OK,
                    )
                } else {
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }

            val result = api.findByNcmId(1)

            assertIs<AmlldbTtmlResult.Unavailable>(result)
            assertTrue(result.reason.contains("过大"), result.toString())
        }
    }

    /**
     * A host that keeps sending must be cut off at the limit. client.get() would have saved the
     * whole 64 MiB body in memory before the limit was checked.
     */
    @Test
    fun stopsReadingAnOversizedBodyAtTheLimit() {
        runBlocking {
            val written = AtomicLong()
            val quietScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
            val api = apiWithEngine { requestUrl ->
                if (requestUrl.contains("stevexmh.net")) {
                    val body = quietScope.writer {
                        val chunk = ByteArray(64 * 1024) { 'x'.code.toByte() }
                        repeat(1024) {
                            channel.writeFully(chunk)
                            written.addAndGet(chunk.size.toLong())
                        }
                    }.channel
                    respond(content = body, status = HttpStatusCode.OK)
                } else {
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }

            try {
                val result = api.findByNcmId(1)

                assertIs<AmlldbTtmlResult.Unavailable>(result)
                assertTrue(result.reason.contains("过大"), result.toString())
                assertTrue(written.get() < 8L * 1024 * 1024, "read ${written.get()} bytes of an oversized body")
            } finally {
                quietScope.cancel()
            }
        }
    }

    @Test
    fun acceptsWhitespaceAfterTtmlRootName() {
        runBlocking {
            val api = apiWithEngine {
                respond(
                    content = "<tt\n xmlns=\"http://www.w3.org/ns/ttml\"><body/></tt>",
                    status = HttpStatusCode.OK,
                )
            }

            val result = api.findByNcmId(1)
            assertIs<AmlldbTtmlResult.Found>(result, result.toString())
        }
    }

    private fun apiWithEngine(
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(String) ->
            io.ktor.client.request.HttpResponseData,
    ): AmlldbApi {
        val client = HttpClient(
            MockEngine { request -> handler(request.url.toString()) },
        ) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
            expectSuccess = false
        }
        return AmlldbApi(ExternalHttpClient(client))
    }
}
