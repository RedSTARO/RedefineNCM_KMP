package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.api.nativeapi.NativeNcmClientFactory
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory

internal fun createNcmHttpClient(
    settings: PlatformSettings,
    engineFactory: HttpClientEngineFactory<*>,
    cookieTransport: HttpClientFactory.CookieTransport = HttpClientFactory.CookieTransport.HEADER,
): HttpClient = if (cookieTransport == HttpClientFactory.CookieTransport.QUERY_PARAMETER) {
    // Fetch cannot set the Cookie/UA headers the upstream protocol requires. Only the Web
    // target retains a configurable API server; native targets ignore legacy saved addresses.
    HttpClientFactory.create(
        baseUrl = settings.getString(SettingKeys.SERVER, DEFAULT_NCM_SERVER),
        realIP = DEFAULT_REAL_IP,
        cookieProvider = { settings.getString(SettingKeys.COOKIE, "") },
        engineFactory = engineFactory,
        cookieTransport = cookieTransport,
    )
} else {
    NativeNcmClientFactory.create(settings, engineFactory)
}

/** Web-only API-server default; native targets neither read it nor offer a server editor. */
internal const val DEFAULT_NCM_SERVER = "https://ncm.tryagain.icu/"
private const val DEFAULT_REAL_IP = "192.168.1.1"
