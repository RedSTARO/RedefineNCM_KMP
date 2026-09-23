package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** QQ's accounts-page name: the number at once, the profile's name once the gateway answers. */
class AccountIdentityTest {
    private val requestedPaths = mutableListOf<String>()

    private fun api(): QQMusicApi = QQMusicApi(
        HttpClient(
            MockEngine { request ->
                requestedPaths += request.url.encodedPath
                // The shape the gateway answered with on 2026-09-23.
                respond(
                    """{"code":0,"msg":"ok","data":{"base_info":{"encrypted_uin":"7eEFNeSlNKns",
                       "name":"听歌的人","avatar":"http://y.gtimg.cn/a.jpg","background_image":"",
                       "user_type":0},"singer":{},"is_followed":0,"tab_detail":{}}}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ),
        baseUrl = { "http://localhost:8080" },
    )

    @Test
    fun theNumberShowsFirstAndTheProfileNameReplacesIt() = runBlocking {
        val identities = QQAccountIdentitySource(api())
            .identities(flowOf("musicid=123456; musickey=k; encrypt_uin=7eEFNeSlNKns"))
            .toList()

        assertEquals(
            listOf(
                AccountIdentity(name = "123456"),
                AccountIdentity(name = "听歌的人", avatarUrl = "http://y.gtimg.cn/a.jpg"),
            ),
            identities,
        )
        assertEquals(listOf("/user/7eEFNeSlNKns/homepage"), requestedPaths)
    }

    @Test
    fun withoutAnEncryptedUinTheNumberIsAllThereIs() = runBlocking {
        val identities = QQAccountIdentitySource(api())
            .identities(flowOf("uin=o0123456; qm_keyst=k"))
            .toList()

        assertEquals(listOf(AccountIdentity(name = "123456")), identities)
        assertEquals(emptyList(), requestedPaths)
    }

    @Test
    fun aSignedOutSlotHasNoIdentity() = runBlocking {
        assertEquals(listOf<AccountIdentity?>(null), QQAccountIdentitySource(api()).identities(flowOf("")).toList())
    }
}
