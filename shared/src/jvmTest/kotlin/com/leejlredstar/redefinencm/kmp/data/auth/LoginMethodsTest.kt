package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Each login method maps its backend's answers onto the one [QrLoginPoll] vocabulary the login
 * page understands, and the registry keeps the methods apart by provider.
 */
class LoginMethodsTest {
    private fun jsonClient(handler: (path: String, query: String) -> String): HttpClient =
        HttpClient(
            MockEngine { request ->
                respond(
                    handler(request.url.encodedPath, request.url.encodedQuery),
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(ApiJson) }
        }

    @Test
    fun neteaseQrIssuesACodeAndMapsTheCheckCodes() = runTest {
        var checkCode = 801
        val api = NCMApi(
            jsonClient { path, _ ->
                when {
                    path.endsWith("/login/qr/key") -> """{"code":200,"data":{"unikey":"K1"}}"""
                    // The backend's base64 is not always padded to a multiple of four.
                    path.endsWith("/login/qr/create") ->
                        """{"code":200,"data":{"qrimg":"data:image/png;base64,iVBORw0KGgo","qrurl":""}}"""
                    else -> when (checkCode) {
                        803 -> """{"code":803,"message":"授权登陆成功","cookie":"MUSIC_U=abc; __csrf=x"}"""
                        else -> """{"code":$checkCode,"message":"status $checkCode"}"""
                    }
                }
            },
        )
        val method = NeteaseQrLoginMethod(api)

        val session = method.start()
        assertEquals("K1", session.token)
        assertEquals(listOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()), session.imagePng.take(4))

        assertEquals(QrLoginPoll.Waiting(), method.poll(session))
        checkCode = 802
        assertEquals(QrLoginPoll.Scanned, method.poll(session))
        checkCode = 803
        assertEquals(QrLoginPoll.Confirmed("MUSIC_U=abc; __csrf=x"), method.poll(session))
        checkCode = 800
        assertEquals(QrLoginPoll.Expired, method.poll(session))
        // An unknown code is shown and polling goes on, as the page always did.
        checkCode = 8821
        assertEquals(QrLoginPoll.Waiting("status 8821"), method.poll(session))
    }

    @Test
    fun neteaseQrRefusesToStartWithoutAKeyOrAnImage() = runTest {
        val noKey = NeteaseQrLoginMethod(NCMApi(jsonClient { _, _ -> """{"code":200,"data":{"unikey":""}}""" }))
        assertFailsWith<LoginMethodException> { noKey.start() }

        val noImage = NeteaseQrLoginMethod(
            NCMApi(
                jsonClient { path, _ ->
                    if (path.endsWith("/key")) """{"code":200,"data":{"unikey":"K"}}""" else """{"code":200,"data":{}}"""
                },
            ),
        )
        assertFailsWith<LoginMethodException> { noImage.start() }
    }

    @Test
    fun qqQrMapsTheGatewayEventsAndStoresTheCredentialInCookieForm() = runTest {
        var event = 1
        var withCredential = true
        val api = QQMusicApi(
            jsonClient { path, _ ->
                if (path.endsWith("/status")) {
                    val credential = if (withCredential) {
                        ""","credential":{"musicid":123,"musickey":"Q_H_L_x","refresh_token":"r","refresh_key":"k"}"""
                    } else {
                        ""
                    }
                    """{"code":0,"msg":"ok","data":{"event":$event,"done":false$credential,"identifier":"abc","login_type":"qq"}}"""
                } else {
                    """{"code":0,"msg":"ok","data":{"qr_type":"qq","identifier":"abc","mimetype":"image/png","data":"iVBORw0KGgo=","img":""}}"""
                }
            },
            baseUrl = { "http://localhost:8080" },
        )
        val method = QQQrLoginMethod(api, QQQrLoginMethod.Kind.QQ)
        assertEquals("qq.qr.qq", method.id)
        assertEquals(MusicProviderId.QQ, method.provider)

        val session = method.start()
        assertEquals("abc", session.token)
        assertTrue(session.imagePng.isNotEmpty())

        assertEquals(QrLoginPoll.Waiting(), method.poll(session))
        event = 2
        assertEquals(QrLoginPoll.Scanned, method.poll(session))
        event = 3
        assertEquals(QrLoginPoll.Expired, method.poll(session))
        event = 4
        assertEquals(QrLoginPoll.Refused, method.poll(session))
        event = 0
        assertEquals(
            QrLoginPoll.Confirmed("musicid=123; musickey=Q_H_L_x; refresh_token=r; refresh_key=k"),
            method.poll(session),
        )
        withCredential = false
        assertIs<QrLoginPoll.Failed>(method.poll(session))
        event = -1
        assertIs<QrLoginPoll.Failed>(method.poll(session))
    }

    @Test
    fun qqQrReportsAnUnreachableGatewayAsAMethodFailure() = runTest {
        val api = QQMusicApi(
            HttpClient(MockEngine { respond("", HttpStatusCode.BadGateway) }),
            baseUrl = { "http://localhost:8080" },
        )
        assertFailsWith<LoginMethodException> { QQQrLoginMethod(api, QQQrLoginMethod.Kind.WECHAT).start() }
    }

    @Test
    fun qqQrKeepsWaitingThroughUnansweredPollsUntilTheyRepeat() = runTest {
        // The WeChat status route is a long poll; one unanswered poll is a slow upstream.
        var answer = false
        val api = QQMusicApi(
            jsonClient { path, _ ->
                if (path.endsWith("/status") && !answer) {
                    """{"code":-1,"msg":"upstream timeout"}"""
                } else if (path.endsWith("/status")) {
                    """{"code":0,"msg":"ok","data":{"event":2,"done":false,"identifier":"abc","login_type":"wx"}}"""
                } else {
                    """{"code":0,"msg":"ok","data":{"qr_type":"wx","identifier":"abc","mimetype":"image/png","data":"iVBORw0KGgo=","img":""}}"""
                }
            },
            baseUrl = { "http://localhost:8080" },
        )
        val method = QQQrLoginMethod(api, QQQrLoginMethod.Kind.WECHAT)
        val session = method.start()

        assertIs<QrLoginPoll.Waiting>(method.poll(session))
        assertIs<QrLoginPoll.Waiting>(method.poll(session))
        // An answer in between resets the run.
        answer = true
        assertEquals(QrLoginPoll.Scanned, method.poll(session))
        answer = false
        assertIs<QrLoginPoll.Waiting>(method.poll(session))
        assertIs<QrLoginPoll.Waiting>(method.poll(session))
        assertIs<QrLoginPoll.Failed>(method.poll(session))
    }

    @Test
    fun qqPastedCredentialsAreNormalisedToTheGatewaysCookieForm() {
        val method = QQCredentialTextLoginMethod()
        assertEquals(
            "musicid=123; musickey=abc; refresh_token=r",
            method.normalize("uin=o0123; qm_keyst=abc; psrf_qqrefresh_token=r").getOrThrow(),
        )
        // Empty means signed out, not invalid.
        assertEquals("", method.normalize("   ").getOrThrow())
        assertTrue(method.normalize("garbage").isFailure)
    }

    @Test
    fun neteaseCookieMethodOnlyTrims() {
        assertEquals("MUSIC_U=x", NeteaseCookieLoginMethod().normalize("  MUSIC_U=x \n").getOrThrow())
    }

    @Test
    fun registryGroupsByProviderAndRejectsDuplicateIds() {
        val ncmCookie = NeteaseCookieLoginMethod()
        val qqText = QQCredentialTextLoginMethod()
        val registry = LoginMethodRegistry(listOf(ncmCookie, qqText))

        assertEquals(listOf<LoginMethod>(ncmCookie), registry.forProvider(MusicProviderId.NETEASE))
        assertEquals(qqText, registry.textMethod(MusicProviderId.QQ))
        assertTrue(registry.qrMethods(MusicProviderId.QQ).isEmpty())

        assertFailsWith<IllegalArgumentException> {
            LoginMethodRegistry(listOf(ncmCookie, NeteaseCookieLoginMethod()))
        }
    }
}
