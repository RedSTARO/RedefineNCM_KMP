package com.leejlredstar.redefinencm.kmp.data.api.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioMatchDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesLegacySongFieldNamesFromMatchedResponse() {
        val response = json.decodeFromString<AudioMatch>(
            """
            {
              "code": 200,
              "data": {
                "type": 1,
                "queryId": "query-1",
                "result": [{
                  "startTime": 1234,
                  "song": {
                    "id": 42,
                    "name": "Matched song",
                    "artists": [{"id": 7, "name": "Artist"}],
                    "album": {"id": 8, "name": "Album", "picUrl": "https://example.test/a.jpg"},
                    "duration": 180000,
                    "mvid": 9,
                    "fee": 1
                  }
                }],
                "noMatchReason": 0
              }
            }
            """.trimIndent(),
        )

        val result = response.data?.result?.single()
        assertEquals(200, response.code)
        assertEquals("query-1", response.data?.queryId)
        assertEquals(1_234L, result?.startTime)
        assertEquals(42L, result?.song?.id)
        assertEquals("Artist", result?.song?.artists?.single()?.name)
        assertEquals("Album", result?.song?.album?.name)
        assertEquals(180_000L, result?.song?.duration)
        assertEquals(9L, result?.song?.mvid)
    }

    @Test
    fun decodesValidNoMatchAndProtocolFailureSeparately() {
        val noMatch = json.decodeFromString<AudioMatch>(
            """{"code":200,"data":{"type":0,"queryId":"q","result":null,"noMatchReason":10}}""",
        )
        val missingData = json.decodeFromString<AudioMatch>("""{"code":200}""")

        assertEquals(0, noMatch.data?.type)
        assertNull(noMatch.data?.result)
        assertEquals(10, noMatch.data?.noMatchReason)
        assertNull(missingData.data)
    }

    @Test
    fun missingMatchTypeDoesNotDefaultToNoMatch() {
        val malformed = json.decodeFromString<AudioMatch>(
            """{"code":200,"data":{"queryId":"q","result":null}}""",
        )

        assertEquals(-1, malformed.data?.type)
    }
}
