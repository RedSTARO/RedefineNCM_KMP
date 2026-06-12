package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import org.koin.dsl.module

// NOTE: dormant until the wasmJs target is declared in shared/build.gradle.kts (decision D2).
actual fun platformModule() = module {
    // Ktor HttpClient (JS engine) configured with base URL + realIP + cookie from settings.
    single<HttpClient> {
        val settings = get<PlatformSettings>()
        HttpClientFactory.create(
            baseUrl = settings.getString(SettingKeys.SERVER, "http://ncm.tryagain.icu/"),
            realIP = "192.168.1.1",
            cookie = settings.getString(SettingKeys.COOKIE, ""),
            engineFactory = Js,
        )
    }

    // PlatformSettings backed by localStorage
    single { PlatformSettings() }
}
