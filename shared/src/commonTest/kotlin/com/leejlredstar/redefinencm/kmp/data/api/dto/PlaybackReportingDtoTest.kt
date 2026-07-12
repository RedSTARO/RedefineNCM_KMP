package com.leejlredstar.redefinencm.kmp.data.api.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PlaybackReportingDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesScrobbleSuccessDetailsWithoutFixingTheirShape() {
        val response = json.decodeFromString<ScrobbleV1Response>(
            """
            {
              "code": 200,
              "data": "scrobble_v1 上报成功",
              "details": {
                "plv": {"fileName": "PLV.log", "payloadSize": 123},
                "pld": {"fileName": "PLD.log", "payloadSize": 456}
              }
            }
            """.trimIndent(),
        )

        assertEquals(200, response.code)
        assertEquals("scrobble_v1 上报成功", response.data)
        assertEquals(
            123,
            response.details?.jsonObject
                ?.get("plv")
                ?.jsonObject
                ?.get("payloadSize")
                ?.jsonPrimitive
                ?.content
                ?.toInt(),
        )
        assertNull(response.msg)
    }

    @Test
    fun decodesScrobbleFailureWithArbitraryUpstreamDetails() {
        val response = json.decodeFromString<ScrobbleV1Response>(
            """{"code":-1,"msg":"PLV 上报失败","details":[{"rate":0.5}]}""",
        )

        assertEquals(-1, response.code)
        assertEquals("PLV 上报失败", response.msg)
        assertEquals("[{\"rate\":0.5}]", response.details.toString())
    }

    @Test
    fun decodesRelayDataAndBothKnownErrorMessageFields() {
        val success = json.decodeFromString<PlayStateSubmitResponse>(
            """{"code":200,"data":{"accepted":true,"opaque":[1,2,3]}}""",
        )
        val error = json.decodeFromString<PlayStateSubmitResponse>(
            """{"code":301,"msg":"需要登录","message":"upstream login required"}""",
        )

        assertEquals(200, success.code)
        assertIs<JsonObject>(success.data)
        assertEquals(
            "true",
            success.data.jsonObject["accepted"]?.jsonPrimitive?.content,
        )
        assertEquals("需要登录", error.msg)
        assertEquals("upstream login required", error.message)
        assertNull(error.data)
    }
}
