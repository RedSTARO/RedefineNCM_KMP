package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.db.DatabaseDriverFactory
import com.leejlredstar.redefinencm.kmp.player.JvmMediaPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.recognition.JvmMicrophoneRecorder
import com.leejlredstar.redefinencm.kmp.recognition.MicrophoneRecorder
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module

actual fun platformModule() = module {
    // Ktor HttpClient (OkHttp engine) configured with base URL + realIP + cookie from settings.
    // OkHttp（而非 CIO）：服务器 DNS 含黑洞 A 记录时自动回退下一个 IP。
    single<HttpClient> {
        val settings = get<PlatformSettings>()
        HttpClientFactory.create(
            baseUrl = settings.getString(SettingKeys.SERVER, "https://ncm.tryagain.icu/"),
            realIP = "192.168.1.1",
            cookieProvider = { settings.getString(SettingKeys.COOKIE, "") },
            engineFactory = OkHttp,
        )
    }

    // PlatformSettings backed by java.util.prefs
    single { PlatformSettings() }

    // SQLDelight driver (file-backed SQLite in ~/.redefinencm/).
    single { DatabaseDriverFactory() }

    // Desktop audio player backed by javax.sound.sampled + mp3spi.
    // Overrides the InMemoryPlatformPlayer default from sharedModule.
    // Uses JVM-decodable local files before hitting the CDN.
    single<PlatformPlayer> { JvmMediaPlayer(get(), get()) }

    // Java Sound 麦克风输入；录音生命周期由调用协程控制。
    single<MicrophoneRecorder> { JvmMicrophoneRecorder() }
}
