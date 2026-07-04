package com.leejlredstar.redefinencm.kmp.util

import android.content.Context
import org.koin.mp.KoinPlatform

actual fun currentAppVersion(): String? = runCatching {
    val context = KoinPlatform.getKoin().get<Context>()
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()
