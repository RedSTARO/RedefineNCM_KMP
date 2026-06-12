package com.leejlredstar.redefinencm.kmp.data.api

import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * NeteaseCloudMusicApi service interface implemented via Ktor.
 * Ported from the original Retrofit NCMApi with the same endpoint structure.
 */
class NCMApi(private val client: HttpClient) {

    // ── Account ──

    suspend fun userAccount(): UserAccount =
        client.get("/user/account").body()

    suspend fun userDetail(uid: Long): UserDetail =
        client.get("/user/detail") { parameter("uid", uid) }.body()

    // ── Login ──

    suspend fun loginStatus(cookie: String): LoginStatus =
        client.post("/login/status") {
            parameter("cookie", cookie)
        }.body()

    suspend fun loginQrKey(): LoginQrKey =
        client.post("/login/qr/key").body()

    suspend fun loginQrCreate(key: String, qrimg: Boolean): LoginQrCreate =
        client.post("/login/qr/create") {
            parameter("key", key)
            parameter("qrimg", qrimg)
        }.body()

    suspend fun loginQrCheck(key: String): LoginQrCheck =
        client.post("/login/qr/check") {
            parameter("key", key)
        }.body()

    suspend fun dailysignin(type: Int): Dailysignin =
        client.get("/daily_signin") { parameter("type", type) }.body()

    // ── Playlist ──

    suspend fun userPlaylist(uid: Long): UserPlaylist =
        client.get("/user/playlist") { parameter("uid", uid) }.body()

    suspend fun playlistTrackAll(id: Long): PlaylistTrackAll =
        client.get("/playlist/track/all") { parameter("id", id) }.body()

    suspend fun playlistDetail(id: Long): PlaylistDetail =
        client.get("/playlist/detail") { parameter("id", id) }.body()

    suspend fun playlistUpdatePlaycount(id: Long): PlaylistUpdatePlayCount =
        client.get("/playlist/update/playcount") { parameter("id", id) }.body()

    // ── Song ──

    suspend fun songUrlV1(id: List<Long>, level: String): SongUrlV1 =
        client.get("/song/url/v1") {
            parameter("id", id.joinToString(","))
            parameter("level", level)
        }.body()

    suspend fun songDetail(ids: List<Long>): SongDetail =
        client.get("/song/detail") {
            parameter("ids", ids.joinToString(","))
        }.body()

    // ── Search ──

    suspend fun search(keywords: String, limit: Int = 30): SearchResult =
        client.get("/cloudsearch") {
            parameter("keywords", keywords)
            parameter("limit", limit)
        }.body()

    suspend fun searchSuggest(keywords: String, type: String = "mobile"): SearchSuggest =
        client.get("/search/suggest") {
            parameter("keywords", keywords)
            parameter("type", type)
        }.body()

    // ── Recommend ──

    suspend fun recommendSongs(): RecommendSongs =
        client.get("/recommend/songs").body()

    suspend fun recommendResource(): RecommendResource =
        client.get("/recommend/resource").body()

    // ── Lyric ──

    suspend fun lyric(id: Long): Lyric =
        client.get("/lyric") { parameter("id", id) }.body()

    // ── Like ──

    suspend fun like(id: Long?): Like =
        client.post("/like") { id?.let { parameter("id", it) } }.body()

    suspend fun likelist(uid: Long): LikeList =
        client.post("/likelist") { parameter("uid", uid) }.body()

    // ── Comment ──

    suspend fun commentMusic(id: Long): CommentMusic =
        client.get("/comment/music") { parameter("id", id) }.body()

    // ── Version ──

    suspend fun innerVersion(url: String): InnerVersion =
        client.get(url).body()
}
