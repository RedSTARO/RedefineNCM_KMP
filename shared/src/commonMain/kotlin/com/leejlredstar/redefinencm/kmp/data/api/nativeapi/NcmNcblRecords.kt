package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pure record encoding from util/ncbl.js at 4a7d557326d16d2f1e5172ee69c9471eb3a50173,
 * attributed there to folltoshe/netease-report-listen-song (AGPLv3).
 * Adapted to Kotlin and modified on 2026-11-13; AGPL-3.0-only, see THIRD_PARTY_NOTICES.md.
 */
internal object NcmNcblRecords {
    fun record(timeSeconds: Long, action: String, data: JsonObject): String =
        "$timeSeconds\u0001$action\u0001$data"

    fun plv(ctx: NcmNcblContext, params: NcmNcblParams, nowMillis: Long): JsonObject {
        val sourceId = params.sourceId.ifEmpty { params.songId.toString() }
        val addRefer = "[F:63][$nowMillis#933#${ctx.app.version}#${ctx.app.versionCode}#c9156c3][e][2][23]" +
            "[cell_pc_songlist_song:2|page_pc_songlist_songflow|page_mine_like_music]" +
            "[${params.songId}:song:x:x|:::|$sourceId:list::]"
        val multiRefers = listOf(
            "[F:26][s][18][_ai]",
            "[F:26][s][12][_ai]",
            "[F:63][$nowMillis#933#${ctx.app.version}#${ctx.app.versionCode}#c9156c3][e][2][8]" +
                "[cell_pc_main_tab_entrance:6|page_pc_main_tab]" +
                // Upstream trace data, not app copy: retain its original service-side page name.
                "[\u6211\u559c\u6b22\u7684\u97f3\u4e50:spm::|:::]",
            "[F:26][s][5][_ai]",
            "[F:26][s][0][_ai]",
        )
        return buildJsonObject {
            put("mode", "circulation")
            put("download", 0)
            put("alg", "")
            put("status", "front")
            put("id", params.songId.toString())
            put("bitrate", params.bitrate)
            put("type", "song")
            put("is_listentogether", 0)
            put("source", params.source.ifEmpty { "list" })
            put("is_heart", 0)
            put("resource_ratio", "")
            put("resource_time", params.totalSeconds)
            put("musiceffect_id", "")
            put("app_mode", 2)
            put("bitrate_level", params.level.ifEmpty { "exhigh" })
            put("_addrefer", addRefer)
            put("_multirefers", JsonArray(multiRefers.map(::JsonPrimitive)))
            put("vipType", ctx.auth.vipType)
            put("fee", 1)
            put("file", 4)
            put("rightSource", 0)
            put("sourceId", sourceId)
            put("sourcetype", "track")
            put("libra_abt", "")
            put("channel", ctx.app.channel)
            put("curStartChannel", "")
        }
    }

    fun pld(ctx: NcmNcblContext, params: NcmNcblParams, nowMillis: Long): JsonObject {
        val sourceId = params.sourceId.ifEmpty { params.songId.toString() }
        val played = minOf(params.playedSeconds, params.totalSeconds)
        val addRefer = "[F:63][$nowMillis#616#${ctx.app.version}#${ctx.app.versionCode}#c9156c3][e][2][92]" +
            "[btn_pc_cover_play|cell_pc_songlist_song:6|page_pc_songlist_songflow|page_mine_like_music]" +
            "[:::|${params.songId}:song:x:x|:::|$sourceId:list::]"
        val multiRefers = listOf(
            "[F:26][s][87][_ai]",
            "[F:26][s][81][_ai]",
            "[F:26][s][75][_ai]",
            "[F:26][s][69][_ai]",
            "[F:26][s][63][_ai]",
        )
        return buildJsonObject {
            put("mode", "circulation")
            put("download", 0)
            put("alg", "")
            put("status", "front")
            put("id", params.songId.toString())
            put("time", played)
            put("type", "song")
            put("is_listentogether", 0)
            put("source", params.source.ifEmpty { "list" })
            put("is_heart", 0)
            put("realtime", played)
            put("resource_ratio", "")
            put("resource_time", params.totalSeconds)
            put("musiceffect_id", "1001")
            put("app_mode", 1)
            put("lyriceffect", "default")
            put("displayMode", "classic")
            put("bitrate", params.bitrate)
            put("bitrate_level", params.level.ifEmpty { "exhigh" })
            put("_addrefer", addRefer)
            put("_multirefers", JsonArray(multiRefers.map(::JsonPrimitive)))
            put("vipType", ctx.auth.vipType)
            put("fee", 8)
            put("file", 4)
            put("rightSource", 0)
            put("sourceId", sourceId)
            put("sourcetype", "track")
            put("end", "interrupt")
            put("libra_abt", "")
            put("channel", ctx.app.channel)
            put("curStartChannel", "")
        }
    }
}
