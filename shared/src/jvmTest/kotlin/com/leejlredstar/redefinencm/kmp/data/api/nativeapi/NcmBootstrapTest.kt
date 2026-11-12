package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.NcmProtocolCrypto
import com.leejlredstar.redefinencm.kmp.data.api.nativecrypto.platformNcmCrypto
import io.ktor.http.HttpMethod
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NcmBootstrapTest {
    private val crypto = platformNcmCrypto()
    private val protocolCrypto = NcmProtocolCrypto(crypto)
    private val now = 1_700_000_000_000L
    private val keyBody = """{"publicKey":"3p7bfXt9wbTTW2HC7OQ1Nz+DQ8hbeGdNrfx+FG+IK08=","version":"3","sk":"server-token|test"}"""
    private class Store : NcmSessionStore {
        val values = mutableMapOf<String, String>()
        override suspend fun read(key: String) = values[key].orEmpty()
        override suspend fun write(key: String, value: String) { values[key] = value }
        override suspend fun credential() = ""
    }
    private fun registration(form: ByteArray): NcmWireResponse {
        val query = parseQueryString(form.decodeToString())
        val nonce = query["nonce"]!!
        val timestamp = query["timestamp"]!!.toLong()
        assertEquals(protocolCrypto.xeapiSign(timestamp, nonce), query["signature"])
        val ciphertext = crypto.aesEcbEncrypt(keyBody.encodeToByteArray(),
            "ab1d5a430f6bb04a3f01e81ddd72bd916d5ce591248ac128714806d7f8fb1b84".chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        return response(buildJsonObject {
            put("code", 200)
            put("data", buildJsonObject {
                put("timestamp", timestamp)
                put("signature", protocolCrypto.xeapiSign(timestamp, nonce))
                put("encryptedData", Base64.Default.encode(ciphertext))
            })
        }.toString())
    }
    private fun response(json: String, cookies: List<String> = emptyList()) = NcmWireResponse(
        200, json.encodeToByteArray(), mapOf("Set-Cookie" to cookies),
    )

    @Test
    fun parallelAnonymousRequestsShareDeviceKeyAndRegistration() = runBlocking {
        val urls = mutableListOf<String>()
        val store = Store()
        val wire = object : NcmWire {
            override suspend fun request(url: String, method: HttpMethod, headers: Map<String, String>, body: ByteArray?): NcmWireResponse {
                urls += url
                delay(1)
                return when {
                    url.endsWith("/api/bsr/sk/get") -> registration(body!!)
                    url.endsWith("/xeapi/register/anonimous") -> response("""{"code":200}""", listOf("MUSIC_A=anonymous-token; Path=/; HttpOnly"))
                    url.endsWith("/eapi/cloudsearch/pc") -> response("""{"code":200}""")
                    else -> error("Unexpected request")
                }
            }
        }
        val protocol = NcmProtocolClient(wire, store, crypto, clock = { now })
        (1..8).map { async { protocol.execute(NcmEndpointMapper.map("cloudsearch", mapOf("keywords" to "a")), "") } }.awaitAll()
        assertEquals(1, urls.count { it.endsWith("/api/bsr/sk/get") })
        assertEquals(1, urls.count { it.endsWith("/xeapi/register/anonimous") })
        assertEquals(8, urls.count { it.endsWith("/eapi/cloudsearch/pc") })
        assertEquals(52, store.values.getValue(NCM_DEVICE_ID_KEY).length)
        assertTrue(store.values.getValue(NCM_ANONYMOUS_KEY).contains("MUSIC_A=anonymous-token"))
        assertTrue(store.values.getValue(NCM_XEAPI_KEY).contains("nativeFetchedAt"))
    }

    @Test
    fun invalidSignatureIsNeverCachedOrUsed() = runBlocking {
        val store = Store()
        var count = 0
        val wire = object : NcmWire {
            override suspend fun request(url: String, method: HttpMethod, headers: Map<String, String>, body: ByteArray?): NcmWireResponse {
                count++
                assertTrue(url.endsWith("/api/bsr/sk/get"))
                return response("""{"code":200,"data":{"timestamp":1700000000000,"signature":"invalid","encryptedData":"bad"}}""")
            }
        }
        val protocol = NcmProtocolClient(wire, store, crypto, clock = { now })
        assertFailsWith<IllegalArgumentException> {
            protocol.execute(NcmEndpointMapper.map("song/url/v1", mapOf("id" to "42", "level" to "standard")), "MUSIC_U=test")
        }
        assertEquals(1, count)
        assertTrue(store.read(NCM_XEAPI_KEY).isEmpty())
    }

    @Test
    fun ciphertextBeginningWithJsonBraceIsStillDecrypted() = runBlocking {
        var calls = 0
        val wire = object : NcmWire {
            override suspend fun request(url: String, method: HttpMethod, headers: Map<String, String>, body: ByteArray?): NcmWireResponse {
                calls++
                return if (url.endsWith("/api/bsr/sk/get")) registration(body!!) else {
                    // Independently generated AES-ECB/eapiKey fixture: {"n":272,"code":200}.
                    val sealed = "7bb21a4d19f571dc7a380771a4dbdc2d5f8520a4669746bfece9e2f84aa4af26"
                        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    assertEquals('{'.code.toByte(), sealed.first())
                    NcmWireResponse(200, sealed)
                }
            }
        }
        val protocol = NcmProtocolClient(wire, Store(), crypto, clock = { now })
        val answer = protocol.execute(NcmEndpointMapper.map("song/url/v1", mapOf("id" to "42", "level" to "standard")), "MUSIC_U=test")
        assertEquals(200, answer.body.jsonObject.getValue("code").jsonPrimitive.intOrNull)
        assertEquals(272, answer.body.jsonObject.getValue("n").jsonPrimitive.intOrNull)
        assertEquals(2, calls)
    }

    @Test
    fun cancelledRefreshDoesNotFallBackToCachedKey(): Unit = runBlocking {
        val store = Store().apply {
            values[NCM_XEAPI_KEY] = keyBody.dropLast(1) + ",\"nativeFetchedAt\":$now}"
        }
        val wire = object : NcmWire {
            override suspend fun request(url: String, method: HttpMethod, headers: Map<String, String>, body: ByteArray?): NcmWireResponse =
                throw CancellationException("cancel bootstrap")
        }
        val protocol = NcmProtocolClient(wire, store, crypto, clock = { now })
        assertFailsWith<CancellationException> {
            protocol.execute(NcmEndpointMapper.map("song/url/v1", mapOf("id" to "42", "level" to "standard")), "MUSIC_U=test")
        }
    }
}
