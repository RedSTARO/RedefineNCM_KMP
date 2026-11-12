package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.data.api.ApiJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NcmEndpointMapperTest {
    private data class Mapping(
        val route: String,
        val uri: String,
        val crypto: NcmEndpointCrypto,
        val data: String = "{}",
        val query: Map<String, String> = emptyMap(),
        val response: NcmEndpointResponseKind = NcmEndpointResponseKind.IDENTITY,
    )

    @Test
    fun ordinaryRoutesMatchThePinnedModules() {
        val weapi = NcmEndpointCrypto.WEAPI
        val eapi = NcmEndpointCrypto.EAPI
        val xeapi = NcmEndpointCrypto.XEAPI
        val mappings = listOf(
            Mapping("user/account", "/api/nuser/account/get", weapi),
            Mapping(
                "user/detail", "/api/v1/user/detail/42", weapi,
                query = mapOf("uid" to "42"), response = NcmEndpointResponseKind.USER_DETAIL,
            ),
            Mapping("user/level", "/api/user/level", weapi),
            Mapping("user/record", "/api/v1/play/record", weapi, """{"uid":"42","type":0}""", mapOf("uid" to "42")),
            Mapping("record/recent/song", "/api/play-record/song/list", weapi, """{"limit":100}"""),
            Mapping("login/status", "/api/w/nuser/account/get", weapi, response = NcmEndpointResponseKind.LOGIN_STATUS),
            Mapping(
                "login/qr/key", "/api/login/qrcode/unikey", eapi, """{"type":3}""",
                response = NcmEndpointResponseKind.LOGIN_QR_KEY,
            ),
            Mapping(
                "login/qr/check", "/api/login/qrcode/client/login", eapi, """{"key":"key-1","type":3}""",
                mapOf("key" to "key-1"), NcmEndpointResponseKind.LOGIN_QR_CHECK,
            ),
            Mapping("daily_signin", "/api/point/dailyTask", eapi, """{"type":0}"""),
            Mapping("vip/growthpoint/getall", "/api/vipnewcenter/app/level/task/reward/getall", xeapi),
            Mapping(
                "user/playlist", "/api/user/playlist", weapi,
                """{"uid":"42","limit":30,"offset":0,"includeVideo":true}""", mapOf("uid" to "42"),
            ),
            Mapping("playlist/detail", "/api/v6/playlist/detail", eapi, """{"id":"42","n":100000,"s":8}""", idQuery),
            Mapping("playlist/update/playcount", "/api/playlist/update/playcount", eapi, idData, idQuery),
            Mapping(
                "playmode/intelligence/list", "/api/playmode/intelligence/list", eapi,
                """{"songId":"42","type":"fromPlayOne","playlistId":"77","startMusicId":"42","count":1}""",
                mapOf("id" to "42", "pid" to "77"),
            ),
            Mapping(
                "song/url/v1", "/api/song/enhance/player/url/v1", xeapi,
                """{"ids":"[42,43]","level":"lossless","encodeType":"flac"}""",
                mapOf("id" to "42,43", "level" to "lossless"),
            ),
            Mapping(
                "song/detail", "/api/v3/song/detail", weapi,
                """{"c":"[{\"id\":42},{\"id\":43}]"}""", mapOf("ids" to "42,43"),
            ),
            Mapping("song/wiki/summary", "/api/song/play/about/block/page", eapi, """{"songId":"42"}""", idQuery),
            Mapping("song/dynamic/cover", "/api/songplay/dynamic-cover", eapi, """{"songId":"42"}""", idQuery),
            Mapping(
                "cloudsearch", "/api/cloudsearch/pc", eapi,
                """{"s":"a keyword","type":1,"limit":30,"offset":0,"total":true}""", mapOf("keywords" to "a keyword"),
            ),
            Mapping("artist/detail", "/api/artist/head/info/get", eapi, idData, idQuery),
            Mapping("artist/top/song", "/api/artist/top/song", weapi, idData, idQuery),
            Mapping("artist/album", "/api/artist/albums/42", weapi, """{"limit":30,"offset":0,"total":true}""", idQuery),
            Mapping("album", "/api/v1/album/42", weapi, query = idQuery),
            Mapping("search/hot/detail", "/api/hotsearchlist/get", weapi),
            Mapping("search/suggest", "/api/search/suggest/web", weapi, """{"s":""}"""),
            Mapping("recommend/songs", "/api/v3/discovery/recommend/songs", weapi),
            Mapping("recommend/resource", "/api/v1/discovery/recommend/resource", weapi),
            Mapping("lyric", "/api/song/lyric", eapi, """{"id":"42","tv":-1,"lv":-1,"rv":-1,"kv":-1,"_nmclfl":1}""", idQuery),
            Mapping(
                "lyric/new", "/api/song/lyric/v1", eapi,
                """{"id":"42","cp":false,"tv":0,"lv":0,"rv":0,"kv":0,"yv":0,"ytv":0,"yrv":0}""", idQuery,
            ),
            Mapping("like", "/api/radio/like", weapi, """{"alg":"itembased","trackId":"42","like":true,"time":"3"}""", idQuery),
            Mapping("likelist", "/api/song/like/get", eapi, """{"uid":"42"}""", mapOf("uid" to "42")),
            Mapping("song/like/check", "/api/song/like/check", eapi, """{"trackIds":"[42,43]"}""", mapOf("ids" to "[42,43]")),
            Mapping(
                "comment/music", "/api/v1/resource/comments/R_SO_4_42", weapi,
                """{"rid":"42","limit":20,"offset":0,"beforeTime":0}""", idQuery,
            ),
            Mapping(
                "comment/hot", "/api/v1/resource/hotcomments/R_SO_4_42", weapi,
                """{"rid":"42","limit":20,"offset":0,"beforeTime":0}""", idQuery + ("type" to "0"),
            ),
        )

        mappings.forEach { expected ->
            val call = NcmEndpointMapper.map(expected.route, expected.query)
            assertEquals(expected.uri, call.uri, expected.route)
            assertEquals(expected.crypto, call.crypto, expected.route)
            assertEquals(json(expected.data), call.data, expected.route)
            assertEquals(expected.response, call.responseKind, expected.route)
            assertEquals(NcmEndpointAction.REQUEST, call.action, expected.route)
            assertTrue(call.cookieOverrides.isEmpty(), expected.route)
            assertEquals(null, call.domain, expected.route)
        }
    }

    @Test
    fun explicitQueryNumbersRemainStringsWhileFallbackDefaultsAreNumbers() {
        val call = NcmEndpointMapper.map(
            "user/playlist",
            mapOf("uid" to "42", "limit" to "100", "offset" to "0"),
        )
        assertEquals(json("""{"uid":"42","limit":"100","offset":"0","includeVideo":true}"""), call.data)
        assertTrue(call.data.getValue("offset").jsonPrimitive.isString)
        assertFalse(NcmEndpointMapper.map("user/playlist", emptyMap()).data.getValue("offset").jsonPrimitive.isString)
        assertEquals(
            json("""{"uid":"42","limit":30,"offset":0,"includeVideo":true}"""),
            NcmEndpointMapper.map("user/playlist", mapOf("uid" to "42", "limit" to "", "offset" to "")).data,
        )
    }

    @Test
    fun serviceDefaultsDoNotReplaceDifferentUpstreamDefaults() {
        // NCMApi defaults userRecord's type to 1, but the upstream route defaults it to 0.
        assertEquals(JsonPrimitive(0), NcmEndpointMapper.map("user/record", emptyMap()).data["type"])
        assertEquals(JsonPrimitive("1"), NcmEndpointMapper.map("user/record", mapOf("type" to "1")).data["type"])
        // NCMApi defaults userPlaylist's limit to 100; the module itself defaults it to 30.
        assertEquals(JsonPrimitive(30), NcmEndpointMapper.map("user/playlist", emptyMap()).data["limit"])
        assertEquals(JsonPrimitive("100"), NcmEndpointMapper.map("user/playlist", mapOf("limit" to "100")).data["limit"])
        assertEquals(JsonPrimitive("0"), NcmEndpointMapper.map("daily_signin", mapOf("type" to "0")).data["type"])
    }

    @Test
    fun songDetailUsesAJsonStringNotAJsonArrayAndSplitsWhitespace() {
        val call = NcmEndpointMapper.map("song/detail", mapOf("ids" to "42 , 43,\t44"))
        assertEquals("/api/v3/song/detail", call.uri)
        assertEquals(NcmEndpointCrypto.WEAPI, call.crypto)
        assertTrue(call.data.getValue("c").jsonPrimitive.isString)
        assertEquals("[{\"id\":42},{\"id\":43},{\"id\":44}]", call.data.getValue("c").jsonPrimitive.content)
        assertEquals(setOf("c"), call.data.keys)
    }

    @Test
    fun cloudsearchRenamesKeywordsAndPreservesPagingAndType() {
        val query = mapOf("keywords" to "a & b /?", "type" to "100", "limit" to "60", "offset" to "30")
        assertEquals(
            json("""{"s":"a & b /?","type":"100","limit":"60","offset":"30","total":true}"""),
            NcmEndpointMapper.map("cloudsearch", query).data,
        )
    }

    @Test
    fun searchSuggestDefaultsToWebUnlessTheCallerExplicitlyRequestsMobile() {
        assertEquals("/api/search/suggest/web", NcmEndpointMapper.map("search/suggest", emptyMap()).uri)
        val call = NcmEndpointMapper.map("search/suggest", mapOf("type" to "mobile", "keywords" to "song"))
        assertEquals("/api/search/suggest/keyword", call.uri)
        assertEquals(json("""{"s":"song"}"""), call.data)
        assertEquals("/api/search/suggest/web", NcmEndpointMapper.map("search/suggest", mapOf("type" to "other")).uri)
    }

    @Test
    fun songUrlSkyAndVividHaveTheirExactEndpointSpecificOverrides() {
        val sky = NcmEndpointMapper.map("song/url/v1", idQuery + ("level" to "sky"))
        assertEquals(json("""{"ids":"[42]","level":"sky","encodeType":"flac","immerseType":"c51"}"""), sky.data)
        assertEquals(
            JsonPrimitive("ste2"),
            NcmEndpointMapper.map("song/url/v1", idQuery + mapOf("level" to "sky", "immerseType" to "ste2")).data["immerseType"],
        )
        val vivid = NcmEndpointMapper.map("song/url/v1", idQuery + ("level" to "vivid"))
        assertEquals(NcmEndpointCrypto.XEAPI, vivid.crypto)
        assertEquals(json("""{"ids":"[42]","level":"vivid","encodeType":"mp3"}"""), vivid.data)
        assertEquals(mapOf("os" to "android", "appver" to "9.5.61"), vivid.cookieOverrides)
        assertTrue(sky.cookieOverrides.isEmpty())
    }

    @Test
    fun intelligenceListUsesTheSeedUnlessAStartingSongIsProvided() {
        val query = mapOf("id" to "42", "pid" to "77", "sid" to "99", "count" to "3")
        assertEquals(
            json("""{"songId":"42","type":"fromPlayOne","playlistId":"77","startMusicId":"99","count":"3"}"""),
            NcmEndpointMapper.map("playmode/intelligence/list", query).data,
        )
        assertEquals(
            JsonPrimitive("42"),
            NcmEndpointMapper.map("playmode/intelligence/list", query + ("sid" to "")).data["startMusicId"],
        )
    }

    @Test
    fun playlistTrackAllIsAnExplicitCompositeWithTheUpstreamThousandTrackLimit() {
        val call = NcmEndpointMapper.map("playlist/track/all", idQuery)
        assertEquals(NcmEndpointAction.PLAYLIST_TRACK_ALL, call.action)
        assertEquals("", call.uri)
        assertEquals(NcmEndpointCrypto.EAPI, call.crypto)
        assertEquals(json("""{"id":"42","n":100000,"s":8,"limit":1000,"offset":0}"""), call.data)
        assertEquals(
            json("""{"id":"42","n":100000,"s":"0","limit":15,"offset":2}"""),
            NcmEndpointMapper.map("playlist/track/all", idQuery + mapOf("s" to "0", "limit" to "15.9", "offset" to "2tail")).data,
        )
    }

    @Test
    fun compositePaginationUsesParseIntThenTheJavaScriptFalsyFallback() {
        assertEquals(
            JsonPrimitive(1000),
            NcmEndpointMapper.map("playlist/track/all", idQuery + ("limit" to "0")).data["limit"],
        )
        assertEquals(
            JsonPrimitive(1000),
            NcmEndpointMapper.map("playlist/track/all", idQuery + ("limit" to "invalid")).data["limit"],
        )
        assertEquals(
            JsonPrimitive(16),
            NcmEndpointMapper.map("playlist/track/all", idQuery + ("limit" to "0x10")).data["limit"],
        )
        assertEquals(
            JsonPrimitive(-2),
            NcmEndpointMapper.map("playlist/track/all", idQuery + ("offset" to " -2 ")).data["offset"],
        )
    }

    @Test
    fun qrCreationIsLocalAndUsesQueryTruthinessRatherThanBooleanParsing() {
        val call = NcmEndpointMapper.map("login/qr/create", mapOf("key" to "key-1", "qrimg" to "true"))
        assertEquals(NcmEndpointAction.QR_CREATE, call.action)
        assertEquals("", call.uri)
        assertEquals(json("""{"key":"key-1","qrimg":true,"platform":"pc"}"""), call.data)
        assertEquals(JsonPrimitive(false), NcmEndpointMapper.map("login/qr/create", emptyMap()).data["qrimg"])
        assertEquals(
            JsonPrimitive(true),
            NcmEndpointMapper.map("login/qr/create", mapOf("qrimg" to "false")).data["qrimg"],
        )
        assertEquals(
            JsonPrimitive("web"),
            NcmEndpointMapper.map("login/qr/create", mapOf("platform" to "web")).data["platform"],
        )
    }

    @Test
    fun audioMatchIsAnUnencryptedGetActionWithAnUnescapedFingerprintValue() {
        val fingerprint = "a+b/= &raw"
        val call = NcmEndpointMapper.map("audio/match", mapOf("duration" to "3", "audioFP" to fingerprint))
        assertEquals(NcmEndpointAction.AUDIO_MATCH, call.action)
        assertEquals(NcmEndpointCrypto.API, call.crypto)
        assertEquals("https://interface.music.163.com", call.domain)
        assertEquals("/api/music/audio/match", call.uri)
        assertEquals(NcmEndpointResponseKind.AUDIO_MATCH, call.responseKind)
        assertEquals(
            json("""{"sessionId":"0123456789abcdef","algorithmCode":"shazam_v2","duration":"3","rawdata":"a+b/= &raw","times":1,"decrypt":1}"""),
            call.data,
        )
    }

    @Test
    fun weblogAndScrobbleHaveDedicatedReportingActions() {
        val body = json("""{"data":{"logs":"[{\"action\":\"startplay\"}]"}}""")
        val weblog = NcmEndpointMapper.map("weblog", emptyMap(), body)
        assertEquals(NcmEndpointAction.WEBLOG, weblog.action)
        assertEquals(NcmEndpointCrypto.WEAPI, weblog.crypto)
        assertEquals("/api/feedback/weblog", weblog.uri)
        assertEquals(body.jsonObject.getValue("data"), weblog.data)
        assertEquals(json("{}"), NcmEndpointMapper.map("weblog", emptyMap()).data)

        val query = mapOf(
            "id" to "42", "time" to "90", "total" to "100", "sourceid" to "77", "source" to "playlist",
            "name" to "Track", "artist" to "Artist", "bitrate" to "320", "level" to "exhigh", "vip" to "true",
            "realIP" to "192.0.2.4", "timestamp" to "123", "cookie" to "MUSIC_U=secret", "unrelated" to "ignored",
        )
        val scrobble = NcmEndpointMapper.map("scrobble/v1", query)
        assertEquals(NcmEndpointAction.SCROBBLE_V1, scrobble.action)
        assertEquals("", scrobble.uri)
        assertEquals(mapOf("os" to "pc"), scrobble.cookieOverrides)
        assertEquals(query.filterKeys { it !in setOf("realIP", "timestamp", "cookie", "unrelated") }.keys, scrobble.data.keys)
        scrobble.data.forEach { (key, value) -> assertEquals(JsonPrimitive(query.getValue(key)), value) }
    }

    @Test
    fun relaySubmitsAnEmbeddedJsonStringWithNumericProgressAndStringResourceId() {
        val query = mapOf(
            "id" to "42", "sessionId" to "AB12CD34EF56", "progress" to "30", "playMode" to "list_loop", "type" to "song",
        )
        val call = NcmEndpointMapper.map("relay/play/state/submit", query)
        assertEquals(NcmEndpointAction.REQUEST, call.action)
        assertEquals(NcmEndpointCrypto.WEAPI, call.crypto)
        assertEquals("/api/relay/play/state/submit", call.uri)
        val embedded = call.data.getValue("playStateSubmitReq").jsonPrimitive
        assertTrue(embedded.isString)
        assertEquals(
            json("""{"resource":{"id":"42","type":"song"},"progress":30,"sessionId":"AB12CD34EF56","playMode":"list_loop"}"""),
            json(embedded.content),
        )
    }

    @Test
    fun relayFallbackSessionMatchesTheUpstreamAlphabetAndLength() {
        val embedded = NcmEndpointMapper.map("relay/play/state/submit", idQuery).data.getValue("playStateSubmitReq").jsonPrimitive
        val data = json(embedded.content).jsonObject
        assertTrue(Regex("^[A-Z0-9]{12}$").matches(data.getValue("sessionId").jsonPrimitive.content))
        assertEquals(JsonPrimitive(0), data["progress"])
        assertEquals(JsonPrimitive("list_loop"), data["playMode"])
        assertFailsWith<IllegalArgumentException> { NcmEndpointMapper.map("relay/play/state/submit", emptyMap()) }
    }

    @Test
    fun commentPagingNamesAndResourcePrefixesMatchTheModules() {
        val query = idQuery + mapOf("limit" to "50", "offset" to "40", "before" to "1234567890")
        assertEquals(
            json("""{"rid":"42","limit":"50","offset":"40","beforeTime":"1234567890"}"""),
            NcmEndpointMapper.map("comment/music", query).data,
        )
        assertEquals(
            "/api/v1/resource/hotcomments/R_SO_4_42",
            NcmEndpointMapper.map("comment/hot", query + ("type" to "0")).uri,
        )
        assertFailsWith<IllegalArgumentException> { NcmEndpointMapper.map("comment/hot", query + ("type" to "unknown")) }
    }

    @Test
    fun likeChecksAndRecommendationsPreserveOptionalValuesWithoutInventingDefaults() {
        val unlike = NcmEndpointMapper.map("like", idQuery + ("like" to "false"))
        assertEquals(JsonPrimitive(false), unlike.data["like"])
        assertFalse(NcmEndpointMapper.map("like", emptyMap()).data.containsKey("trackId"))
        assertEquals(JsonPrimitive("1"), NcmEndpointMapper.map("recommend/songs", mapOf("afresh" to "1")).data["afresh"])
        assertEquals(
            JsonPrimitive("[42,43]"),
            NcmEndpointMapper.map("song/like/check", mapOf("ids" to "[42,43]")).data["trackIds"],
        )
    }

    @Test
    fun versionIsLocalAndUnknownRoutesCannotFallThroughToTheNetEaseHost() {
        val call = NcmEndpointMapper.map("inner/version", emptyMap())
        assertEquals(NcmEndpointAction.LOCAL_VERSION, call.action)
        assertEquals("", call.uri)
        assertEquals(json("{}"), call.data)
        listOf("unknown/route", "song/url", "api/v3/song/detail", "https://example.test/user/account").forEach { route ->
            assertFailsWith<IllegalArgumentException>(route) { NcmEndpointMapper.map(route, emptyMap()) }
        }
        assertEquals(
            NcmEndpointMapper.map("user/account", emptyMap()),
            NcmEndpointMapper.map("/user/account/", emptyMap()),
        )
    }

    @Test
    fun transportCredentialsAndCacheBustersAreNeverEndpointData() {
        val query = mapOf("cookie" to "MUSIC_U=secret", "realIP" to "192.0.2.4", "timestamp" to "123")
        assertEquals(json("{}"), NcmEndpointMapper.map("user/account", query).data)
        assertEquals(json("""{"limit":100}"""), NcmEndpointMapper.map("record/recent/song", query).data)
        assertTrue(NcmEndpointMapper.map("user/account", query).cookieOverrides.isEmpty())
    }

    private fun json(text: String): JsonElement = ApiJson.parseToJsonElement(text)

    private companion object {
        val idQuery = mapOf("id" to "42")
        const val idData = """{"id":"42"}"""
    }
}
