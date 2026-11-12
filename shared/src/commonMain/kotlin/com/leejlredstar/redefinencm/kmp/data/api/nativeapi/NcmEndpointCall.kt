package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import kotlinx.serialization.json.JsonObject

/** Protocol selected by the pinned upstream module, before transport encryption. */
internal enum class NcmEndpointCrypto {
    EAPI,
    WEAPI,
    XEAPI,
    API,
    LINUXAPI,
    NEAPI,
}

/** Operations that cannot be represented as one ordinary encrypted POST. */
internal enum class NcmEndpointAction {
    REQUEST,
    QR_CREATE,
    PLAYLIST_TRACK_ALL,
    AUDIO_MATCH,
    LOCAL_VERSION,
    WEBLOG,
    SCROBBLE_V1,
}

/** Pure response transformations performed by the upstream modules. */
internal enum class NcmEndpointResponseKind {
    IDENTITY,
    USER_DETAIL,
    LOGIN_STATUS,
    LOGIN_QR_KEY,
    LOGIN_QR_CHECK,
    AUDIO_MATCH,
}

/**
 * A NetEase request, or an explicit dispatcher action when [action] is not [NcmEndpointAction.REQUEST].
 *
 * [uri] is the canonical `/api/...` path used as encryption input, not the public service route.
 * It is empty for local or composite actions with no single upstream request. [data] holds the
 * module's JSON values before the transport adds its shared fields, such as `header` or `e_r`.
 * Cookie overrides merge over the caller's credential, and never replace the account wholesale.
 *
 * For [NcmEndpointAction.PLAYLIST_TRACK_ALL], `limit` and `offset` are local pagination controls:
 * the dispatcher sends the remaining fields to `/api/v6/playlist/detail`, slices its track IDs,
 * then sends a JSON **string** in `c` to `/api/v3/song/detail`, using EAPI for both requests.
 * [NcmEndpointAction.AUDIO_MATCH] is an unencrypted GET with [data] as query parameters.
 * [NcmEndpointAction.WEBLOG] and [NcmEndpointAction.SCROBBLE_V1] are owned by the reporting dispatcher.
 */
internal data class NcmEndpointCall(
    val uri: String,
    val data: JsonObject,
    val crypto: NcmEndpointCrypto = NcmEndpointCrypto.EAPI,
    val cookieOverrides: Map<String, String> = emptyMap(),
    val domain: String? = null,
    val responseKind: NcmEndpointResponseKind = NcmEndpointResponseKind.IDENTITY,
    val action: NcmEndpointAction = NcmEndpointAction.REQUEST,
)
