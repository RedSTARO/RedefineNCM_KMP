package com.leejlredstar.redefinencm.kmp.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Action strings for notification media controls.
 * These match the intent-filter actions in AndroidManifest.xml.
 */
object MediaActions {
    const val TOGGLE = "com.leejlredstar.redefinencm.kmp.TOGGLE_PLAY_PAUSE"
    const val NEXT = "com.leejlredstar.redefinencm.kmp.NEXT"
    const val PREV = "com.leejlredstar.redefinencm.kmp.PREVIOUS"
}

actual object LyricNotificationController {
    private const val CHANNEL_ID = "live_update_lyric"
    private const val NOTIFICATION_ID = 0x4C595243 // "LYRC"

    @Volatile
    private var lastLyric: String? = null

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        ensureChannel(context)
    }

    actual fun updateLyric(
        title: String?,
        artist: String?,
        currentLyric: String?,
        nextLyric: String?,
        artworkUri: String?,
        isPlaying: Boolean,
    ) {
        val context = appContext ?: return
        if (!canPostNotifications(context)) return
        val lyric = currentLyric?.trim().takeUnless { it.isNullOrEmpty() } ?: return
        if (lyric == lastLyric) return

        val displayTitle = title ?: "RedefineNCM"
        val trimmedArtist = artist?.trim().orEmpty()
        val trimmedNext = nextLyric?.trim().orEmpty()

        val contentText = buildString {
            if (trimmedArtist.isNotEmpty()) {
                append(trimmedArtist)
                append(" · ")
            }
            append(lyric)
        }

        val detailText = buildString {
            append(lyric)
            if (trimmedNext.isNotEmpty()) {
                appendLine()
                append(trimmedNext)
            }
        }

        fun mediaIntent(action: String) =
            Intent(action).setPackage(context.packageName)

        val prevIntent = PendingIntent.getBroadcast(
            context, 0,
            mediaIntent(MediaActions.PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIntent = PendingIntent.getBroadcast(
            context, 1,
            mediaIntent(MediaActions.TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val nextIntent = PendingIntent.getBroadcast(
            context, 2,
            mediaIntent(MediaActions.NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val playPauseIcon = if (isPlaying)
            android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseLabel = if (isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(displayTitle)
            .setContentText(contentText)
            .setSubText(trimmedArtist.ifEmpty { null })
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(detailText)
                    .setSummaryText(trimmedArtist.ifEmpty { null }),
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous, "Previous", prevIntent,
                ),
            )
            .addAction(
                NotificationCompat.Action(playPauseIcon, playPauseLabel, toggleIntent),
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next, "Next", nextIntent,
                ),
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    context.packageManager
                        .getLaunchIntentForPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )

        if (shouldRequestLiveUpdate(context)) {
            builder.setRequestPromotedOngoing(true)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        lastLyric = lyric
    }

    actual fun clearFocus() {
        val context = appContext ?: return
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        lastLyric = null
    }

    actual fun reset() {
        lastLyric = null
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live Update Lyric",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows current song lyrics in the notification shade"
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager?.createNotificationChannel(channel)
    }

    private fun shouldRequestLiveUpdate(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager?.canPostPromotedNotifications() == true
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
