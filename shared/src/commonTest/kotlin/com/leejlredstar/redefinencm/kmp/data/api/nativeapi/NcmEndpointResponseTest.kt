package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NcmEndpointResponseTest {
    @Test
    fun ordinaryResponsesRemainUnchangedIncludingServerErrors() {
        val body = json("""{"code":301,"message":"Session expired","data":null}""")
        assertSame(body, shape(NcmEndpointResponseKind.IDENTITY, body))
    }

    @Test
    fun userDetailUsesTheUpstreamGlobalAvatarKeyReplacement() {
        val body = json(
            """{"code":200,"profile":{"avatarImgId_str":"123"},"nested":[{"avatarImgId_str":"456"}],"marker":"avatarImgId_str"}""",
        )
        assertEquals(
            json("""{"code":200,"profile":{"avatarImgIdStr":"123"},"nested":[{"avatarImgIdStr":"456"}],"marker":"avatarImgIdStr"}"""),
            shape(NcmEndpointResponseKind.USER_DETAIL, body),
        )
    }

    @Test
    fun successfulLoginStatusWrapsTheWholeBodyWithoutInventingAnOuterCode() {
        val body = json("""{"code":200,"account":{"id":42},"profile":{"userId":42}}""")
        assertEquals(
            json("""{"data":{"code":200,"account":{"id":42},"profile":{"userId":42}}}"""),
            shape(NcmEndpointResponseKind.LOGIN_STATUS, body),
        )
    }

    @Test
    fun loginStatusErrorsAreNotRewrappedAsSuccess() {
        val body = json("""{"code":301,"message":"Session expired"}""")
        assertSame(body, shape(NcmEndpointResponseKind.LOGIN_STATUS, body))
        assertSame(JsonNull, shape(NcmEndpointResponseKind.LOGIN_STATUS, JsonNull))
    }

    @Test
    fun qrKeyAlwaysAddsItsServiceEnvelopeAroundTheUpstreamBody() {
        val body = json("""{"code":200,"unikey":"key-1"}""")
        assertEquals(
            json("""{"data":{"code":200,"unikey":"key-1"},"code":200}"""),
            shape(NcmEndpointResponseKind.LOGIN_QR_KEY, body),
        )
    }

    @Test
    fun qrCheckJoinsWholeSetCookieValuesWithSemicolons() {
        val body = json("""{"code":803,"message":"Authorized","cookie":"discard old field"}""")
        assertEquals(
            json("""{"code":803,"message":"Authorized","cookie":"MUSIC_U=token; Path=/; HttpOnly;__csrf=csrf; Path=/; SameSite=None"}"""),
            NcmEndpointMapper.shape(
                NcmEndpointResponseKind.LOGIN_QR_CHECK,
                body,
                listOf("MUSIC_U=token; Path=/; HttpOnly", "__csrf=csrf; Path=/; SameSite=None"),
            ),
        )
    }

    @Test
    fun qrCheckWaitingResponseStillContainsAnEmptyCookieString() {
        val body = json("""{"code":801,"message":"Waiting"}""")
        assertEquals(
            json("""{"code":801,"message":"Waiting","cookie":""}"""),
            shape(NcmEndpointResponseKind.LOGIN_QR_CHECK, body),
        )
    }

    @Test
    fun audioMatchReturnsOnlyTheServiceCodeAndTheInnerData() {
        val body = json("""{"code":0,"message":"upstream","data":{"type":1,"result":[{"score":0.99}]},"extra":true}""")
        assertEquals(
            json("""{"code":200,"data":{"type":1,"result":[{"score":0.99}]}}"""),
            shape(NcmEndpointResponseKind.AUDIO_MATCH, body),
        )
        assertEquals(
            json("""{"code":200,"data":null}"""),
            shape(NcmEndpointResponseKind.AUDIO_MATCH, json("""{"data":null}""")),
        )
        assertEquals(
            json("""{"code":200}"""),
            shape(NcmEndpointResponseKind.AUDIO_MATCH, json("{}")),
        )
    }

    private fun json(text: String): JsonElement = ApiJson.parseToJsonElement(text)

    private fun shape(kind: NcmEndpointResponseKind, body: JsonElement): JsonElement =
        NcmEndpointMapper.shape(kind, body)
}
