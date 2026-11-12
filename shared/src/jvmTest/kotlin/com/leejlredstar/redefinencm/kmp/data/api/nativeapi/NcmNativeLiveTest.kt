package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.platformNcmCrypto
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in anonymous read-only smoke test; never loads a real account from settings. */
class NcmNativeLiveTest {
    @Test
    fun anonymousReadsUseOnlyNetEaseWithoutAnyApiServer() = runBlocking {
        if (System.getenv("NCM_NATIVE_LIVE_TEST") != "1") return@runBlocking
        val remote = HttpClient(OkHttp) {
            followRedirects = false
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }
        val memory = object : NcmSessionStore {
            val values = mutableMapOf<String, String>()
            override suspend fun read(key: String) = values[key].orEmpty()
            override suspend fun write(key: String, value: String) { values[key] = value }
            override suspend fun credential() = ""
        }
        val crypto = platformNcmCrypto()
        val wire = KtorNcmWire(remote)
        val dispatcher = NativeNcmDispatcher(NcmProtocolClient(wire, memory, crypto), wire, crypto)
        try {
            val search = dispatcher.execute("cloudsearch", mapOf("keywords" to "hello", "limit" to "2"))
            assertEquals(200, search.body.jsonObject.getValue("code").jsonPrimitive.intOrNull)
            val songs = search.body.jsonObject.getValue("result").jsonObject.getValue("songs").jsonArray
            assertTrue(songs.isNotEmpty())
            val song = songs.first().jsonObject.getValue("id").jsonPrimitive.content
            val detail = dispatcher.execute("song/detail", mapOf("ids" to song))
            assertEquals(200, detail.body.jsonObject.getValue("code").jsonPrimitive.intOrNull)
            val lyric = dispatcher.execute("lyric/new", mapOf("id" to song))
            assertEquals(200, lyric.body.jsonObject.getValue("code").jsonPrimitive.intOrNull)
            val url = dispatcher.execute("song/url/v1", mapOf("id" to song, "level" to "standard"))
            assertEquals(200, url.body.jsonObject.getValue("code").jsonPrimitive.intOrNull)
            assertTrue(url.body.jsonObject.getValue("data").jsonArray.isNotEmpty())
            // Deliberately print only operation outcomes, never a signed URL or login state.
            println("Native NetEase anonymous search/detail/lyric/stream resolution: passed")
            val facadeClient = NativeNcmClientFactory.create(memory, OkHttp)
            try {
                val api = NCMApi(facadeClient)
                assertEquals(200, api.search("hello", limit = 1).code)
                val key = api.loginQrKey().data?.unikey
                assertTrue(!key.isNullOrBlank())
                val qr = api.loginQrCreate(key!!, true)
                assertEquals(200, qr.code)
                // Merely poll the newly issued anonymous QR once. No user account signs in.
                assertTrue(api.loginQrCheck(key).code in setOf(800, 801, 802))
                println("Native NetEase facade/QR issue/local image/anonymous poll: passed")
            } finally { facadeClient.close() }
        } finally { remote.close() }
    }
}
