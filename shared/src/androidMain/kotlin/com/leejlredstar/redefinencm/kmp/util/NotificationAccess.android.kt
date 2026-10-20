package com.leejlredstar.redefinencm.kmp.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.leejlredstar.redefinencm.kmp.notification.AndroidLyricNotification

/** Whether a lyric notification posted now would be seen at all. */
internal fun lyricNotificationsVisible(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!canPostNotifications(context) || !manager.areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    // A channel the user switched off drops every notification posted to it, silently.
    val channel = manager.getNotificationChannel(AndroidLyricNotification.CHANNEL_ID) ?: return true
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}

@Composable
actual fun rememberNotificationAccess(): NotificationAccess {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val access = remember(context) {
        object : NotificationAccess {
            var visible by mutableStateOf(lyricNotificationsVisible(context))
            var launch: () -> Unit = {}

            override val allowed: Boolean get() = visible

            override fun request() {
                visible = lyricNotificationsVisible(context)
                if (!visible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !canPostNotifications(context)
                ) {
                    launch()
                }
            }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access.visible = lyricNotificationsVisible(context)
    }
    SideEffect { access.launch = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
    DisposableEffect(lifecycleOwner, access) {
        // Notifications are switched on and off in system settings, away from the app.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access.visible = lyricNotificationsVisible(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return access
}
