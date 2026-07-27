package com.leejlredstar.redefinencm.kmp.ui.amll

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberReducedMotionEnabled(): Boolean {
    val resolver = LocalContext.current.applicationContext.contentResolver
    var reducedMotionEnabled by remember(resolver) {
        mutableStateOf(readReducedMotionEnabled(resolver))
    }

    DisposableEffect(resolver) {
        val animatorDurationScaleUri =
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotionEnabled = readReducedMotionEnabled(resolver)
            }
        }

        reducedMotionEnabled = readReducedMotionEnabled(resolver)
        runCatching {
            resolver.registerContentObserver(
                animatorDurationScaleUri,
                false,
                observer,
            )
        }
        onDispose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }

    return reducedMotionEnabled
}

private fun readReducedMotionEnabled(
    resolver: android.content.ContentResolver,
): Boolean = runCatching {
    // Android's Accessibility "Remove animations" switch drives this global scale to zero.
    Settings.Global.getFloat(
        resolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        DEFAULT_ANIMATOR_DURATION_SCALE,
    ) <= 0f
}.getOrDefault(false)

private const val DEFAULT_ANIMATOR_DURATION_SCALE = 1f
