package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module

actual fun platformModule() = module {
    // Ktor HttpClient (OkHttp engine) configured with base URL + realIP + cookie from settings.
    single<HttpClient> {
        val settings = get<PlatformSettings>()
        HttpClientFactory.create(
            baseUrl = settings.getString(SettingKeys.SERVER, "http://ncm.tryagain.icu/"),
            realIP = "192.168.1.1",
            cookie = settings.getString(SettingKeys.COOKIE, ""),
            engineFactory = OkHttp,
        )
    }

    // PlatformSettings backed by DataStore (needs the app Context provided via androidContext()).
    single { PlatformSettings(get()) }
}
