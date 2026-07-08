package com.leejlredstar.redefinencm.kmp

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import org.koin.android.ext.koin.androidContext
import org.koin.mp.KoinPlatform

class RedefineNCMApp : Application(), SingletonImageLoader.Factory {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val syncDownloadedSongState = Runnable {
        runCatching {
            KoinPlatform.getKoin().get<SongDownloadManager>().syncWithLocalLibrary()
        }
    }
    private var downloadedSongsObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@RedefineNCMApp) }
        LyricNotificationController.init(applicationContext)
        registerDownloadedSongsObserver()
    }

    private fun registerDownloadedSongsObserver() {
        if (downloadedSongsObserver != null) return
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                scheduleDownloadedSongStateSync()
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduleDownloadedSongStateSync()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.registerContentObserver(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
        }
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        downloadedSongsObserver = observer
    }

    private fun scheduleDownloadedSongStateSync() {
        mainHandler.removeCallbacks(syncDownloadedSongState)
        mainHandler.postDelayed(syncDownloadedSongState, 750L)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
}
