package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The renewer keeps an account sent per request alive: it asks about expiry once per credential,
 * renews on a refusal once per credential, and never writes a renewal over an account the user
 * changed while it ran.
 *
 * Real time rather than `runTest`: the renewer bounds its work with `withTimeoutOrNull`, and a
 * virtual clock fires that bound the moment the mock engine hands its answer to another thread.
 */
class QQCredentialRenewerTest {
    private class MemorySlot(var value: String) : ProviderCredentialSlot {
        override val provider = MusicProviderId.QQ
        override suspend fun read(): String = value
        override suspend fun write(credential: String): Result<Unit> {
            value = credential
            return Result.success(Unit)
        }

        override suspend fun replace(expected: String, credential: String): Result<Boolean> {
            if (value != expected) return Result.success(false)
            value = credential
            return Result.success(true)
        }

        override fun credentialUpdates(): Flow<String> = flowOf(value)
    }

    private data class Recorded(val path: String, val cookie: String?)

    private val requests = mutableListOf<Recorded>()

    /**
     * A gateway that answers [expired] to the expiry check, [renewedJson] to a renewal, and
     * refuses a search sent with any cookie in [refusedCookies].
     */
    private fun gateway(
        slot: MemorySlot,
        expired: Boolean = true,
        renewedJson: String? = RENEWED,
        refusedCookies: Set<String> = emptySet(),
        onRenew: () -> Unit = {},
    ): Pair<QQMusicApi, QQCredentialRenewer> {
        lateinit var renewer: QQCredentialRenewer
        val engine = MockEngine { request ->
            val cookie = request.headers[HttpHeaders.Cookie]
            val path = request.url.encodedPath
            requests += Recorded(path, cookie)
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                path.endsWith("/login/check_expired") ->
                    respond("""{"code":0,"msg":"ok","data":$expired}""", HttpStatusCode.OK, json)
                path.endsWith("/login/refresh_credential") -> {
                    onRenew()
                    if (renewedJson == null) {
                        respond("""{"code":-1,"msg":"未授权"}""", HttpStatusCode.Unauthorized, json)
                    } else {
                        respond("""{"code":0,"msg":"ok","data":$renewedJson}""", HttpStatusCode.OK, json)
                    }
                }
                cookie != null && cookie in refusedCookies ->
                    respond("""{"code":-1,"msg":"未授权"}""", HttpStatusCode.Unauthorized, json)
                else -> respond(
                    """{"code":0,"msg":"ok","data":{"total_num":0,"nextpage":0,"song":[]}}""",
                    HttpStatusCode.OK,
                    json,
                )
            }
        }
        val api = QQMusicApi(
            HttpClient(engine),
            baseUrl = { "http://localhost:8080" },
            cookie = { slot.value },
            onRejected = { rejected -> renewer.renewAfterRejection(rejected) },
        )
        renewer = QQCredentialRenewer(api = { api }, slot = slot)
        return api to renewer
    }

    private fun count(pathSuffix: String) = requests.count { it.path.endsWith(pathSuffix) }

    @Test
    fun anExpiredCredentialIsRenewedAndWrittenBack() = runBlocking {
        val slot = MemorySlot(OLD)
        val (_, renewer) = gateway(slot)

        renewer.ensureFresh()

        assertEquals(RENEWED_COOKIE, slot.value)
        // The fresh key is not asked about again.
        renewer.ensureFresh()
        assertEquals(1, count("/login/check_expired"))
    }

    @Test
    fun aRenewalThatRacedASignOutIsDropped() = runBlocking {
        val slot = MemorySlot(OLD)
        // The user signs out while the gateway is renewing the old key.
        val (_, renewer) = gateway(slot, onRenew = { slot.value = "" })

        renewer.ensureFresh()

        assertEquals("", slot.value)
    }

    @Test
    fun anAccountSignedInAfterTheFirstCheckIsCheckedToo() = runBlocking {
        val slot = MemorySlot("")
        val (_, renewer) = gateway(slot, expired = false)

        renewer.ensureFresh()
        assertEquals(0, count("/login/check_expired"))

        slot.value = OLD
        renewer.ensureFresh()
        renewer.ensureFresh()
        assertEquals(1, count("/login/check_expired"))
        assertEquals(OLD, slot.value)
    }

    @Test
    fun aRefusedSignedInRequestIsRenewedAndSentOnceMore() = runBlocking {
        val slot = MemorySlot(OLD)
        val (api, _) = gateway(slot, refusedCookies = setOf(OLD))

        val result = api.search("晴天", 10)

        assertNotNull(result)
        assertEquals(RENEWED_COOKIE, slot.value)
        val searches = requests.filter { it.path.endsWith("/search/search_by_type") }
        assertEquals(listOf(OLD, RENEWED_COOKIE), searches.map { it.cookie })
    }

    @Test
    fun aCredentialIsRenewedOnlyOnceHoweverOftenItIsRefused() = runBlocking {
        val slot = MemorySlot(OLD)
        // The renewal itself is refused, so the stored account never changes.
        val (api, _) = gateway(slot, renewedJson = null, refusedCookies = setOf(OLD))

        assertNull(api.search("晴天", 10))
        assertNull(api.search("晴天", 10))

        assertEquals(1, count("/login/refresh_credential"))
        assertEquals(OLD, slot.value)
    }

    @Test
    fun aRequestRefusedAfterAnotherRenewalRetriesWithTheStoredAccount() = runBlocking {
        val slot = MemorySlot(OLD)
        val (_, renewer) = gateway(slot)

        // The stored account has already moved on from the one the refused request carried.
        slot.value = RENEWED_COOKIE
        assertTrue(renewer.renewAfterRejection(OLD))
        assertEquals(0, count("/login/refresh_credential"))
    }

    private companion object {
        const val OLD = "musicid=1152921504; musickey=Q_H_L_old; refresh_token=rt; refresh_key=rk"
        const val RENEWED =
            """{"musicid":1152921504,"musickey":"Q_H_L_new","refresh_token":"rt2","refresh_key":"rk2"}"""
        const val RENEWED_COOKIE =
            "musicid=1152921504; musickey=Q_H_L_new; refresh_token=rt2; refresh_key=rk2"
    }
}
