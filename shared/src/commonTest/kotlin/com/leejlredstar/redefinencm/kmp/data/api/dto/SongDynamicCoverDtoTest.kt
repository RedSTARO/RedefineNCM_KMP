package com.leejlredstar.redefinencm.kmp.data.api.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongDynamicCoverDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun resolvesCurrentVideoPlayUrlField() {
        val response = json.decodeFromString<SongDynamicCoverResponse>(
            """{"code":200,"data":{"videoPlayUrl":"https://example.test/cover.mp4","ignored":1}}""",
        )

        assertEquals("https://example.test/cover.mp4", response.resolvedVideoUrl())
    }

    @Test
    fun acceptsCompatibleUrlAliasButRejectsNonHttpSources() {
        assertEquals(
            "http://example.test/cover.mp4",
            SongDynamicCoverResponse(
                code = 200,
                data = SongDynamicCoverData(url = " http://example.test/cover.mp4 "),
            ).resolvedVideoUrl(),
        )
        assertNull(
            SongDynamicCoverResponse(
                code = 200,
                data = SongDynamicCoverData(videoPlayUrl = "javascript:alert(1)"),
            ).resolvedVideoUrl(),
        )
    }
}
