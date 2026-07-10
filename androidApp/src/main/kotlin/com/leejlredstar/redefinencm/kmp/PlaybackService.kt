package com.leejlredstar.redefinencm.kmp

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.player.AndroidMediaSessionInitializationState
import com.leejlredstar.redefinencm.kmp.player.ExoPlayerPlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.canExposeAndroidMediaSession
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

/**
 * Foreground service for background media playback.
 *
 * 媒体通知完全交给 Media3 的 DefaultMediaNotificationProvider：原生 MediaStyle
 * 通知（封面经 BitmapLoader 自动拉取 artworkUri、系统进度条、播放/上一首/下一首
 * 控制），播放开始时它自行 startForeground、停止时降级 —— 与原版行为一致。
 * 不要在这里手动 startForeground 自制通知：那会顶掉原生媒体通知的位置
 * （用户只会看到一个没有封面/进度条的假通知）。
 *
 * 服务由 MainActivity 以普通 startService 拉起（不是 startForegroundService，
 * 否则必须 5 秒内 startForeground，而彼时还没有播放会话）。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var initializationJob: Job? = null
    @Volatile
    private var initializationState = AndroidMediaSessionInitializationState.Loading

    override fun onCreate() {
        super.onCreate()
        initializationJob = serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                get<PlatformSettings>().awaitLoaded()
                val exoPlayer = (get<PlatformPlayer>() as ExoPlayerPlatformPlayer).exoPlayer
                mediaSession = MediaSession.Builder(this@PlaybackService, exoPlayer).build()
                initializationState = AndroidMediaSessionInitializationState.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                initializationState = AndroidMediaSessionInitializationState.Failed
                System.err.println("PlaybackService initialization failed: ${error.message}")
                stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val session = mediaSession
        return if (canExposeAndroidMediaSession(initializationState, session != null)) {
            session
        } else {
            // MediaSessionService has a synchronous callback and cannot suspend a cold external
            // controller while DataStore loads. That first connection is rejected; the caller must
            // reconnect. MainActivity waits for settings first, and UNDISPATCHED therefore creates
            // the normal in-app session synchronously before onCreate returns.
            null
        }
    }

    override fun onDestroy() {
        initializationState = AndroidMediaSessionInitializationState.Destroyed
        initializationJob?.cancel()
        initializationJob = null
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        LyricNotificationController.clearFocus()
        super.onDestroy()
    }
}
