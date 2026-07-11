package com.leejlredstar.redefinencm.kmp.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object NowPlayingNotificationIntents {
    const val ACTION_OPEN_NOW_PLAYING =
        "com.leejlredstar.redefinencm.kmp.notification.OPEN_NOW_PLAYING"
    const val EXTRA_OPEN_NOW_PLAYING =
        "com.leejlredstar.redefinencm.kmp.notification.extra.OPEN_NOW_PLAYING"
}

fun createNowPlayingPendingIntent(context: Context, requestCode: Int): PendingIntent? {
    val intent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            action = NowPlayingNotificationIntents.ACTION_OPEN_NOW_PLAYING
            putExtra(NowPlayingNotificationIntents.EXTRA_OPEN_NOW_PLAYING, true)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        ?: return null
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
