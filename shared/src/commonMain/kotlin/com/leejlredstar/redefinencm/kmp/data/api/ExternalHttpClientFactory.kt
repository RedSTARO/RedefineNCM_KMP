package com.leejlredstar.redefinencm.kmp.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Credential-free client for public services outside the user-configured NCM backend.
 *
 * This must stay separate from [HttpClientFactory]: that factory intentionally injects the
 * account Cookie, realIP and timestamp into every request.
 */
class ExternalHttpClient(val client: HttpClient)

object ExternalHttpClientFactory {
    fun create(engineFactory: HttpClientEngineFactory<*>): ExternalHttpClient =
        ExternalHttpClient(
            HttpClient(engineFactory) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        coerceInputValues = true
                    })
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    requestTimeoutMillis = 30_000
                    socketTimeoutMillis = 30_000
                }
                expectSuccess = false
            },
        )
}
