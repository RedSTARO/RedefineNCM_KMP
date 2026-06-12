package com.leejlredstar.redefinencm.kmp

import android.app.Application
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import org.koin.android.ext.koin.androidContext

/**
 * Android Application: starts Koin (providing the app Context so the DataStore-backed
 * PlatformSettings can resolve it) and initialises the lyric notification controller.
 *
 * Registered via `android:name=".RedefineNCMApp"` in AndroidManifest.xml.
 */
class RedefineNCMApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@RedefineNCMApp) }
        LyricNotificationController.init(applicationContext)
    }
}
