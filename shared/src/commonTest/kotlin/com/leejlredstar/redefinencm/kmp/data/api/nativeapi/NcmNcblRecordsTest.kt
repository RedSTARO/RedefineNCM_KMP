package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NcmNcblRecordsTest {
    private val builder = NcmNcblBuilder(NcmNcblTestCrypto())

    @Test
    fun plvAndPldMatchRawPinnedUpstreamRecords() {
        // Generated offline with pinned util/ncbl.js, Node v26.2.0, Date.now=1700000000123.
        // The VM allowed only built-in crypto/zlib and a rejecting axios stub. No account was used.
        val prepared = builder.prepare(
            NcmNcblParams(
                songId = 42,
                playedSeconds = 291,
                totalSeconds = 300,
                sourceId = "36780169",
                source = "playlist",
                name = "ignored",
                artist = "ignored",
            ),
            COOKIE,
            NOW,
        )
        val plv = """{"mode":"circulation","download":0,"alg":"","status":"front","id":"42","bitrate":320,"type":"song","is_listentogether":0,"source":"playlist","is_heart":0,"resource_ratio":"","resource_time":300,"musiceffect_id":"","app_mode":2,"bitrate_level":"exhigh","_addrefer":"[F:63][1700000000123#933#3.1.35#205293#c9156c3][e][2][23][cell_pc_songlist_song:2|page_pc_songlist_songflow|page_mine_like_music][42:song:x:x|:::|36780169:list::]","_multirefers":["[F:26][s][18][_ai]","[F:26][s][12][_ai]","[F:63][1700000000123#933#3.1.35#205293#c9156c3][e][2][8][cell_pc_main_tab_entrance:6|page_pc_main_tab][""" +
            "\u6211\u559c\u6b22\u7684\u97f3\u4e50" +
            """:spm::|:::]","[F:26][s][5][_ai]","[F:26][s][0][_ai]"],"vipType":"11","fee":1,"file":4,"rightSource":0,"sourceId":"36780169","sourcetype":"track","libra_abt":"","channel":"fixture","curStartChannel":""}"""
        val pld = """{"mode":"circulation","download":0,"alg":"","status":"front","id":"42","time":291,"type":"song","is_listentogether":0,"source":"playlist","is_heart":0,"realtime":291,"resource_ratio":"","resource_time":300,"musiceffect_id":"1001","app_mode":1,"lyriceffect":"default","displayMode":"classic","bitrate":320,"bitrate_level":"exhigh","_addrefer":"[F:63][1700000000123#616#3.1.35#205293#c9156c3][e][2][92][btn_pc_cover_play|cell_pc_songlist_song:6|page_pc_songlist_songflow|page_mine_like_music][:::|42:song:x:x|:::|36780169:list::]","_multirefers":["[F:26][s][87][_ai]","[F:26][s][81][_ai]","[F:26][s][75][_ai]","[F:26][s][69][_ai]","[F:26][s][63][_ai]"],"vipType":"11","fee":8,"file":4,"rightSource":0,"sourceId":"36780169","sourcetype":"track","end":"interrupt","libra_abt":"","channel":"fixture","curStartChannel":""}"""
        assertEquals("1700000000\u0001_plv\u0001$plv", prepared.plvRecord)
        assertEquals("1700000000\u0001_pld\u0001$pld", prepared.pldRecord)
        assertFalse(prepared.plvRecord.endsWith('\n'))
        assertFalse(prepared.pldRecord.endsWith('\n'))
    }

    @Test
    fun metadataAndCookieMatchRawPinnedUpstreamAndPreserveTokenBytes() {
        val prepared = builder.prepare(NcmNcblParams(42, 30), COOKIE, NOW)
        assertEquals(
            """{"JSESSIONID-WYYY":"session-fixture","MUSIC_U":"fake+/%==","NMTID":"ti-fixture","WEVNSM":"1.2.3","WNMCID":"cid-fixture","__csrf":"csrf-fixture","_iuqxldmzr_":"33","_ntes_nnid":"nnid-fixture","_ntes_nuid":"nuid-fixture","appver":"3.1.35.205293","channel":"fixture","clientSign":"sign-fixture","deviceId":"fallback-device","mode":"fallback-model","ntes_kaola_ad":"1","os":"pc","osver":"fixture-os"}""",
            prepared.metaJson,
        )
        assertEquals(
            "JSESSIONID-WYYY=session-fixture; MUSIC_U=fake+/%==; NMTID=ti-fixture; WEVNSM=1.2.3; " +
                "WNMCID=cid-fixture; __csrf=csrf-fixture; __remember_me=true; _iuqxldmzr_=33; " +
                "_ntes_nnid=nnid-fixture; _ntes_nuid=nuid-fixture; appver=3.1.35.205293; " +
                "channel=fixture; clientSign=sign-fixture; deviceId=fallback-device; mode=fallback-model; " +
                "ntes_kaola_ad=1; os=pc; osver=fixture-os",
            prepared.cookieHeader,
        )
        assertEquals("fake+/%==", prepared.context.auth.token)
        assertEquals("fallback-device", prepared.context.device.id)
        assertEquals("fallback-model", prepared.context.device.model)
        assertEquals(NOW, prepared.context.startTime)
        assertTrue(prepared.context.processId in 10000..99999)
    }

    @Test
    fun sourceFallsBackToSongIdAndPlayedTimeIsCappedByTotal() {
        val prepared = builder.prepare(NcmNcblParams(42, 999, totalSeconds = 300), COOKIE, NOW)
        val plv = data(prepared.plvRecord)
        val pld = data(prepared.pldRecord)
        assertEquals("42", plv.getValue("sourceId").jsonPrimitive.content)
        assertEquals("list", plv.getValue("source").jsonPrimitive.content)
        assertEquals("track", plv.getValue("sourcetype").jsonPrimitive.content)
        assertEquals("300", pld.getValue("time").jsonPrimitive.content)
        assertEquals("300", pld.getValue("realtime").jsonPrimitive.content)
        assertEquals("300", pld.getValue("resource_time").jsonPrimitive.content)
        assertFalse("time" in plv)
        assertNotEquals(plv.getValue("app_mode"), pld.getValue("app_mode"))
    }

    @Test
    fun acceptsButDoesNotEmitUnusedSongFields() {
        val ordinary = builder.prepare(NcmNcblParams(42, 30), COOKIE, NOW)
        val ignored = builder.prepare(
            NcmNcblParams(42, 30, name = "Not a record field", artist = "Not a record field", vip = true),
            COOKIE,
            NOW,
        )
        assertEquals(ordinary.plvRecord, ignored.plvRecord)
        assertEquals(ordinary.pldRecord, ignored.pldRecord)
    }

    @Test
    fun cookieParsingUsesFirstEqualsNoDecodingAndLastDuplicateWins() {
        assertEquals(
            mapOf("MUSIC_U" to "last+%2F==", "literal" to "a=b=c"),
            builder.parseCookie("bad; =ignored; MUSIC_U=first; literal=a=b=c; MUSIC_U=last+%2F==;"),
        )
    }

    @Test
    fun forcesPcOnLocalSnapshotWithoutChangingCallerCookie() {
        val cookie = "MUSIC_U=fake; WNMCID=fixture-cid; os=ios; appver=3.1.35"
        val parsed = builder.parseCookie(cookie)
        val context = builder.context(parsed, NOW)
        assertEquals("ios", parsed["os"])
        assertEquals("pc", context.device.systemType)
        assertEquals("3.1.35", parsed["appver"])
        assertTrue(builder.cookieHeader(context).contains("appver=3.1.35.205293"))
    }

    @Test
    fun missingOptionalCookieFieldsUseUpstreamDefaults() {
        val prepared = builder.prepare(NcmNcblParams(42, 30), "MUSIC_U=fake", NOW)
        val context = prepared.context
        assertEquals("1.0.0", context.app.nsm)
        assertEquals("010203.$NOW.01.0", context.app.cid)
        assertEquals("netease", context.app.channel)
        assertEquals("3.1.35", context.app.version)
        assertEquals("205293", context.app.versionCode)
        assertEquals(",", context.device.nnid)
        assertEquals("Microsoft-Windows-10-Professional-build-19045-64bit", context.device.systemVersion)
    }

    @Test
    fun malformedInputsAndHeaderInjectionFailBeforeDispatch() {
        assertFailsWith<IllegalArgumentException> { NcmNcblParams(0, 30) }
        assertFailsWith<IllegalArgumentException> { NcmNcblParams(42, 0) }
        assertFailsWith<IllegalArgumentException> { NcmNcblParams(42, 30, totalSeconds = 0) }
        assertFailsWith<IllegalArgumentException> { NcmNcblParams(42, 30, bitrate = 0) }
        assertFailsWith<IllegalArgumentException> { builder.prepare(NcmNcblParams(42, 30), "", NOW) }
        assertFailsWith<IllegalArgumentException> {
            builder.prepare(NcmNcblParams(42, 30), "MUSIC_U=fake\r\nInjected: header", NOW)
        }
        assertFailsWith<IllegalArgumentException> {
            builder.prepare(NcmNcblParams(42, 30), COOKIE, -1)
        }
    }

    @Test
    fun credentialBearingModelsDoNotExposeValuesInToString() {
        val prepared = builder.prepare(NcmNcblParams(42, 30), COOKIE, NOW)
        listOf(
            prepared,
            prepared.context,
            prepared.context.app,
            prepared.context.auth,
            prepared.context.device,
            NcmNcblOptions(keyA = "private-fixture-key".encodeToByteArray()),
        ).forEach { model ->
            assertTrue(model.toString().contains("<redacted>"))
            assertFalse(model.toString().contains("fake+/%=="))
            assertFalse(model.toString().contains("session-fixture"))
            assertFalse(model.toString().contains("private-fixture-key"))
        }
    }

    @Test
    fun multipartHasExactUpstreamCrlfAndNoPayloadTextConversion() {
        val payload = byteArrayOf(0, 1, 2, 13, 10, -1)
        val part = builder.multipart(payload, "fixed_boundary", "op_12345_0_4294967295")
        val prefix = ("--fixed_boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"op_12345_0_4294967295\"\r\n" +
            "Content-Type: multipart/form-data\r\n\r\n").encodeToByteArray()
        val suffix = "\r\n--fixed_boundary--\r\n".encodeToByteArray()
        kotlin.test.assertContentEquals(prefix + payload + suffix, part.body)
        assertEquals("fixed_boundary", part.boundary)
        assertEquals("op_12345_0_4294967295", part.fileName)
        assertFailsWith<IllegalArgumentException> { builder.multipart(payload, "bad\r\nboundary", part.fileName) }
        assertFailsWith<IllegalArgumentException> { builder.multipart(payload, "safe", "op_12345_0_1\r\ninjected") }
    }

    private fun data(record: String) = Json.parseToJsonElement(record.substringAfter('\u0001').substringAfter('\u0001')).jsonObject

    companion object {
        private const val NOW = 1700000000123L
        private const val COOKIE = "MUSIC_U=fake+/%==; JSESSIONID-WYYY=session-fixture; NMTID=ti-fixture; " +
            "WEVNSM=1.2.3; WNMCID=cid-fixture; __csrf=csrf-fixture; _ntes_nnid=nnid-fixture; " +
            "_ntes_nuid=nuid-fixture; appver=3.1.35; versioncode=205293; channel=fixture; clientSign=sign-fixture; " +
            "sDeviceId=fallback-device; mobilename=fallback-model; os=pc; osver=fixture-os; uid=123; vipType=11"
    }
}
