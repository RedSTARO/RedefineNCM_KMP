package com.leejlredstar.redefinencm.kmp.di

import com.leejlredstar.redefinencm.kmp.data.db.DatabaseDriverFactory
import com.leejlredstar.redefinencm.kmp.data.api.ExternalHttpClientFactory
import com.leejlredstar.redefinencm.kmp.player.ExoPlayerPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.recognition.AndroidMicrophoneRecorder
import com.leejlredstar.redefinencm.kmp.recognition.MicrophoneRecorder
import com.leejlredstar.redefinencm.kmp.transition.AnalysisLocalAudio
import com.leejlredstar.redefinencm.kmp.transition.BeatModelLoader
import com.leejlredstar.redefinencm.kmp.transition.LiteRtBeatModelLoader
import com.leejlredstar.redefinencm.kmp.transition.MediaCodecTrackEndsDecoder
import com.leejlredstar.redefinencm.kmp.transition.TrackEndsDecoder
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.findDownloadedSongUri
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual fun platformModule() = module {
    // Ktor HttpClient (OkHttp engine) configured with base URL + realIP + cookie from settings.
    single<HttpClient> { createNcmHttpClient(get(), OkHttp) }
    single { ExternalHttpClientFactory.create(OkHttp) }

    // PlatformSettings backed by DataStore (needs the app Context provided via androidContext()).
    single { PlatformSettings(get()) }

    // SQLDelight driver (needs Context for the Android SQLite helper).
    single { DatabaseDriverFactory(androidContext()) }

    // ExoPlayer-backed PlatformPlayer. It overrides the InMemoryPlatformPlayer in sharedModule.
    // Must be resolved on the main thread (ExoPlayer requirement); Koin singleton lives for the
    // app lifetime. PlaybackService wraps its active ExoPlayer in a MediaSession.
    single<PlatformPlayer> { ExoPlayerPlatformPlayer(androidContext(), get(), get(), get(), get()) }

    // 前台听歌识曲使用的原始 PCM 麦克风输入。
    single<MicrophoneRecorder> { AndroidMicrophoneRecorder(androidContext()) }

    // Smart song transitions: the beat model through LiteRT, and the track ends decoded with the
    // extractors ExoPlayer plays through.
    single<BeatModelLoader> { LiteRtBeatModelLoader(androidContext()) }
    single<TrackEndsDecoder> { MediaCodecTrackEndsDecoder(androidContext()) }
    single { AnalysisLocalAudio { id -> withContext(Dispatchers.IO) { findDownloadedSongUri(id) } } }
}
