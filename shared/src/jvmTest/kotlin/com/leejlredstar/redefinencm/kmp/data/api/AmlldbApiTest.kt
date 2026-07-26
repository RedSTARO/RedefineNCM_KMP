package com.leejlredstar.redefinencm.kmp.data.api

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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AmlldbApiTest {
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
