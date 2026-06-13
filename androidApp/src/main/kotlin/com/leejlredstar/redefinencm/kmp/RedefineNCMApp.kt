package com.leejlredstar.redefinencm.kmp

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import org.koin.android.ext.koin.androidContext

class RedefineNCMApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@RedefineNCMApp) }
        LyricNotificationController.init(applicationContext)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
}
