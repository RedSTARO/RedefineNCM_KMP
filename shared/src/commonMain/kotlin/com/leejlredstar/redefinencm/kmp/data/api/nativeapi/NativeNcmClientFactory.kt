package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.api.NcmCredentialCookieAttribute
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.platformNcmCrypto
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

/** Native targets use this factory; the browser deliberately retains HttpClientFactory's server. */
internal object NativeNcmClientFactory {
    fun create(settings: PlatformSettings, engineFactory: HttpClientEngineFactory<*>): HttpClient =
        create(SettingsNcmSessionStore(settings), engineFactory)

    internal fun create(store: NcmSessionStore, engineFactory: HttpClientEngineFactory<*>): HttpClient {
        val remote = HttpClient(engineFactory) {
            // Never forward credentials to a Location chosen by an upstream error/redirect.
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }
        val crypto = platformNcmCrypto()
        val wire = KtorNcmWire(remote)
        val protocol = NcmProtocolClient(wire, store, crypto)
        val dispatcher = NativeNcmDispatcher(protocol, wire, crypto, onClose = remote::close)
        val nativeFactory = object : HttpClientEngineFactory<HttpClientEngineConfig> {
            override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
                NativeNcmEngine(dispatcher).also { it.config.apply(block) }
        }
        // Use an engine factory, not the shared-engine overload: client.close() must also close
        // the protocol engine and its real network connection pool.
        return HttpClient(nativeFactory) {
            install(ContentNegotiation) { json(ApiJson) }
            install(HttpTimeout) { requestTimeoutMillis = 60_000 }
            // Freeze one credential at request creation, including explicit reporting snapshots.
            // It is an in-process attribute, never an HTTP cookie sent to native-ncm.invalid.
            install(createClientPlugin("NativeNcmCredential") {
                onRequest { request, _ ->
                    val cookie = request.attributes.getOrNull(NcmCredentialCookieAttribute)
                        ?: request.url.parameters["cookie"]
                        ?: store.credential()
                    if (!request.attributes.contains(NcmCredentialCookieAttribute)) {
                        request.attributes.put(NcmCredentialCookieAttribute, HttpClientFactory.cleanCookie(cookie))
                    }
                    request.headers.remove(HttpHeaders.Cookie)
                    request.url.parameters.remove("cookie")
                }
            })
            install(io.ktor.client.plugins.DefaultRequest) {
                url("https://$NATIVE_NCM_HOST/")
            }
            expectSuccess = false
        }
    }
}
