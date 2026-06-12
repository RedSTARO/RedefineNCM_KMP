package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.player.InMemoryPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.viewmodel.LoginViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Koin DI modules for the shared KMP module.
 * Platform-specific modules (engine factories, settings, player) are provided
 * by each platform's source set.
 */

// Expect: platform provides the HTTP engine factory and settings
expect fun platformModule(): Module

/**
 * Initialise Koin once. Idempotent (safe to call from multiple entry points / repeated calls).
 *
 * Entry points:
 * - Desktop `main()` and iOS `MainViewController()` → `initKoin()`
 * - Android `Application.onCreate()` → `initKoin { androidContext(this@App) }` (the Android
 *   `platformModule()` builds `PlatformSettings(get())`, which resolves the Context provided here)
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    if (GlobalContext.getOrNull() != null) return
    startKoin {
        config?.invoke(this)
        modules(sharedModule, platformModule())
    }
}

val sharedModule = module {
    // API
    single { NCMApi(get()) }

    // Repository
    single { Repository(get()) }

    // Player — shared in-memory default (no real audio). A platform that implements a real
    // PlatformPlayer should bind it in platformModule() and remove this default (or load with
    // Koin override). NowPlayingViewModel resolves PlatformPlayer from here.
    single<PlatformPlayer> { InMemoryPlatformPlayer() }

    // ViewModels
    factory { LoginViewModel(get()) }
    factory { MainViewModel(get(), get()) }
    // 2 args: repo + player; lyricBus uses its default (the LyricBus object, not a Koin dep).
    factory { NowPlayingViewModel(get(), get()) }
}
