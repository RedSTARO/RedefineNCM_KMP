package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.AmlldbApi
import com.leejlredstar.redefinencm.kmp.data.api.ExternalHttpClient
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.auth.CredentialStore
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodRegistry
import com.leejlredstar.redefinencm.kmp.data.auth.NeteaseCookieLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.NeteaseCredentialSlot
import com.leejlredstar.redefinencm.kmp.data.auth.NeteaseQrLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptor
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptorRegistry
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderServerSetting
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredentialSlot
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.QQPhoneCodeLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.QQQrLoginMethod
import com.leejlredstar.redefinencm.kmp.ui.login.CredentialTextLoginPresenter
import com.leejlredstar.redefinencm.kmp.ui.login.LoginPresenterRegistry
import com.leejlredstar.redefinencm.kmp.ui.login.PhoneCodeLoginPresenter
import com.leejlredstar.redefinencm.kmp.ui.login.QrLoginPresenter
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.NeteaseProvider
import com.leejlredstar.redefinencm.kmp.data.provider.QQProvider
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.data.db.DatabaseDriverFactory
import com.leejlredstar.redefinencm.kmp.download.LocalMediaAssets
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.lyric.LyricResolver
import com.leejlredstar.redefinencm.kmp.player.InMemoryPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlaybackReportingCoordinator
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestorer
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.LoginViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.SongRecognitionViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import org.koin.mp.Lockable

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
private val koinInitLock = Lockable()

fun initKoin(config: KoinAppDeclaration? = null) {
    KoinPlatformTools.synchronized(koinInitLock) {
        if (KoinPlatformTools.defaultContext().getOrNull() != null) return@synchronized
        startKoin {
            config?.invoke(this)
            modules(sharedModule, platformModule())
        }
    }
}

val sharedModule = module {
    // API
    single { NCMApi(get()) }
    single { AmlldbApi(get()) }
    // QQ Music talks to a backend the user self-hosts, addressed by a setting rather than a
    // constant so it works the same way the NetEase server address already does.
    single {
        val settings = get<PlatformSettings>()
        QQMusicApi(
            client = get<ExternalHttpClient>().client,
            baseUrl = {
                settings.getStringAsync(SettingKeys.QQ_SERVER, SettingKeys.QQ_SERVER_DEFAULT)
            },
            cookie = { settings.getStringAsync(SettingKeys.QQ_COOKIE, "") },
        )
    }

    // Database — DatabaseDriverFactory is provided by platformModule()
    single { AppDatabase(get<DatabaseDriverFactory>().createDriver()) }

    // Repository
    single { Repository(get(), get()) }

    // Credentials — one slot per provider, holding that provider's change rules. NetEase's
    // restarts account work through the main view model, resolved lazily so the slot does not
    // pull the view-model graph up at startup.
    single {
        val koin = getKoin()
        NeteaseCredentialSlot(get()) { koin.get<MainViewModel>().refreshAccount() }
    }
    single { QQCredentialSlot(get()) }
    single { CredentialStore(listOf(get<NeteaseCredentialSlot>(), get<QQCredentialSlot>())) }

    // What the login and settings pages say about each provider. A new provider registers a
    // descriptor here, a credential slot above, and its login methods below.
    single {
        ProviderLoginDescriptorRegistry(
            listOf(
                ProviderLoginDescriptor(
                    provider = MusicProviderId.NETEASE,
                    introduction = "RedefineNCM 是第三方网易云音乐客户端。登录后可以使用你的歌单、每日推荐和喜欢的音乐；不登录也可以搜索和播放。",
                    accountLabel = "网易云音乐账号",
                    signedOutHint = "登录后可以查看歌单、每日推荐和喜欢的音乐",
                    logoutWarning = "退出后「我的」、每日推荐和喜欢等功能将不可用，已下载的歌曲会保留。",
                    server = ProviderServerSetting(
                        key = SettingKeys.SERVER,
                        default = DEFAULT_NCM_SERVER,
                        label = "服务器地址",
                        appliesWhen = "服务器地址重启后生效",
                    ),
                ),
                ProviderLoginDescriptor(
                    provider = MusicProviderId.QQ,
                    introduction = "登录后 QQ 音乐的搜索和播放使用你账号的权益；不登录也可以搜索，并播放可免费收听的歌曲。",
                    accountLabel = "QQ 音乐账号",
                    signedOutHint = "登录后搜索和播放使用你账号的权益",
                    logoutWarning = "退出后 QQ 音乐按未登录状态搜索和播放。",
                    server = ProviderServerSetting(
                        key = SettingKeys.QQ_SERVER,
                        default = SettingKeys.QQ_SERVER_DEFAULT,
                        label = "QQ 音乐后端地址",
                        appliesWhen = "后端地址立即生效",
                    ),
                ),
            ),
        )
    }

    // Login sources, logic half — the login page renders whatever is registered here for the
    // provider it was opened for. A way to sign in of an existing shape is an entry in this list.
    single {
        LoginMethodRegistry(
            listOf(
                NeteaseQrLoginMethod(get()),
                NeteaseCookieLoginMethod(),
                QQQrLoginMethod(get(), QQQrLoginMethod.Kind.QQ),
                QQQrLoginMethod(get(), QQQrLoginMethod.Kind.WECHAT),
                QQPhoneCodeLoginMethod(get()),
                QQCredentialTextLoginMethod(),
            ),
        )
    }

    // Login sources, UI half — one presenter per shape, in the order their sections appear. A new
    // shape of login is a method interface, a presenter here, and their registrations.
    single {
        LoginPresenterRegistry(
            listOf(
                QrLoginPresenter(),
                PhoneCodeLoginPresenter(),
                CredentialTextLoginPresenter(),
            ),
        )
    }

    // Providers — NetEase is an adapter over the existing Repository, not a rewrite of it. Order
    // here is the order aggregated results are grouped in, so NetEase stays first.
    single {
        MusicProviderRegistry(
            providers = listOf(
                NeteaseProvider(get(), get()),
                QQProvider(get(), get(), get<QQCredentialSlot>()),
            ),
            settings = get(),
        )
    }
    single { LocalMediaAssets(get()) }
    single { LyricResolver(get(), get(), get()) }

    // Download queue — one process-wide queue drives the manager page and row status chips.
    single { SongDownloadManager(get(), get(), get(), get()) }

    // Player — shared in-memory default (no real audio). A platform that implements a real
    // PlatformPlayer should bind it in platformModule() and remove this default (or load with
    // Koin override). NowPlayingViewModel resolves PlatformPlayer from here.
    single<PlatformPlayer> { InMemoryPlatformPlayer() }

    // Queue restoration is process work, not screen work. Platform media services can await it
    // before exposing a transport session.
    single(createdAtStart = true) {
        val koin = getKoin()
        val settings = get<PlatformSettings>()
        PlayerStatusRestorer(
            awaitSettings = { settings.awaitLoaded() },
            playerProvider = { koin.get<PlatformPlayer>() },
            statusLoader = { koin.get<Repository>().getPlayerStatus() },
            onReady = { koin.get<NowPlayingViewModel>() },
            playerDispatcher = kotlinx.coroutines.Dispatchers.Main,
            workerDispatcher = kotlinx.coroutines.Dispatchers.Default,
        )
    }

    // Eager process-wide reporter: playback accounting must not depend on whether a particular
    // screen or ViewModel has already been composed.
    single(createdAtStart = true) { PlaybackReportingCoordinator(get(), get(), get()) }

    // ViewModels
    // One login page per provider; the provider is the injection parameter.
    factory { (provider: MusicProviderId) -> LoginViewModel(provider, get(), get(), get(), get()) }
    // Single —— 与原版单 Activity 共享一个 MainViewModel 一致：各屏共享搜索/歌单/推荐状态，
    // init 中的 UID 解析与播放状态恢复只执行一次。
    single { MainViewModel(get(), get(), get(), get(), get(), get(), get()) }
    // Single — the now-playing state is inherently global (only one song plays at a time).
    // The eager status restorer resolves this singleton after settings and queue restoration, so
    // restored/background playback also resolves lyrics without waiting for a screen composition.
    single { NowPlayingViewModel(get(), get(), get(), get(), get(), get()) }
    // Factory — recording and cancellation are scoped to one pushed recognition page.
    factory { SongRecognitionViewModel(get(), get(), get()) }
}
