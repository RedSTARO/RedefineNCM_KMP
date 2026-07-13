package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.db.DatabaseDriverFactory
import com.leejlredstar.redefinencm.kmp.player.IosAVPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.recognition.IosMicrophoneRecorder
import com.leejlredstar.redefinencm.kmp.recognition.MicrophoneRecorder
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module

actual fun platformModule() = module {
    // Ktor HttpClient (Darwin engine). Cookie is read fresh per request, so QR login takes
    // effect without relaunch; only a server (base URL) change still needs a relaunch.
    single<HttpClient> { createNcmHttpClient(get(), Darwin) }

    // PlatformSettings backed by NSUserDefaults
    single { PlatformSettings() }

    // SQLDelight driver (Documents-directory SQLite via NativeSqliteDriver).
    single { DatabaseDriverFactory() }

    // Real AVPlayer-backed audio playback, overriding the shared InMemoryPlatformPlayer.
    single<PlatformPlayer> { IosAVPlayer(get(), get()) }

    // AVAudioEngine 麦克风输入；结束后恢复 AVPlayer 使用的 Playback 会话。
    single<MicrophoneRecorder> { IosMicrophoneRecorder() }
}
