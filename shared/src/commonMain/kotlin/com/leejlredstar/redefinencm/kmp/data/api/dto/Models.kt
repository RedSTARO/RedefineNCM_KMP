package com.leejlredstar.redefinencm.kmp.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── User / Account ──

@Serializable
data class UserAccount(
    val code: Int,
    val account: UserAccountDetail? = null,
    val profile: UserDetailProfile? = null,
)

@Serializable
data class UserAccountDetail(
    val id: Long,
    val userName: String = "",
    val type: Int = 0,
    val status: Int = 0,
)

@Serializable
data class UserDetail(
    val code: Int,
    val level: Int = 0,
    val listenSongs: Long = 0,
    val profile: UserDetailProfile = UserDetailProfile(),
)

@Serializable
data class UserDetailProfile(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    val backgroundUrl: String = "",
    val signature: String = "",
    val gender: Int = 0,
)

@Serializable
data class UserLevelResponse(
    val code: Int = 0,
    val full: Boolean = false,
    val data: UserLevelData? = null,
    val message: String? = null,
    val msg: String? = null,
)

@Serializable
data class UserLevelData(
    val userId: Long = 0,
    val info: String = "",
    val progress: Double = 0.0,
    val nextPlayCount: Long = 0,
    val nextLoginCount: Long = 0,
    val nowPlayCount: Long = 0,
    val nowLoginCount: Long = 0,
    val level: Int = 0,
)

@Serializable
data class UserRecordResponse(
    val code: Int = 0,
    val weekData: List<UserRecordEntry> = emptyList(),
    val allData: List<UserRecordEntry> = emptyList(),
    val message: String? = null,
    val msg: String? = null,
)

@Serializable
data class UserRecordEntry(
    val song: SongDetailSongs = SongDetailSongs(),
    val playCount: Long = 0,
    val score: Long = 0,
)

@Serializable
data class RecentSongsResponse(
    val code: Int = 0,
    val data: RecentSongsData? = null,
    val message: String? = null,
    val msg: String? = null,
)

@Serializable
data class RecentSongsData(
    val total: Long = 0,
    val list: List<RecentSongEntry> = emptyList(),
)

@Serializable
data class RecentSongEntry(
    val resourceId: Long = 0,
    val playTime: Long = 0,
    val resourceType: String = "",
    val data: SongDetailSongs = SongDetailSongs(),
    val banned: Boolean = false,
    val multiTerminalInfo: JsonElement? = null,
)

// ── Login ──

@Serializable
data class LoginStatus(
    val code: Int = 0,
    val data: LoginStatusData? = null,
)

@Serializable
data class LoginStatusData(
    val code: Int = 0,
    val profile: UserDetailProfile? = null,
)

@Serializable
data class LoginQrKey(
    val code: Int = 0,
    val data: LoginQrKeyData = LoginQrKeyData(),
)

@Serializable
data class LoginQrKeyData(
    val unikey: String = "",
)

@Serializable
data class LoginQrCreate(
    val code: Int = 0,
    val data: LoginQrCreateData = LoginQrCreateData(),
)

@Serializable
data class LoginQrCreateData(
    val qrimg: String = "",
    val qrurl: String = "",
)

@Serializable
data class LoginQrCheck(
    val code: Int = 0,
    val message: String = "",
    val cookie: String = "",
)

@Serializable
data class Dailysignin(
    val code: Int = 0,
)

@Serializable
data class VipGrowthPointResponse(
    val code: Int = 0,
    val data: JsonElement? = null,
    val message: String? = null,
    val msg: String? = null,
)

// ── Playlist ──

@Serializable
data class UserPlaylist(
    val code: Int = 0,
    val more: Boolean = false,
    val playlist: List<UserPlaylistEach> = emptyList(),
)

@Serializable
data class UserPlaylistEach(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String = "",
    val trackCount: Long = 0,
    val playCount: Long = 0,
    val specialType: Int = 0,
    val creator: UserPlaylistCreator = UserPlaylistCreator(),
    val description: String = "",
)

@Serializable
data class UserPlaylistCreator(
    val nickname: String = "",
    val avatarUrl: String = "",
    val userId: Long = 0,
)

@Serializable
data class PlaylistDetail(
    val code: Int = 0,
    val playlist: PlaylistDetailPlaylist? = null,
)

@Serializable
data class PlaylistDetailPlaylist(
    val id: Long = 0,
    val name: String = "",
    val coverImgUrl: String = "",
    val trackCount: Long = 0,
    val playCount: Long = 0,
    val description: String = "",
    val creator: UserPlaylistCreator = UserPlaylistCreator(),
    val tags: List<String> = emptyList(),
)

@Serializable
data class PlaylistTrackAll(
    val code: Int = 0,
    val songs: List<SongDetailSongs> = emptyList(),
)

@Serializable
data class PlaylistUpdatePlayCount(
    val code: Int = 0,
)

@Serializable
data class IntelligenceListResponse(
    val code: Int = 0,
    val data: List<IntelligenceListItem>? = null,
    val message: String? = null,
    val msg: String? = null,
)

@Serializable
data class IntelligenceListItem(
    val id: Long = 0,
    val alg: String? = null,
    val recommended: Boolean = false,
    val songInfo: SongDetailSongs? = null,
)

// ── Songs ──

@Serializable
data class SongUrlV1(
    val code: Int = 0,
    val data: List<SongUrlV1Data> = emptyList(),
)

@Serializable
data class SongUrlV1Data(
    val id: Long = 0,
    val url: String = "",
    val level: String = "",
    val time: Long = 0,
    val size: Long = 0,
)

@Serializable
data class SongDetail(
    val code: Int = 0,
    val songs: List<SongDetailSongs> = emptyList(),
)

@Serializable
data class SongDetailSongs(
    val id: Long = 0,
    val name: String = "",
    @SerialName("ar") val ar: List<SongArtist> = emptyList(),
    @SerialName("al") val al: SongAlbum = SongAlbum(),
    val dt: Long = 0,
    val mv: Long = 0,
    /** NetEase's fee class: 1 is VIP-only, 4 is sold by album; 0 and 8 are free to play. */
    val fee: Int = 0,
)

@Serializable
data class SongArtist(
    val id: Long = 0,
    val name: String = "",
)

@Serializable
data class SongAlbum(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
)

/** Minimal DTO surface used from the deliberately broad `/song/wiki/summary` response. */
@Serializable
data class SongWikiSummaryResponse(
    val code: Int = 0,
    val data: SongWikiSummaryData? = null,
    val message: String? = null,
    val msg: String? = null,
)

/** `/song/dynamic/cover` returns a video resource, not an image URL. */
@Serializable
data class SongDynamicCoverResponse(
    val code: Int = 0,
    val data: SongDynamicCoverData? = null,
    val message: String? = null,
    val msg: String? = null,
)

@Serializable
data class SongDynamicCoverData(
    val videoPlayUrl: String? = null,
    // Some compatible backends expose one of these older aliases.
    val videoUrl: String? = null,
    val url: String? = null,
)

fun SongDynamicCoverResponse.resolvedVideoUrl(): String? = sequenceOf(
    data?.videoPlayUrl,
    data?.videoUrl,
    data?.url,
).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .firstOrNull { candidate ->
        candidate.startsWith("https://", ignoreCase = true) ||
            candidate.startsWith("http://", ignoreCase = true)
    }

@Serializable
data class SongWikiSummaryData(
    val blocks: List<SongWikiBlock>? = null,
)

@Serializable
data class SongWikiBlock(
    val showType: String? = null,
    val uiElement: SongWikiUiElement? = null,
    val creatives: List<SongWikiCreative>? = null,
)

@Serializable
data class SongWikiCreative(
    val creativeType: String? = null,
    val uiElement: SongWikiUiElement? = null,
    val resources: List<SongWikiResource>? = null,
)

@Serializable
data class SongWikiResource(
    val resourceType: String? = null,
    val uiElement: SongWikiUiElement? = null,
)

@Serializable
data class SongWikiUiElement(
    val mainTitle: SongWikiTitle? = null,
    val textLinks: List<SongWikiTextLink>? = null,
    val descriptions: List<SongWikiDescription>? = null,
    val buttons: List<SongWikiButton>? = null,
    val images: List<SongWikiImage>? = null,
)

@Serializable
data class SongWikiTitle(
    val title: String? = null,
)

@Serializable
data class SongWikiTextLink(
    val text: String? = null,
)

@Serializable
data class SongWikiDescription(
    val description: String? = null,
)

@Serializable
data class SongWikiButton(
    val text: String? = null,
)

@Serializable
data class SongWikiImage(
    val title: String? = null,
)

// ── Audio recognition ──

@Serializable
data class AudioMatch(
    val code: Int = 0,
    val data: AudioMatchData? = null,
)

@Serializable
data class AudioMatchData(
    val type: Int = -1,
    val queryId: String = "",
    val result: List<AudioMatchResult>? = null,
    val noMatchReason: Int? = null,
)

@Serializable
data class AudioMatchResult(
    val startTime: Long = 0,
    val song: AudioMatchSong = AudioMatchSong(),
)

/** `/audio/match` still returns the legacy long-form song field names. */
@Serializable
data class AudioMatchSong(
    val id: Long = 0,
    val name: String = "",
    val artists: List<SongArtist> = emptyList(),
    val album: SongAlbum = SongAlbum(),
    val duration: Long = 0,
    val mvid: Long = 0,
    val fee: Int = 0,
)

// ── Playback reporting ──

/**
 * `/scrobble/v1` can return different `details` shapes for PLV and PLD failures, so the
 * payload must remain untyped until the response code is known.
 */
@Serializable
data class ScrobbleV1Response(
    val code: Int = 0,
    val data: String? = null,
    val msg: String? = null,
    val details: JsonElement? = null,
)

@Serializable
data class WeblogResponse(
    val code: Int = 0,
    val data: JsonElement? = null,
    val details: JsonElement? = null,
    val msg: String? = null,
    val message: String? = null,
)

/** `/relay/play/state/submit` forwards an upstream response whose `data` schema is not fixed. */
@Serializable
data class PlayStateSubmitResponse(
    val code: Int = 0,
    val data: JsonElement? = null,
    val msg: String? = null,
    val message: String? = null,
)

// ── Search ──

@Serializable
data class SearchResult(
    val code: Int = 0,
    val result: SearchResultData? = null,
)

@Serializable
data class SearchResultData(
    val songs: List<SongDetailSongs> = emptyList(),
    val songCount: Long = 0,
)

// ── Artist and album ──

/** `/artist/detail`: who the artist is. Their songs and albums come from two other calls. */
@Serializable
data class ArtistDetailResponse(
    val code: Int = 0,
    val data: ArtistDetailData = ArtistDetailData(),
)

@Serializable
data class ArtistDetailData(
    val artist: ArtistProfile = ArtistProfile(),
)

@Serializable
data class ArtistProfile(
    val id: Long = 0,
    val name: String = "",
    val cover: String = "",
    val avatar: String = "",
    val briefDesc: String = "",
    val albumSize: Int = 0,
    val musicSize: Int = 0,
    val alias: List<String> = emptyList(),
    val transNames: List<String> = emptyList(),
)

/** `/artist/top/song`: the artist's fifty most played songs. */
@Serializable
data class ArtistTopSongs(
    val code: Int = 0,
    val songs: List<SongDetailSongs> = emptyList(),
)

/** `/artist/album`: one page of the artist's albums, newest first. */
@Serializable
data class ArtistAlbums(
    val code: Int = 0,
    val hotAlbums: List<AlbumSummary> = emptyList(),
    val more: Boolean = false,
)

@Serializable
data class AlbumSummary(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val publishTime: Long = 0,
    val size: Int = 0,
)

/** `/album`: the album and its songs. */
@Serializable
data class AlbumDetail(
    val code: Int = 0,
    val album: AlbumInfo = AlbumInfo(),
    val songs: List<SongDetailSongs> = emptyList(),
)

@Serializable
data class AlbumInfo(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val publishTime: Long = 0,
    val size: Int = 0,
    val company: String? = null,
    val description: String? = null,
    val artists: List<SongArtist> = emptyList(),
)

/** The hot-search chart, from `/search/hot/detail`. */
@Serializable
data class SearchHotDetail(
    val code: Int = 0,
    val data: List<SearchHotItem> = emptyList(),
)

@Serializable
data class SearchHotItem(
    val searchWord: String = "",
    val score: Long = 0,
    /** A short line about why the word is hot; often empty. */
    val content: String = "",
)

@Serializable
data class SearchSuggest(
    val code: Int = 0,
    val result: SearchSuggestData? = null,
)

@Serializable
data class SearchSuggestData(
    val allMatch: List<SearchSuggestMatch> = emptyList(),
)

@Serializable
data class SearchSuggestMatch(
    val keyword: String = "",
)

// ── Recommend ──

@Serializable
data class RecommendSongs(
    val code: Int = 0,
    val data: RecommendSongsData = RecommendSongsData(),
)

@Serializable
data class RecommendSongsData(
    val dailySongs: List<SongDetailSongs> = emptyList(),
)

@Serializable
data class RecommendResource(
    val code: Int = 0,
    val featureFirst: Boolean = false,
    val haveRcmdSongs: Boolean = false,
    val recommend: List<RecommendResourceRecommend> = emptyList(),
)

@Serializable
data class RecommendResourceRecommend(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val playcount: Long = 0,
)

// ── Lyric ──

@Serializable
data class Lyric(
    val sgc: Boolean = false,
    val sfy: Boolean = false,
    val qfy: Boolean = false,
    val code: Int = 0,
    val lrc: LyricLrc? = null,
    val yrc: LyricLrc? = null,
    val tlyric: LyricLrc? = null,
    val romalrc: LyricLrc? = null,
)

@Serializable
data class LyricLrc(
    val version: Int = 0,
    val lyric: String = "",
)

// ── Like ──

@Serializable
data class Like(
    val code: Int = 0,
)

@Serializable
data class LikeList(
    val code: Int = 0,
    val ids: List<Long> = emptyList(),
)

@Serializable
data class SongLikeCheck(
    val code: Int = 0,
    val ids: List<Long> = emptyList(),
    val message: String? = null,
    val msg: String? = null,
)

// ── Comments ──

@Serializable
data class CommentMusic(
    val code: Int = 0,
    val isMusician: Boolean = false,
    val userId: Long = 0,
    val topComments: List<CommentMusicComments> = emptyList(),
    val moreHot: Boolean = false,
    val hotComments: List<CommentMusicComments> = emptyList(),
    val comments: List<CommentMusicComments> = emptyList(),
    /** All comments on the song, of which the response carries one page. */
    val total: Long = 0,
    /** Whether there are latest comments after this page. */
    val more: Boolean = false,
)

/** One page of a song's hot comments, from `/comment/hot`. */
@Serializable
data class CommentHot(
    val code: Int = 0,
    val hotComments: List<CommentMusicComments> = emptyList(),
    val hasMore: Boolean = false,
    val total: Long = 0,
)

@Serializable
data class CommentMusicComments(
    val user: UserDetailProfile = UserDetailProfile(),
    val commentId: Long = 0,
    val content: String = "",
    val richContent: String = "",
    val time: Long = 0,
    val timeStr: String = "",
    val likedCount: Long = 0,
)

// ── Version ──

@Serializable
data class InnerVersion(
    val code: Int = 0,
    val data: InnerVersionData = InnerVersionData(),
)

@Serializable
data class InnerVersionData(
    val version: String = "",
)
