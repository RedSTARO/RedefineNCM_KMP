package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.data.db.DatabaseDriverFactory
import com.leejlredstar.redefinencm.kmp.player.InMemoryPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.viewmodel.LoginViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
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
private var koinStarted = false

fun initKoin(config: KoinAppDeclaration? = null) {
    if (koinStarted) return
    koinStarted = true
    startKoin {
        config?.invoke(this)
        modules(sharedModule, platformModule())
    }
}

val sharedModule = module {
    // API
    single { NCMApi(get()) }

    // Database — DatabaseDriverFactory is provided by platformModule()
    single { AppDatabase(get<DatabaseDriverFactory>().createDriver()) }

    // Repository
    single { Repository(get(), get()) }

    // Player — shared in-memory default (no real audio). A platform that implements a real
    // PlatformPlayer should bind it in platformModule() and remove this default (or load with
    // Koin override). NowPlayingViewModel resolves PlatformPlayer from here.
    single<PlatformPlayer> { InMemoryPlatformPlayer() }

    // ViewModels
    factory { LoginViewModel(get(), get()) }
    // Single —— 与原版单 Activity 共享一个 MainViewModel 一致：各屏共享搜索/歌单/推荐状态，
    // init 中的 UID 解析与播放状态恢复只执行一次。
    single { MainViewModel(get(), get(), get()) }
    // 2 args: repo + player; lyricBus uses its default (the LyricBus object, not a Koin dep).
    // Single — the now-playing state is inherently global (only one song plays at a time).
    // Both NowPlayingScreen and FullLyricScreen inject this same instance.
    single { NowPlayingViewModel(get(), get()) }
}
