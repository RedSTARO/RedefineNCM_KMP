package com.leejlredstar.redefinencm.kmp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import org.koin.android.ext.koin.androidContext

class RedefineNCMApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@RedefineNCMApp) }
        LyricNotificationController.init(applicationContext)
        // 下载目录扫描是磁盘 IO，放后台线程预热，避免阻塞主线程启动路径
        refreshDownloadedSongsCache("download-cache-warmup")
    }

    private fun refreshDownloadedSongsCache(threadName: String) {
        Thread({ DownloadedSongsCache.refresh() }, threadName).apply {
            isDaemon = true
            start()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
}
