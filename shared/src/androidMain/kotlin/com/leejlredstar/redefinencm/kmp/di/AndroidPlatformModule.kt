package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.db.DatabaseDriverFactory
import com.leejlredstar.redefinencm.kmp.player.ExoPlayerPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() = module {
    // Ktor HttpClient (OkHttp engine) configured with base URL + realIP + cookie from settings.
    single<HttpClient> {
        val settings = get<PlatformSettings>()
        HttpClientFactory.create(
            baseUrl = settings.getString(SettingKeys.SERVER, "http://ncm.tryagain.icu/"),
            realIP = "192.168.1.1",
            cookieProvider = { settings.getString(SettingKeys.COOKIE, "") },
            engineFactory = OkHttp,
        )
    }

    // PlatformSettings backed by DataStore (needs the app Context provided via androidContext()).
    single { PlatformSettings(get()) }

    // SQLDelight driver (needs Context for the Android SQLite helper).
    single { DatabaseDriverFactory(androidContext()) }

    // ExoPlayer-backed PlatformPlayer — overrides the InMemoryPlatformPlayer in sharedModule.
    // Must be resolved on the main thread (ExoPlayer requirement); Koin singleton lives for the
    // app lifetime. PlaybackService wraps the same ExoPlayer instance in a MediaSession.
    single<PlatformPlayer> { ExoPlayerPlatformPlayer(androidContext(), get(), get()) }
}
