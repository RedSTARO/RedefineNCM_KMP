package com.leejlredstar.redefinencm.kmp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import org.koin.android.ext.koin.androidContext
import org.koin.mp.KoinPlatform

class RedefineNCMApp : Application(), SingletonImageLoader.Factory {
    private var lastDownloadStateSyncAt = 0L

    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@RedefineNCMApp) }
        LyricNotificationController.init(applicationContext)
        // 下载目录扫描是磁盘 IO，放后台线程预热，避免阻塞主线程启动路径
        syncDownloadedSongState("download-cache-warmup")
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                syncDownloadedSongState("download-cache-resume")
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun syncDownloadedSongState(threadName: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDownloadStateSyncAt < 1_500L) return
        lastDownloadStateSyncAt = now
        Thread({
            DownloadedSongsCache.refresh()
            runCatching {
                KoinPlatform.getKoin().get<SongDownloadManager>().syncWithLocalLibrary()
            }
        }, threadName).apply {
            isDaemon = true
            start()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
}
