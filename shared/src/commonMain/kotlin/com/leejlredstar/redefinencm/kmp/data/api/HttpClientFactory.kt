package com.leejlredstar.redefinencm.kmp.data.api

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.date.getTimeMillis
import kotlinx.serialization.json.Json

/**
 * Creates and configures a Ktor HttpClient for the NCM API. The engine is provided by each
 * platform (OkHttp / Darwin / CIO / Js) via [engineFactory].
 *
 * Ported from the original OkHttp interceptor logic in RetrofitInstance:
 * - sets the configurable base URL (without it, `NCMApi`'s relative paths can't resolve),
 * - injects the `realIP` query parameter on every request,
 * - injects a `timestamp` query parameter on every request (cache-buster),
 * - attaches the cleaned auth cookie as a header when present.
 *
 * Deviation from the original: the original skipped the cookie for login paths. Ktor's
 * `defaultRequest` runs before the per-request path is merged, so a path check there is not
 * reliable; instead the cookie is attached whenever it is non-empty (when the user is not logged
 * in it is empty, so login flows are unaffected in practice). Refine with a send-pipeline
 * interceptor if strict per-path skipping is needed.
 *
 * The client is built once (a Koin singleton) with the base URL / cookie read from settings at
 * creation time, matching the original `RetrofitInstance` object. Changing the server or cookie
 * therefore takes effect on next launch (or rebuild the client).
 */
object HttpClientFactory {

    fun create(
        baseUrl: String,
        realIP: String,
        cookie: String,
        engineFactory: HttpClientEngineFactory<*>,
    ): HttpClient {
        val cleanedCookie = cleanCookie(cookie)
        return HttpClient(engineFactory) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.HEADERS
            }
            defaultRequest {
                url(baseUrl)
                contentType(ContentType.Application.Json)
                // defaultRequest's receiver is DefaultRequestBuilder, not HttpRequestBuilder,
                // so default query params are appended via url.parameters (merged into every request).
                url.parameters.append("realIP", realIP)
                url.parameters.append("timestamp", getTimeMillis().toString())
                if (cleanedCookie.isNotEmpty()) {
                    header(HttpHeaders.Cookie, cleanedCookie)
                }
            }
            expectSuccess = false
        }
    }

    /**
     * Normalise a raw cookie to `name=value; name=value` (drop attributes / empty parts).
     * Ported from the original `RetrofitInstance.getCookie()`.
     */
    private fun cleanCookie(raw: String): String =
        raw.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .joinToString("; ")
}

/**
 * Safe API call wrapper — catches exceptions and returns null (no crash propagates to UI).
 * Ported from the original Retrofit `safeApiCall`.
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> T): T? {
    return try {
        apiCall()
    } catch (e: Exception) {
        println("safeApiCall failed: ${e.message}")
        null
    }
}
