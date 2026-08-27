package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the two response-shape hazards of the `Rain120/qq-music-api` backend — query parameters
 * rather than the path parameters its routes declare, and search rows disagreeing with playlist
 * rows on field names — plus the quality descent that keeps an entitlement wall from looking like
 * a broken provider.
 */
class QQMusicMappingTest {
    private val recordedUrls = mutableListOf<String>()

    private fun apiAnswering(body: (path: String, query: String) -> String?): QQMusicApi {
        val engine = MockEngine { request ->
            recordedUrls += request.url.toString()
            val payload = body(request.url.encodedPath, request.url.encodedQuery)
            if (payload == null) {
                respond("{}", HttpStatusCode.BadRequest)
            } else {
                respond(
                    payload,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        return QQMusicApi(HttpClient(engine), baseUrl = { "http://localhost:3200" })
    }

    @Test
    fun searchReadsTheEnvelopeAndConvertsSecondsToMillis() = runTest {
        val api = apiAnswering { _, _ ->
            """
            {"response":{"data":{"song":{"list":[
              {"songmid":"0039MnYb0qxYhV","songname":"晴天","albummid":"000MkMni19ClKG",
               "albumname":"叶惠美","interval":269,
               "singer":[{"mid":"0025NhlN2yWrP4","name":"周杰伦"}],
               "size128":4308000,"size320":10770000,"sizeflac":0}
            ]}}}}
            """.trimIndent()
        }

        val tracks = api.search("晴天", 10)!!.data!!.song!!.list.map { it.toProviderTrack() }

        assertEquals(1, tracks.size)
        val track = tracks.single()
        assertEquals(ProviderItemId.qq("0039MnYb0qxYhV"), track.id)
        assertEquals("晴天", track.title)
        assertEquals("周杰伦", track.artistLine)
        assertEquals("叶惠美", track.album?.name)
        // QQ reports seconds where NetEase reports milliseconds.
        assertEquals(269_000L, track.durationMillis)
        assertTrue(track.artworkUrl.endsWith("000MkMni19ClKG.jpg"))
    }

    @Test
    fun requestsPassQueryParametersBecauseTheBackendIgnoresItsOwnPathParameters() = runTest {
        val api = apiAnswering { _, _ -> """{"response":{"data":{"song":{"list":[]}}}}""" }
        api.search("周杰伦", 5)

        val url = recordedUrls.single()
        // `/getSearchByKey/周杰伦` answers `400 search key is null`; every controller reads ctx.query.
        assertTrue(url.contains("getSearchByKey?"), "expected a query string, got $url")
        assertTrue(url.contains("limit=5"), url)
    }

    @Test
    fun playlistRowsUseADifferentShapeFromSearchRows() = runTest {
        val api = apiAnswering { _, _ ->
            """
            {"response":{"cdlist":[{
              "disstid":"7707261125","dissname":"甜度爆表","logo":"http://qpic/x.jpg",
              "desc":"note","songnum":66,
              "songlist":[{"mid":"002xTzGb2UBQRk","name":"你的","interval":163,
                "singer":[{"mid":"001","name":"DouDou"},{"mid":"002","name":"Viva宋佩豫"}],
                "album":{"mid":"0023VbHy1oT80v","name":"你的"}}]
            }]}}
            """.trimIndent()
        }

        val entry = assertNotNull(api.playlistDetail("7707261125")).cdlist.single()
        assertEquals("甜度爆表", entry.dissname)
        assertEquals(66, entry.songnum)

        // `mid`/`name` with a nested album, against search's `songmid`/`songname`/`albummid`.
        val track = entry.songlist.single().toProviderTrack()
        assertEquals(ProviderItemId.qq("002xTzGb2UBQRk"), track.id)
        assertEquals("你的", track.title)
        assertEquals("DouDou / Viva宋佩豫", track.artistLine)
        assertEquals(163_000L, track.durationMillis)
        assertTrue(track.artworkUrl.endsWith("0023VbHy1oT80v.jpg"))
    }

    @Test
    fun lyricsKeepTranslationSeparateAndDropEmptyOnes() = runTest {
        val api = apiAnswering { _, _ ->
            """{"response":{"code":0,"lyric":"[00:01.00]line","trans":""}}"""
        }
        val lyric = assertNotNull(api.lyric("0039MnYb0qxYhV"))
        assertEquals("[00:01.00]line", lyric.lyric)
        assertEquals("", lyric.trans)
    }

    @Test
    fun playUrlIsNullWhenTheBackendReportsNoPlaybackLink() = runTest {
        // Signed out, or without an entitled account, QQ answers with an empty purl.
        val api = apiAnswering { _, _ ->
            """{"data":{"playUrl":{"0039MnYb0qxYhV":{"url":"","error":"暂无播放链接"}}}}"""
        }
        assertNull(api.songUrl("0039MnYb0qxYhV", "flac"))
    }

    @Test
    fun playUrlIsReturnedWhenTheTierIsReachable() = runTest {
        val api = apiAnswering { _, _ ->
            """{"data":{"playUrl":{"002xTzGb2UBQRk":{"url":"http://cdn/x.mp3?vkey=1"}}}}"""
        }
        assertEquals("http://cdn/x.mp3?vkey=1", api.songUrl("002xTzGb2UBQRk", "128"))
    }

    @Test
    fun nonSuccessStatusIsNotDecodedAsAnEmptyResult() = runTest {
        val api = apiAnswering { _, _ -> null }
        // A 400 body decoded as the success shape would read as "no such song" instead of an error.
        assertNull(api.search("x", 1))
    }

    @Test
    fun qualityLadderDescendsFromTheRequestedTier() {
        // Anonymous QQ reaches only 128/m4a, so a lossless preference must still reach audio.
        assertEquals(
            listOf("flac", "320", "128", "m4a"),
            SoundQualityPreference.LOSSLESS.qqTierLadder(),
        )
        assertEquals(listOf("320", "128", "m4a"), SoundQualityPreference.HIGH.qqTierLadder())
        assertEquals(listOf("128", "m4a"), SoundQualityPreference.STANDARD.qqTierLadder())
        // Never ascends: a standard preference must not be served lossless.
        assertTrue(SoundQualityPreference.STANDARD.qqTierLadder().none { it == "flac" })
    }

    @Test
    fun neteaseSpatialTiersMapToTheTopRungRatherThanTheBottom() {
        // Spatial and mastered tiers are lossless-or-better sources with no QQ equivalent.
        assertEquals(
            SoundQualityPreference.HIRES,
            SoundQualityPreference.fromSoundQualityName("DOLBY"),
        )
        assertEquals(
            SoundQualityPreference.HIRES,
            SoundQualityPreference.fromSoundQualityName("JYMASTER"),
        )
        assertEquals(
            SoundQualityPreference.HIGH,
            SoundQualityPreference.fromSoundQualityName("EXHIGH"),
        )
        assertEquals(
            SoundQualityPreference.STANDARD,
            SoundQualityPreference.fromSoundQualityName("standard"),
        )
        // An unknown tier degrades to high rather than failing.
        assertEquals(
            SoundQualityPreference.HIGH,
            SoundQualityPreference.fromSoundQualityName("future-tier"),
        )
    }

    @Test
    fun interleavingAlternatesProvidersSoOneDoesNotDominateTheTop() {
        val ncm = ProviderSearchResults(
            MusicProviderId.NETEASE,
            listOf(track("ncm", "a"), track("ncm", "b"), track("ncm", "c")),
        )
        val qq = ProviderSearchResults(MusicProviderId.QQ, listOf(track("qq", "x")))

        assertEquals(
            listOf("a", "x", "b", "c"),
            listOf(ncm, qq).interleaved().map { it.title },
        )
    }

    private fun track(provider: String, title: String) = ProviderTrack(
        id = if (provider == "qq") ProviderItemId.qq(title) else ProviderItemId.netease(1),
        title = title,
    )
}
