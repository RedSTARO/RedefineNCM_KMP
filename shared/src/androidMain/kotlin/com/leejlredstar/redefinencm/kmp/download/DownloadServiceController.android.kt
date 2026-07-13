package com.leejlredstar.redefinencm.kmp.download

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import org.koin.mp.KoinPlatform

actual object DownloadServiceController {
    actual fun supportsPersistentDownloadQueue(): Boolean = true

    actual fun ensureRunning() {
        val context = KoinPlatform.getKoin().get<Context>().applicationContext
        val intent = Intent(context, AndroidDownloadService::class.java)
            .setAction(AndroidDownloadService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }
}
