package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.AmlldbApi
import com.leejlredstar.redefinencm.kmp.data.api.ExternalHttpClient
import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.auth.AccountIdentity
import com.leejlredstar.redefinencm.kmp.data.auth.AccountIdentitySource
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderEnabledSetting
import com.leejlredstar.redefinencm.kmp.data.auth.QQAccountIdentitySource
import com.leejlredstar.redefinencm.kmp.data.auth.ServerCheckResult
import com.leejlredstar.redefinencm.kmp.data.local.LocalAccount
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderRegistration
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderRegistrations
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.viewmodel.AccountsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import com.leejlredstar.redefinencm.kmp.data.auth.NeteaseCookieLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.NeteaseCredentialSlot
import com.leejlredstar.redefinencm.kmp.data.auth.NeteaseQrLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptor
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderServerSetting
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredentialRenewer
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
        val koin = getKoin()
        QQMusicApi(
            client = get<ExternalHttpClient>().client,
            baseUrl = {
                settings.getStringAsync(SettingKeys.QQ_SERVER, SettingKeys.QQ_SERVER_DEFAULT)
            },
            // A browser cannot send the header; asking it to would only fail the request.
            cookie = {
                if (getPlatform().canSendCookieHeader) {
                    settings.getStringAsync(SettingKeys.QQ_COOKIE, "")
                } else {
                    ""
                }
            },
            // Resolved lazily: the renewer calls this API itself.
            onRejected = { rejected -> koin.get<QQCredentialRenewer>().renewAfterRejection(rejected) },
        )
    }
    single {
        val koin = getKoin()
        QQCredentialRenewer(api = { koin.get() }, slot = get<QQCredentialSlot>())
    }

    // Database — DatabaseDriverFactory is provided by platformModule()
    single { AppDatabase(get<DatabaseDriverFactory>().createDriver()) }

    // Repository
    single { Repository(get(), get()) }

    // Credential slots — kept as their own singletons because the QQ renewer writes through its
    // slot directly. NetEase's restarts account work through the main view model, resolved lazily
    // so the slot does not pull the view-model graph up at startup.
    single {
        val koin = getKoin()
        NeteaseCredentialSlot(get()) { koin.get<MainViewModel>().refreshAccount() }
    }
    single { QQCredentialSlot(get()) }

    // Providers — each registered once: what it answers, where its account lives, what the pages
    // say about it, how to sign in to it, and who its account is. Order is the order the pages
    // list them and search groups results, so NetEase stays first. A new provider is one more
    // entry here and a MusicProviderId.
    single {
        val koin = getKoin()
        val ncmApi = get<NCMApi>()
        val qqApi = get<QQMusicApi>()
        val canSendCookie = getPlatform().canSendCookieHeader
        ProviderRegistrations(
            listOf(
                ProviderRegistration(
                    // An adapter over the existing Repository, not a rewrite of it.
                    provider = NeteaseProvider(get(), get()),
                    credentialSlot = get<NeteaseCredentialSlot>(),
                    descriptor = ProviderLoginDescriptor(
                        provider = MusicProviderId.NETEASE,
                        introduction = "RedefineNCM 是第三方网易云音乐客户端。登录后可以使用你的歌单、每日推荐和喜欢的音乐；不登录也可以搜索和播放。",
                        accountLabel = "网易云音乐账号",
                        signedOutHint = "登录后可以查看歌单、每日推荐和喜欢的音乐",
                        logoutWarning = "退出后「我的」、每日推荐和喜欢等功能将不可用，已下载的歌曲会保留。",
                        server = ProviderServerSetting(
                            key = SettingKeys.SERVER,
                            default = DEFAULT_NCM_SERVER,
                            label = "网易云音乐服务器地址",
                            appliesWhen = "服务器地址重启后生效",
                            // 原版 ServerItem：调 /inner/version/ 校验服务器可用性并显示版本
                            check = { address -> checkNcmServer(ncmApi, address) },
                        ),
                    ),
                    loginMethods = listOf(
                        NeteaseQrLoginMethod(ncmApi),
                        NeteaseCookieLoginMethod(),
                    ),
                    // The account the app resolves at startup carries the name and avatar.
                    identity = AccountIdentitySource { _ ->
                        koin.get<MainViewModel>().userDetail.map { detail ->
                            detail?.profile?.let { profile ->
                                AccountIdentity(
                                    name = profile.nickname.takeIf(String::isNotBlank),
                                    avatarUrl = profile.avatarUrl.takeIf(String::isNotBlank),
                                )
                            }
                        }
                    },
                ),
                ProviderRegistration(
                    provider = QQProvider(qqApi, get(), get<QQCredentialRenewer>()),
                    credentialSlot = get<QQCredentialSlot>(),
                    descriptor = ProviderLoginDescriptor(
                        provider = MusicProviderId.QQ,
                        introduction = "登录后 QQ 音乐的搜索和播放使用你账号的权益；不登录也可以搜索，并播放可免费收听的歌曲。",
                        accountLabel = "QQ 音乐账号",
                        signedOutHint = "登录后搜索和播放使用你账号的权益",
                        logoutWarning = "退出后 QQ 音乐按未登录状态搜索和播放。",
                        server = ProviderServerSetting(
                            key = SettingKeys.QQ_SERVER,
                            default = SettingKeys.QQ_SERVER_DEFAULT,
                            label = "QQ 音乐网关地址",
                            appliesWhen = "网关地址立即生效",
                            check = { address -> checkQQGateway(qqApi, address) },
                        ),
                        enabledSetting = ProviderEnabledSetting(
                            key = SettingKeys.QQ_ENABLED,
                            default = false,
                            label = "启用 QQ 音乐",
                            supportingText = "需要自建 QQMusicApi 网关；未登录时按 QQ 对未登录用户的限制播放",
                        ),
                        // The gateway reads the account from a Cookie header only (see AGENTS.md D6).
                        signInUnavailableReason = if (canSendCookie) {
                            null
                        } else {
                            "浏览器无法携带 QQ 音乐的登录凭证，Web 端以未登录身份使用 QQ 音乐"
                        },
                    ),
                    loginMethods = listOf(
                        QQQrLoginMethod(qqApi, QQQrLoginMethod.Kind.QQ),
                        QQQrLoginMethod(qqApi, QQQrLoginMethod.Kind.WECHAT),
                        QQPhoneCodeLoginMethod(qqApi),
                        QQCredentialTextLoginMethod(),
                    ),
                    identity = QQAccountIdentitySource(qqApi),
                ),
            ),
        )
    }
    // The registries the pages and the player use, derived from the registrations above.
    single { get<ProviderRegistrations>().credentialStore() }
    single { get<ProviderRegistrations>().descriptorRegistry() }
    // Login sources, logic half — the login page renders whatever is registered for the provider
    // it was opened for; a platform that cannot sign in to a provider gets none of its methods.
    single { get<ProviderRegistrations>().loginMethodRegistry() }
    single {
        MusicProviderRegistry(
            providers = get<ProviderRegistrations>().musicProviders(),
            settings = get(),
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

    // The device-local account owns what never leaves this device; it has a name and no login.
    single { LocalAccount(get()) }

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
    single { NowPlayingViewModel(get(), get(), get(), get(), get(), get(), get()) }
    // Factory — recording and cancellation are scoped to one pushed recognition page.
    factory { SongRecognitionViewModel(get(), get(), get()) }
    // Single — the settings summary and the accounts page read the same account state.
    single { AccountsViewModel(get(), get(), get()) }
}

/** NetEase's server check: `/inner/version/` answers with the backend's version. */
private suspend fun checkNcmServer(api: NCMApi, address: String): ServerCheckResult = try {
    val result = api.innerVersion("${address}inner/version/")
    if (result.code == 200) {
        ServerCheckResult(true, "服务器可用，版本：${result.data.version}")
    } else {
        ServerCheckResult(false, "服务器有响应，但返回了错误（代码 ${result.code}）")
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    // The raw exception names a socket or parser, not what to do about it.
    ServerCheckResult(false, "无法连接到这个服务器，请检查地址是否正确、服务是否在运行")
}

/** The QQ gateway's check: whether it serves its own API description. */
private suspend fun checkQQGateway(api: QQMusicApi, address: String): ServerCheckResult =
    if (api.ping(address)) {
        ServerCheckResult(true, "网关可用")
    } else {
        ServerCheckResult(false, "无法连接到这个网关，请检查地址是否正确、网关是否在运行")
    }
