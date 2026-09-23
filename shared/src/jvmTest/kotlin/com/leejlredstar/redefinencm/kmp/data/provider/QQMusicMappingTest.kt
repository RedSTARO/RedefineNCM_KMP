package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.api.QQSong
import com.leejlredstar.redefinencm.kmp.data.api.QQSonglistDetail
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the shapes of the `L-1124/QQMusicApi` web gateway this client depends on: the
 * `{code, msg, data}` envelope, integer enum parameters, the bare CDN path it returns for a
 * stream, its playlist paging, and the quality descent that keeps an entitlement wall from looking
 * like a broken provider.
 */
class QQMusicMappingTest {
    private val recordedUrls = mutableListOf<String>()
    private val recordedCookies = mutableListOf<String?>()

    private fun apiAnswering(
        cookie: String = "",
        body: (path: String, query: String) -> String?,
    ): QQMusicApi {
        val engine = MockEngine { request ->
            recordedUrls += request.url.toString()
            recordedCookies += request.headers[HttpHeaders.Cookie]
            val payload = body(request.url.encodedPath, request.url.encodedQuery)
            if (payload == null) {
                respond(
                    """{"code":-1,"msg":"未授权"}""",
                    HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    payload,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        return QQMusicApi(
            HttpClient(engine),
            baseUrl = { "http://localhost:8080" },
            cookie = { cookie },
        )
    }

    @Test
    fun searchReadsTheEnvelopeAndConvertsSecondsToMillis() = runTest {
        val api = apiAnswering { _, _ ->
            """
            {"code":0,"msg":"ok","data":{"total_num":1,"nextpage":2,"song":[
              {"id":97773,"mid":"0039MnYb0qxYhV","name":"晴天","title":"晴天",
               "singer":[{"id":4558,"mid":"0025NhlN2yWrP4","name":"周杰伦"}],
               "album":{"id":1,"mid":"000MkMni19ClKG","name":"叶惠美","pmid":"000MkMni19ClKG_1"},
               "file":{"media_mid":"x","size_128mp3":4308000,"size_320mp3":10770000,"size_flac":0},
               "pay":{"pay_play":0,"pay_month":1},"interval":269}
            ]}}
            """.trimIndent()
        }

        val tracks = api.search("晴天", 10)!!.song.map { it.toProviderTrack() }

        assertEquals(1, tracks.size)
        val track = tracks.single()
        assertEquals(ProviderItemId.qq("0039MnYb0qxYhV"), track.id)
        assertEquals("晴天", track.title)
        assertEquals("周杰伦", track.artistLine)
        assertEquals("叶惠美", track.album?.name)
        assertEquals(ProviderItemId.qq("000MkMni19ClKG"), track.album?.id)
        // pay_month=1 is QQ's members' mark; no FLAC size means no lossless tag.
        assertEquals(setOf(TrackTag.VIP), track.tags)
        // QQ reports seconds where NetEase reports milliseconds.
        assertEquals(269_000L, track.durationMillis)
        assertTrue(track.artworkUrl.endsWith("000MkMni19ClKG.jpg"))
    }

    @Test
    fun searchAsksForSongsByIntegerEnumAndPagesByNumber() = runTest {
        val api = apiAnswering { _, _ -> """{"code":0,"msg":"ok","data":{"song":[]}}""" }
        api.search("周杰伦", 5, page = 3)

        val url = recordedUrls.single()
        assertTrue(url.contains("/search/search_by_type?"), url)
        // The gateway exposes enums as their integer value; `song` would be a 422.
        assertTrue(url.contains("search_type=0"), url)
        assertTrue(url.contains("num=5"), url)
        assertTrue(url.contains("page=3"), url)
    }

    @Test
    fun rowsWithoutAnAlbumOfTheirOwnUseTheCoverIdForArtwork() {
        val song = QQSong(
            mid = "001ztyT12cfz2M",
            name = "晴天",
            album = com.leejlredstar.redefinencm.kmp.data.api.QQAlbum(mid = "", name = "", pmid = "0030lak94GN5Ad_0"),
        )

        val track = song.toProviderTrack()

        // Singles and indie uploads carry an empty album mid but a cover id, which QQ serves off
        // the same path as album art.
        assertNull(track.album?.id)
        assertTrue(track.artworkUrl.endsWith("0030lak94GN5Ad_0.jpg"), track.artworkUrl)
    }

    @Test
    fun playlistPagesCarryTheListInfoAndTheGatewaysMoreFlag() = runTest {
        val api = apiAnswering { _, _ ->
            """
            {"code":0,"msg":"ok","data":{
              "info":{"id":7707261125,"title":"甜度爆表","picurl":"http://qpic/x.jpg","desc":"note","songnum":66},
              "songs":[{"id":1,"mid":"002xTzGb2UBQRk","name":"你的","interval":163,
                "singer":[{"mid":"001","name":"DouDou"},{"mid":"002","name":"Viva宋佩豫"}],
                "album":{"mid":"0023VbHy1oT80v","name":"你的"}}],
              "total":66,"hasmore":1}}
            """.trimIndent()
        }

        val page = assertNotNull(api.playlistDetail(7707261125, num = 200, page = 1))
        assertEquals("甜度爆表", page.info?.title)
        assertEquals(66, page.info?.songnum)
        assertEquals(1, page.hasmore)
        assertTrue(recordedUrls.single().contains("/songlist/7707261125/detail?"), recordedUrls.single())
        assertTrue(recordedUrls.single().contains("num=200"), recordedUrls.single())

        val track = page.songs.single().toProviderTrack()
        assertEquals(ProviderItemId.qq("002xTzGb2UBQRk"), track.id)
        assertEquals("你的", track.title)
        assertEquals("DouDou / Viva宋佩豫", track.artistLine)
        assertEquals(163_000L, track.durationMillis)
        assertTrue(track.artworkUrl.endsWith("0023VbHy1oT80v.jpg"))
    }

    @Test
    fun playlistPagingStopsWhenTheGatewaySaysNoMoreOrAtTheCap() = runTest {
        val requestedPages = mutableListOf<Int>()
        val pages = collectQQPlaylistPages(maxPages = 10) { page ->
            requestedPages += page
            QQSonglistDetail(
                songs = listOf(QQSong(mid = "m$page")),
                hasmore = if (page < 3) 1 else 0,
            )
        }
        assertEquals(listOf(1, 2, 3), requestedPages)
        assertEquals(listOf("m1", "m2", "m3"), pages.flatMap { it.songs }.map { it.mid })

        // A gateway that never clears `hasmore` must not be paged forever.
        val capped = collectQQPlaylistPages(maxPages = 2) { page ->
            QQSonglistDetail(songs = listOf(QQSong(mid = "m$page")), hasmore = 1)
        }
        assertEquals(2, capped.size)

        // A failed later page keeps the pages already read rather than dropping the playlist.
        val partial = collectQQPlaylistPages(maxPages = 10) { page ->
            if (page == 1) QQSonglistDetail(songs = listOf(QQSong(mid = "m1")), hasmore = 1) else null
        }
        assertEquals(1, partial.size)
    }

    @Test
    fun lyricsCarryTranslationAndRomanizationSeparately() = runTest {
        val api = apiAnswering { _, _ ->
            """{"code":0,"msg":"ok","data":{"songid":97773,"lyric":"[00:01.00]line","trans":"[00:01.00]译","roma":""}}"""
        }
        val lyric = assertNotNull(api.lyric("0039MnYb0qxYhV"))
        assertEquals("[00:01.00]line", lyric.lyric)
        assertEquals("[00:01.00]译", lyric.trans)
        assertEquals("", lyric.roma)
        assertTrue(recordedUrls.single().contains("/song/0039MnYb0qxYhV/lyric?"), recordedUrls.single())
    }

    @Test
    fun streamUrlJoinsTheCdnHostOntoThePathTheGatewayReturns() = runTest {
        val api = apiAnswering { _, _ ->
            """
            {"code":0,"msg":"ok","data":{"expiration":7200,"data":[
              {"mid":"002xTzGb2UBQRk","filename":"M500002xTzGb2UBQRk002xTzGb2UBQRk.mp3",
               "purl":"M500002AkhKv0YDLIl.mp3?guid=1&vkey=K&uin=&src=x.mp3&redirect=1",
               "vkey":"K","ekey":"","result":0}]}}
            """.trimIndent()
        }

        assertEquals(
            "https://dl.stream.qqmusic.qq.com/M500002AkhKv0YDLIl.mp3?guid=1&vkey=K&uin=&src=x.mp3&redirect=1",
            api.songUrl("002xTzGb2UBQRk", QQFileType.MP3_128),
        )
        val url = recordedUrls.single()
        assertTrue(url.contains("/song/002xTzGb2UBQRk/url?"), url)
        assertTrue(url.contains("file_type=13"), url)
    }

    @Test
    fun streamUrlIsNullWhenTheGatewayReportsNoPath() = runTest {
        // A track the account may not fetch at that tier answers an empty purl and a result code.
        val api = apiAnswering { _, _ ->
            """{"code":0,"msg":"ok","data":{"expiration":7200,"data":[{"mid":"001ztyT12cfz2M","filename":"F000x.flac","purl":"","vkey":"","ekey":"","result":104003}]}}"""
        }
        assertNull(api.songUrl("001ztyT12cfz2M", QQFileType.FLAC))
    }

    @Test
    fun aNonZeroCodeIsNotDecodedAsAnEmptyResult() = runTest {
        val api = apiAnswering { _, _ -> """{"code":-1,"msg":"上游错误","data":null}""" }
        // Decoded as the success shape this would read as "no such song" instead of an error.
        assertNull(api.search("x", 1))
    }

    @Test
    fun anUnauthorizedStatusIsNotDecodedAsAnEmptyResult() = runTest {
        val api = apiAnswering { _, _ -> null }
        assertNull(api.search("x", 1))
    }

    @Test
    fun theStoredCredentialTravelsAsTheCookieOnEveryRequest() = runTest {
        val api = apiAnswering(cookie = " musicid=123; musickey=abc ") { _, _ ->
            """{"code":0,"msg":"ok","data":{"song":[]}}"""
        }
        api.search("x", 1)
        api.lyric("m")

        // Credentials go per request rather than being installed into the gateway, so the Android
        // build can sign in without reaching the gateway's config file.
        assertEquals(
            listOf<String?>("musicid=123; musickey=abc", "musicid=123; musickey=abc"),
            recordedCookies,
        )
    }

    @Test
    fun noCookieHeaderIsSentWhenSignedOut() = runTest {
        val api = apiAnswering { _, _ -> """{"code":0,"msg":"ok","data":{"song":[]}}""" }
        api.search("x", 1)
        assertEquals(listOf<String?>(null), recordedCookies)
    }

    @Test
    fun qrCodeRoutesMapTheGatewayShapes() = runTest {
        val api = apiAnswering { path, _ ->
            if (path.endsWith("/status")) {
                """
                {"code":0,"msg":"ok","data":{"event":0,"done":true,
                  "credential":{"musicid":123,"musickey":"Q_H_L_x","openid":"","refresh_token":"r",
                    "access_token":"","expired_at":0,"unionid":"","str_musicid":"123","refresh_key":"k",
                    "musickeyCreateTime":1,"keyExpiresIn":2,"encryptUin":"e","loginType":1},
                  "identifier":"abc","login_type":"qq"}}
                """.trimIndent()
            } else {
                """{"code":0,"msg":"ok","data":{"qr_type":"qq","identifier":"abc","mimetype":"image/png","data":"iVBORw0KGgo=","img":"data:image/png;base64,iVBORw0KGgo="}}"""
            }
        }

        val code = assertNotNull(api.qrCodeStart("qq"))
        assertEquals("abc", code.identifier)
        assertEquals("iVBORw0KGgo=", code.data)
        assertTrue(recordedUrls.first().endsWith("/login/qrcode/qq"), recordedUrls.first())

        val status = assertNotNull(api.qrCodeStatus("qq", "abc"))
        assertEquals(0, status.event)
        assertEquals(123L, status.credential?.musicid)
        assertEquals("k", status.credential?.refreshKey)
        assertTrue(recordedUrls.last().contains("/login/qrcode/qq/status?identifier=abc"), recordedUrls.last())
    }

    @Test
    fun qualityLadderDescendsFromTheRequestedTier() {
        // A lossless preference must still reach audio when only 128 is served to the account.
        assertEquals(
            listOf(QQFileType.FLAC, QQFileType.MP3_320, QQFileType.MP3_128, QQFileType.AAC_96),
            SoundQualityPreference.LOSSLESS.qqFileTypeLadder(),
        )
        assertEquals(
            listOf(QQFileType.MP3_320, QQFileType.MP3_128, QQFileType.AAC_96),
            SoundQualityPreference.HIGH.qqFileTypeLadder(),
        )
        assertEquals(
            listOf(QQFileType.MP3_128, QQFileType.AAC_96),
            SoundQualityPreference.STANDARD.qqFileTypeLadder(),
        )
        // Never ascends: a standard preference must not be served lossless.
        assertTrue(SoundQualityPreference.STANDARD.qqFileTypeLadder().none { it == QQFileType.FLAC })
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
