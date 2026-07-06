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
 * The base URL is fixed at creation time (changing the server still needs a client rebuild /
 * relaunch), but the cookie is read **fresh per request** via [cookieProvider], so logging in
 * (which writes the cookie into settings) takes effect immediately on the next request without a
 * relaunch.
 */
object HttpClientFactory {

    fun create(
        baseUrl: String,
        realIP: String,
        cookieProvider: () -> String,
        engineFactory: HttpClientEngineFactory<*>,
    ): HttpClient {
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
                // 绝不把会话 Cookie（账号凭证）打进日志——只留占位，避免凭证泄漏到 logcat/控制台
                sanitizeHeader { header -> header == HttpHeaders.Cookie }
            }
            // 宽松超时：直连（不走系统代理）时 TCP 首次握手实测可达 3s+ 且有丢包重传，
            // CIO 默认 connect 超时太紧会导致零星 ConnectTimeout（如 /lyric 反复失败）。
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
            defaultRequest {
                url(baseUrl)
                contentType(ContentType.Application.Json)
                // defaultRequest's receiver is DefaultRequestBuilder, not HttpRequestBuilder,
                // so default query params are appended via url.parameters (merged into every request).
                url.parameters.append("realIP", realIP)
                url.parameters.append("timestamp", getTimeMillis().toString())
                // 每请求现取 cookie：登录写入 settings 后无需重启即刻生效
                val cleanedCookie = cleanCookie(cookieProvider())
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
