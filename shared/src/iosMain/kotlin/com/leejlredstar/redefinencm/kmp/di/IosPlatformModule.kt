package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.db.DatabaseDriverFactory
import com.leejlredstar.redefinencm.kmp.player.IosAVPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module

actual fun platformModule() = module {
    // Ktor HttpClient (Darwin engine). Cookie is read fresh per request, so QR login takes
    // effect without relaunch; only a server (base URL) change still needs a relaunch.
    single<HttpClient> {
        val settings = get<PlatformSettings>()
        HttpClientFactory.create(
            baseUrl = settings.getString(SettingKeys.SERVER, "http://ncm.tryagain.icu/"),
            realIP = "192.168.1.1",
            cookieProvider = { settings.getString(SettingKeys.COOKIE, "") },
            engineFactory = Darwin,
        )
    }

    // PlatformSettings backed by NSUserDefaults
    single { PlatformSettings() }

    // SQLDelight driver (Documents-directory SQLite via NativeSqliteDriver).
    single { DatabaseDriverFactory() }

    // Real AVPlayer-backed audio playback, overriding the shared InMemoryPlatformPlayer.
    single<PlatformPlayer> { IosAVPlayer(get(), get()) }
}
