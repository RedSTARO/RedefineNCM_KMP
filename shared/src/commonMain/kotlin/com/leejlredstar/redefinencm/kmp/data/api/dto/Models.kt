package com.leejlredstar.redefinencm.kmp.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val klyric: LyricLrc? = null,
    val tlyric: LyricLrc? = null,
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

// ── Comments ──

@Serializable
data class CommentMusic(
    val isMusician: Boolean = false,
    val userId: Long = 0,
    val topComments: List<CommentMusicComments> = emptyList(),
    val moreHot: Boolean = false,
    val hotComments: List<CommentMusicComments> = emptyList(),
    val comments: List<CommentMusicComments> = emptyList(),
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
