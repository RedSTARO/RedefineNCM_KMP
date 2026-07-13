package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.api.HttpClientFactory
import com.leejlredstar.redefinencm.kmp.data.db.DatabaseDriverFactory
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.WebPlatformPlayer
import com.leejlredstar.redefinencm.kmp.recognition.MicrophoneRecorder
import com.leejlredstar.redefinencm.kmp.recognition.WasmMicrophoneRecorder
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import org.koin.dsl.module

actual fun platformModule() = module {
    // The Fetch API forbids a user-defined Cookie header. NCM API accepts the same cookie value
    // as a query parameter, so Web uses that transport and disables URL logging in the factory.
    single<HttpClient> {
        createNcmHttpClient(
            settings = get(),
            engineFactory = Js,
            cookieTransport = HttpClientFactory.CookieTransport.QUERY_PARAMETER,
        )
    }

    // PlatformSettings backed by localStorage
    single { PlatformSettings() }

    // SQLDelight over the synchronous browser-storage driver.
    single { DatabaseDriverFactory() }

    // Real browser audio output; overrides the common in-memory reference player.
    single<PlatformPlayer> { WebPlatformPlayer(get(), get()) }

    // 浏览器 getUserMedia 输入；只在 HTTPS 或 localhost 安全上下文工作。
    single<MicrophoneRecorder> { WasmMicrophoneRecorder() }
}
