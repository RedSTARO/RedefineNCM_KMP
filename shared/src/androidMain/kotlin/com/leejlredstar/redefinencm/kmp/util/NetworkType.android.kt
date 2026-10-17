package com.leejlredstar.redefinencm.kmp.util

import android.content.Context
import android.net.ConnectivityManager
import org.koin.mp.KoinPlatform

actual fun currentNetworkType(): NetworkType = runCatching {
    val context = KoinPlatform.getKoin().get<Context>()
    val manager = context.getSystemService(ConnectivityManager::class.java)
    if (manager?.isActiveNetworkMetered == true) NetworkType.CELLULAR else NetworkType.WIFI
}.getOrDefault(NetworkType.WIFI)
