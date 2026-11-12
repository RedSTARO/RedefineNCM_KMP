package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.platformNcmCrypto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NcmNativeTransportTest {
    private class Store : NcmSessionStore {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key].orEmpty()
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun credential() = "MUSIC_U=unit+test==; __csrf=csrf"
    }
    private class Wire(val answer: suspend (String, HttpMethod, Map<String, String>, ByteArray?) -> NcmWireResponse) : NcmWire {
        val urls = mutableListOf<String>()
        override suspend fun request(url: String, method: HttpMethod, headers: Map<String, String>, body: ByteArray?): NcmWireResponse {
            urls += url
            return answer(url, method, headers, body)
        }
    }
    private val crypto get() = platformNcmCrypto()
    private fun ok(body: String = """{"code":200}""") = NcmWireResponse(200, body.encodeToByteArray())
    private fun dispatcher(wire: NcmWire, store: Store = Store()) = NativeNcmDispatcher(
        NcmProtocolClient(wire, store, crypto, clock = { 1_700_000_000_000L }), wire, crypto,
    )

    @Test
    fun preservesExistingNcmApiAndNeverCallsAnApiServer() = runBlocking {
        val wire = Wire { url, _, headers, body ->
            assertEquals("https://interfacepc.music.163.com/eapi/cloudsearch/pc", url)
            assertTrue(headers.getValue("Cookie").contains("MUSIC_U=unit%2Btest%3D%3D"))
            assertFalse(headers.containsKey("X-Real-IP"))
            assertFalse(body!!.decodeToString().contains("query words"))
            ok("""{"code":200,"result":{"songs":[],"songCount":0}}""")
        }
        val engine = NativeNcmEngine(dispatcher(wire))
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(ApiJson) }
            install(io.ktor.client.plugins.DefaultRequest) { url("https://$NATIVE_NCM_HOST/") }
        }
        try {
            assertEquals(200, NCMApi(client).search("query words").code)
            assertEquals(1, wire.urls.size)
        } finally { client.close() }
    }

    @Test
    fun credentialSnapshotOverridesTheCurrentAccount() = runBlocking {
        val wire = Wire { _, _, headers, _ ->
            assertTrue(headers.getValue("Cookie").contains("MUSIC_U=bound"))
            assertFalse(headers.getValue("Cookie").contains("unit"))
            ok()
        }
        dispatcher(wire).execute("user/level", emptyMap(), credential = "MUSIC_U=bound")
        assertEquals(1, wire.urls.size)
    }

    @Test
    fun localQrGenerationNeedsNoNetworkOrAccount() = runBlocking {
        val wire = Wire { _, _, _, _ -> error("QR images must be generated locally") }
        val response = dispatcher(wire).execute("login/qr/create", mapOf("key" to "a+b=", "qrimg" to "true"))
        val data = response.body.jsonObject.getValue("data").jsonObject
        assertEquals("https://music.163.com/login?codekey=a%2Bb%3D", data.getValue("qrurl").jsonPrimitive.content)
        assertTrue(data.getValue("qrimg").jsonPrimitive.content.startsWith("data:image/png;base64,iVBOR"))
        assertTrue(wire.urls.isEmpty())
    }

    @Test
    fun unknownRoutesAndArbitraryDestinationsFailClosed() = runBlocking {
        val wire = Wire { _, _, _, _ -> error("Unexpected network access") }
        assertFailsWith<IllegalArgumentException> { dispatcher(wire).execute("anything/unregistered", emptyMap()) }
        assertTrue(isNcmProtocolUrl("https://interface.music.163.com/api/a"))
        assertFalse(isNcmProtocolUrl("https://interface.music.163.com.evil.test/api/a"))
        assertFalse(isNcmProtocolUrl("http://interface.music.163.com/api/a"))
        assertFalse(isNcmProtocolUrl("https://name:secret@interface.music.163.com/api/a"))
        assertFalse(isNcmProtocolUrl("https://interface.music.163.com:8080/api/a"))
    }

    @Test
    fun playlistAllKeepsItsTwoStageContractAndEmptyListsDoNotFetchDetails() = runBlocking {
        var calls = 0
        val wire = Wire { url, _, _, _ ->
            calls++
            when (calls) {
                1 -> { assertTrue(url.endsWith("/eapi/v6/playlist/detail")); ok("""{"code":200,"playlist":{"trackIds":[{"id":1},{"id":2}]}}""") }
                2 -> { assertTrue(url.endsWith("/eapi/v3/song/detail")); ok("""{"code":200,"songs":[{"id":1},{"id":2}],"privileges":[]}""") }
                else -> error("Unexpected playlist request")
            }
        }
        val response = dispatcher(wire).execute("playlist/track/all", mapOf("id" to "42"))
        assertEquals(2, calls)
        assertEquals(200, response.body.jsonObject.getValue("code").jsonPrimitive.intOrNull)
        val emptyWire = Wire { _, _, _, _ -> ok("""{"code":200,"playlist":{"trackIds":[]}}""") }
        val empty = dispatcher(emptyWire).execute("playlist/track/all", mapOf("id" to "42"))
        assertEquals("[]", empty.body.jsonObject.getValue("songs").toString())
        assertEquals(1, emptyWire.urls.size)
    }

    @Test
    fun audioMatchIsCredentialFreeAndEncodesFingerprintOnce() = runBlocking {
        val wire = Wire { url, method, headers, body ->
            assertEquals(HttpMethod.Get, method)
            assertTrue(url.contains("rawdata=a%2Bb%3D"))
            assertTrue(headers.isEmpty())
            assertEquals(null, body)
            ok("""{"data":{"type":0}}""")
        }
        val response = dispatcher(wire).execute("audio/match", mapOf("duration" to "3", "audioFP" to "a+b="))
        assertEquals("0", response.body.jsonObject.getValue("data").jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun cancelledOrAmbiguousWritesAreNeverRetried() = runBlocking {
        val wire = Wire { _, _, _, _ -> throw CancellationException("test cancellation") }
        assertFailsWith<CancellationException> {
            dispatcher(wire).execute("weblog", emptyMap(), ApiJson.parseToJsonElement("""{"data":{"logs":"[]"}}"""))
        }
        assertEquals(1, wire.urls.size)
    }

    @Test
    fun eapiProbesNmtidBeforeGeneratingAFallbackAndAcceptsCodeZero() = runBlocking {
        val cookies = mutableListOf<String>()
        val wire = Wire { _, _, headers, _ ->
            cookies += headers.getValue("Cookie")
            ok("""{"code":0}""")
        }
        val protocol = NcmProtocolClient(wire, Store(), crypto)
        repeat(4) { assertEquals(200, protocol.execute(NcmEndpointMapper.map("cloudsearch", mapOf("keywords" to "a")), "MUSIC_U=test").status) }
        assertTrue(cookies.take(3).none { "NMTID=" in it })
        assertTrue("NMTID=00O" in cookies.last())
        val captured = mutableListOf<String>()
        val realWire = Wire { _, _, headers, _ ->
            captured += headers.getValue("Cookie")
            NcmWireResponse(200, """{"code":200}""".encodeToByteArray(), mapOf("set-cookie" to listOf("NMTID=real; Path=/; HttpOnly")))
        }
        val real = NcmProtocolClient(realWire, Store(), crypto)
        repeat(2) { real.execute(NcmEndpointMapper.map("cloudsearch", mapOf("keywords" to "a")), "MUSIC_U=test") }
        assertFalse("NMTID=" in captured.first())
        assertTrue("NMTID=real" in captured.last())
    }

    @Test
    fun scrobblePostsPlvThenPldAndChecksEachFileAcknowledgment() = runBlocking {
        val records = mutableListOf<String>()
        val wire = Wire { url, _, _, bytes ->
            assertTrue(url.startsWith("https://clientlog3.music.163.com/api/clientlog/encrypt/upload"))
            val text = bytes!!.decodeToString()
            val filename = Regex("filename=\\\"([^\\\"]+)\\\"").find(text)!!.groupValues[1]
            records += filename
            ok("""{"code":200,"data":{"successfiles":["$filename"]}}""")
        }
        val result = dispatcher(wire).execute("scrobble/v1", mapOf("id" to "42", "time" to "60", "total" to "120"))
        assertEquals(200, result.body.jsonObject.getValue("code").jsonPrimitive.intOrNull)
        assertEquals(2, records.size)
        assertTrue(records[0] != records[1])
        val rejected = Wire { _, _, _, _ -> ok("""{"code":200,"data":{"successfiles":[]}}""") }
        val rejection = dispatcher(rejected).execute("scrobble/v1", mapOf("id" to "42", "time" to "60"))
        assertEquals(-1, rejection.body.jsonObject.getValue("code").jsonPrimitive.intOrNull)
        assertEquals(1, rejected.urls.size)
        val partial = Wire { _, _, _, _ -> throw java.io.IOException("ambiguous disconnect") }
        assertFailsWith<java.io.IOException> { dispatcher(partial).execute("scrobble/v1", mapOf("id" to "42", "time" to "60")) }
        assertEquals(1, partial.urls.size)
    }

    @Test
    fun cookieRoundTripPreservesOpaqueCredentialBytes() {
        val cookie = "MUSIC_U=a+b==; __csrf=xyz; Path=/; Secure; HttpOnly; SameSite=None"
        assertEquals("MUSIC_U=a+b==; __csrf=xyz", ncmCookieHeader(ncmCookieMap(cookie)))
        assertFailsWith<IllegalArgumentException> { ncmCookieHeader(mapOf("MUSIC_U" to "a\r\nb")) }
    }
}
