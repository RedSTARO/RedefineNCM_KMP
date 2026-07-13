package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory

internal fun createNcmHttpClient(
    settings: PlatformSettings,
    engineFactory: HttpClientEngineFactory<*>,
    cookieTransport: HttpClientFactory.CookieTransport = HttpClientFactory.CookieTransport.HEADER,
): HttpClient = HttpClientFactory.create(
    baseUrl = settings.getString(SettingKeys.SERVER, DEFAULT_NCM_SERVER),
    realIP = DEFAULT_REAL_IP,
    cookieProvider = { settings.getString(SettingKeys.COOKIE, "") },
    engineFactory = engineFactory,
    cookieTransport = cookieTransport,
)

private const val DEFAULT_NCM_SERVER = "https://ncm.tryagain.icu/"
private const val DEFAULT_REAL_IP = "192.168.1.1"
