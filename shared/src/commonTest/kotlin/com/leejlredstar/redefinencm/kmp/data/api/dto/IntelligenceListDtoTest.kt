package com.leejlredstar.redefinencm.kmp.data.api.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntelligenceListDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesIntelligenceItemsAndModernSongShape() {
        val response = json.decodeFromString<IntelligenceListResponse>(
            """
            {
              "code": 200,
              "data": [
                {
                  "id": 33894312,
                  "alg": "itembased",
                  "recommended": true,
                  "songInfo": {
                    "id": 33894312,
                    "name": "Test song",
                    "ar": [{"id": 7, "name": "Test artist"}],
                    "al": {"id": 8, "name": "Test album", "picUrl": "https://example.test/cover.jpg"},
                    "dt": 245000,
                    "mv": 9,
                    "privilege": {"st": 0}
                  }
                },
                {
                  "id": 36871368,
                  "recommended": false
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(200, response.code)
        val items = requireNotNull(response.data)
        assertEquals(2, items.size)
        assertEquals("itembased", items[0].alg)
        assertTrue(items[0].recommended)
        val song = requireNotNull(items[0].songInfo)
        assertEquals(33_894_312L, song.id)
        assertEquals("Test song", song.name)
        assertEquals("Test artist", song.ar.single().name)
        assertEquals("Test album", song.al.name)
        assertEquals(245_000L, song.dt)
        assertEquals(9L, song.mv)
        assertFalse(items[1].recommended)
        assertNull(items[1].songInfo)
    }

    @Test
    fun decodesLoginFailureWithoutData() {
        val response = json.decodeFromString<IntelligenceListResponse>(
            """{"code":301,"message":null,"msg":"需要登录"}""",
        )

        assertEquals(301, response.code)
        assertNull(response.data)
        assertNull(response.message)
        assertEquals("需要登录", response.msg)
    }

    @Test
    fun decodesLikedPlaylistSpecialType() {
        val response = json.decodeFromString<UserPlaylist>(
            """
            {
              "code": 200,
              "playlist": [
                {"id": 42, "name": "我喜欢的音乐", "specialType": 5}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(5, response.playlist.single().specialType)
    }

    @Test
    fun missingSpecialTypeUsesLegacyDefault() {
        val response = json.decodeFromString<UserPlaylist>(
            """
            {
              "code": 200,
              "playlist": [
                {
                  "id": 42,
                  "name": "测试用户喜欢的音乐",
                  "creator": {"userId": 7}
                }
              ]
            }
            """.trimIndent(),
        )

        val playlist = response.playlist.single()
        assertEquals(0, playlist.specialType)
        assertEquals(7L, playlist.creator.userId)
    }
}
