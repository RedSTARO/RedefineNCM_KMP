package com.leejlredstar.redefinencm.kmp.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaybackReportingApiTest {
    @Test
    fun safeApiCallDoesNotConvertCancellationIntoReportingFailure() = runTest {
        assertFailsWith<CancellationException> {
            safeApiCall<Unit> { throw CancellationException("cancel reporting") }
        }
    }

    @Test
    fun startPlaybackWeblogContainsOnlyTheRecentPlayAction() {
        assertEquals(
            """[{"action":"startplay","json":{"id":518066366,"type":"song","mainsite":"1","mainsiteWeb":"1","content":"id=36780169"}}]""",
            startPlaybackWeblogLogs(songId = 518_066_366, sourceId = 36_780_169),
        )
    }

    @Test
    fun startPlaybackWeblogRejectsInvalidIds() {
        assertFailsWith<IllegalArgumentException> {
            startPlaybackWeblogLogs(songId = 0, sourceId = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            startPlaybackWeblogLogs(songId = 1, sourceId = 0)
        }
    }

    @Test
    fun scrobbleQueryMapsEveryDocumentedParameter() {
        val parameters = scrobbleV1QueryParameters(
            id = 518_066_366,
            timeSeconds = 291,
            sourceId = "36780169",
            source = "playlist",
            name = "Song name",
            artist = "Artist name",
            bitrate = 320,
            level = "exhigh",
            totalSeconds = 300,
        )

        assertEquals(
            listOf(
                "id" to "518066366",
                "time" to "291",
                "sourceid" to "36780169",
                "source" to "playlist",
                "name" to "Song name",
                "artist" to "Artist name",
                "bitrate" to "320",
                "level" to "exhigh",
                "total" to "300",
            ),
            parameters,
        )
    }

    @Test
    fun scrobbleQueryOmitsBlankOrNonPositiveOptionalValues() {
        val parameters = scrobbleV1QueryParameters(
            id = 42,
            timeSeconds = 30,
            sourceId = " ",
            source = "",
            name = null,
            artist = "\t",
            bitrate = 0,
            level = " ",
            totalSeconds = -1,
        )

        assertEquals(listOf("id" to "42", "time" to "30"), parameters)
    }

    @Test
    fun scrobbleQueryRejectsInvalidRequiredValues() {
        assertFailsWith<IllegalArgumentException> {
            scrobbleV1QueryParameters(
                id = 0,
                timeSeconds = 30,
                sourceId = null,
                source = null,
                name = null,
                artist = null,
                bitrate = null,
                level = null,
                totalSeconds = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            scrobbleV1QueryParameters(
                id = 42,
                timeSeconds = 0,
                sourceId = null,
                source = null,
                name = null,
                artist = null,
                bitrate = null,
                level = null,
                totalSeconds = null,
            )
        }
    }

    @Test
    fun playStateQueryMapsTrackedSessionAndPlaybackState() {
        assertEquals(
            listOf(
                "id" to "518066366",
                "sessionId" to "AB12CD34EF56",
                "progress" to "30",
                "playMode" to "list_loop",
                "type" to "song",
            ),
            submitPlayStateQueryParameters(
                id = 518_066_366,
                sessionId = "AB12CD34EF56",
                progressSeconds = 30,
                playMode = "list_loop",
                type = "song",
            ),
        )
    }

    @Test
    fun playStateQueryRejectsMalformedSessionAndProgress() {
        assertFailsWith<IllegalArgumentException> {
            submitPlayStateQueryParameters(
                id = 42,
                sessionId = "lowercase123",
                progressSeconds = 30,
                playMode = "list_loop",
                type = "song",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            submitPlayStateQueryParameters(
                id = 42,
                sessionId = "AB12CD34EF56",
                progressSeconds = -1,
                playMode = "list_loop",
                type = "song",
            )
        }
    }
}
