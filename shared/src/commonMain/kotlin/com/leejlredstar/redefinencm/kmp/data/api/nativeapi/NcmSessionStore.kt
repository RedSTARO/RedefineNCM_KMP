package com.leejlredstar.redefinencm.kmp.data.api.nativeapi

import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys

/** Session bootstrap material stays private to this installation, outside shared settings exports. */
internal interface NcmSessionStore {
    suspend fun read(key: String): String
    suspend fun write(key: String, value: String)
    suspend fun credential(): String
}

internal class SettingsNcmSessionStore(private val settings: PlatformSettings) : NcmSessionStore {
    override suspend fun read(key: String): String {
        settings.awaitLoaded()
        return settings.getString(key, "")
    }

    override suspend fun write(key: String, value: String) {
        settings.setString(key, value)
        settings.flush()
    }

    override suspend fun credential(): String {
        settings.awaitLoaded()
        return settings.getString(SettingKeys.COOKIE, "")
    }
}

internal const val NCM_DEVICE_ID_KEY = "ncmNativeDeviceId"
internal const val NCM_ANONYMOUS_KEY = "ncmNativeAnonymousCookie"
internal const val NCM_XEAPI_KEY = "ncmNativeXeapiPublicKey"

internal fun ncmCookieMap(cookie: String): Map<String, String> = cookie.split(';').mapNotNull { pair ->
    val separator = pair.indexOf('=')
    if (separator <= 0) null else pair.substring(0, separator).trim() to pair.substring(separator + 1).trim()
}.filter { (key, _) -> key.lowercase() !in setOf(
    "path", "domain", "expires", "max-age", "secure", "httponly", "samesite", "priority", "partitioned",
) }.toMap()

/** Keep the value byte-for-byte, including '+' and '=' in MUSIC_U. Never URL-decode a cookie. */
internal fun ncmCookieHeader(cookie: Map<String, String>): String = cookie.entries.joinToString("; ") { (k, v) ->
    require(k.none { it.code < 0x21 || it.code > 0x7e || it in ";=" }) { "Invalid cookie name" }
    require(v.none { it.code < 0x20 || it.code > 0x7e || it == ';' }) { "Invalid cookie value" }
    "$k=$v"
}
