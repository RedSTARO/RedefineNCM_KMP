package com.leejlredstar.redefinencm.kmp.data.api.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UserLevelDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesSuccessfulUserLevelResponse() {
        val response = json.decodeFromString<UserLevelResponse>(
            """
            {
              "code": 200,
              "full": false,
              "data": {
                "userId": 135077276,
                "info": "60G音乐云盘免费容量${'$'}黑名单上限80${'$'}价值400云贝",
                "progress": 0.418,
                "nextPlayCount": 2000,
                "nextLoginCount": 100,
                "nowPlayCount": 836,
                "nowLoginCount": 100,
                "level": 7
              }
            }
            """.trimIndent(),
        )

        assertEquals(200, response.code)
        assertFalse(response.full)
        val data = requireNotNull(response.data)
        assertEquals(135_077_276L, data.userId)
        assertEquals("60G音乐云盘免费容量\$黑名单上限80\$价值400云贝", data.info)
        assertEquals(0.418, data.progress)
        assertEquals(2_000L, data.nextPlayCount)
        assertEquals(100L, data.nextLoginCount)
        assertEquals(836L, data.nowPlayCount)
        assertEquals(100L, data.nowLoginCount)
        assertEquals(7, data.level)
    }

    @Test
    fun decodesLoginFailureWithoutLevelData() {
        val response = json.decodeFromString<UserLevelResponse>(
            """{"code":301,"message":null,"msg":"需要登录"}""",
        )

        assertEquals(301, response.code)
        assertFalse(response.full)
        assertNull(response.data)
        assertNull(response.message)
        assertEquals("需要登录", response.msg)
    }
}
