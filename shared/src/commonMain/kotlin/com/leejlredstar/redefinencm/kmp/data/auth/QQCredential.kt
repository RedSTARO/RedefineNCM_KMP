package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.QQGatewayCredential
import kotlinx.serialization.json.Json

/**
 * A QQ Music account as the `L-1124/QQMusicApi` web gateway understands it.
 *
 * The gateway reads the caller's account from request cookies named after its own `Credential`
 * fields: `musicid` and `musickey` are required together, the rest let it renew the key. It does
 * not read the cookie names `y.qq.com` sets in a browser, so a pasted browser cookie is translated
 * here — `uin` → `musicid` (minus the `o` prefix and zero padding QQ writes), `qm_keyst` →
 * `musickey`, and the `psrf_*` / `wx*` pairs onto the renewal fields. The stored form is always
 * [toCookieHeader], which is what every QQ request sends.
 */
data class QQCredential(
    val musicId: Long,
    val musicKey: String,
    val openId: String = "",
    val refreshToken: String = "",
    val accessToken: String = "",
    val expiredAt: Long = 0L,
    val unionId: String = "",
    val strMusicId: String = "",
    val refreshKey: String = "",
    /**
     * The account's encrypted UIN, which QQ's profile routes take instead of the number. The
     * gateway does not read it from a cookie; it travels in the stored form only so the accounts
     * page can ask for the account's name.
     */
    val encryptUin: String = "",
) {
    val isSignedIn: Boolean get() = musicId > 0 && musicKey.isNotBlank()

    /** Whether the gateway has what it needs to renew [musicKey] once it expires. */
    val canRefresh: Boolean get() = refreshToken.isNotBlank() || refreshKey.isNotBlank()

    fun toCookieHeader(): String = buildList {
        add("musicid=$musicId")
        add("musickey=$musicKey")
        if (openId.isNotBlank()) add("openid=$openId")
        if (refreshToken.isNotBlank()) add("refresh_token=$refreshToken")
        if (accessToken.isNotBlank()) add("access_token=$accessToken")
        if (expiredAt > 0) add("expired_at=$expiredAt")
        if (unionId.isNotBlank()) add("unionid=$unionId")
        if (strMusicId.isNotBlank() && strMusicId != musicId.toString()) add("str_musicid=$strMusicId")
        if (refreshKey.isNotBlank()) add("refresh_key=$refreshKey")
        if (encryptUin.isNotBlank()) add("$EncryptUinCookie=$encryptUin")
    }.joinToString("; ")

    companion object {
        /**
         * Reads any of the three forms a user or the gateway can hand over: the gateway's
         * `Credential` JSON, a `name=value; …` string in the gateway's names, or a `y.qq.com`
         * browser cookie. Null when no signed-in account can be made of it.
         */
        fun parse(raw: String): QQCredential? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            if (text.startsWith("{")) {
                return runCatching { json.decodeFromString<QQGatewayCredential>(text) }
                    .getOrNull()
                    ?.let(::fromGateway)
                    ?.takeIf { it.isSignedIn }
            }
            return fromCookiePairs(cookiePairs(text))
        }

        fun fromGateway(credential: QQGatewayCredential): QQCredential = QQCredential(
            musicId = credential.musicid,
            musicKey = credential.musickey,
            openId = credential.openid,
            refreshToken = credential.refreshToken,
            accessToken = credential.accessToken,
            expiredAt = credential.expiredAt,
            unionId = credential.unionid,
            strMusicId = credential.strMusicid,
            refreshKey = credential.refreshKey,
            encryptUin = credential.encryptUin,
        )

        private fun fromCookiePairs(pairs: Map<String, String>): QQCredential? {
            val musicId = (pairs["musicid"] ?: pairs["uin"] ?: pairs["wxuin"])
                ?.let(::parseUin)
                ?: return null
            val musicKey = pairs["musickey"] ?: pairs["qm_keyst"] ?: pairs["qqmusic_key"] ?: return null
            if (musicId <= 0 || musicKey.isBlank()) return null
            return QQCredential(
                musicId = musicId,
                musicKey = musicKey,
                openId = pairs["openid"] ?: pairs["psrf_qqopenid"] ?: pairs["wxopenid"] ?: "",
                refreshToken = pairs["refresh_token"]
                    ?: pairs["psrf_qqrefresh_token"]
                    ?: pairs["wxrefresh_token"]
                    ?: "",
                accessToken = pairs["access_token"] ?: pairs["psrf_qqaccess_token"] ?: "",
                expiredAt = (pairs["expired_at"] ?: pairs["psrf_access_token_expiresAt"])
                    ?.toLongOrNull()
                    ?: 0L,
                unionId = pairs["unionid"] ?: pairs["psrf_qqunionid"] ?: pairs["wxunionid"] ?: "",
                strMusicId = pairs["str_musicid"] ?: "",
                refreshKey = pairs["refresh_key"] ?: "",
                encryptUin = pairs[EncryptUinCookie] ?: pairs["euin"] ?: "",
            )
        }

        /** `o0123456789` → 123456789. */
        private fun parseUin(value: String): Long? =
            value.trim().removePrefix("o").trimStart('0').ifEmpty { "0" }.toLongOrNull()

        private fun cookiePairs(text: String): Map<String, String> =
            text.split(';', '\n')
                .mapNotNull { pair ->
                    val at = pair.indexOf('=')
                    if (at <= 0) return@mapNotNull null
                    pair.substring(0, at).trim() to pair.substring(at + 1).trim()
                }
                .filter { (_, value) -> value.isNotEmpty() }
                .toMap()

        /** The name [encryptUin] is stored under; one the gateway ignores. */
        private const val EncryptUinCookie = "encrypt_uin"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
}
