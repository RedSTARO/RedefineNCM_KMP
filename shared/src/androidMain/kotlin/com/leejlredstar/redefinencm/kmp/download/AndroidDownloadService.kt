package com.leejlredstar.redefinencm.kmp.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import kotlin.math.roundToInt

class AndroidDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val downloadManager: SongDownloadManager by lazy { KoinPlatform.getKoin().get() }
    private val notificationManager: NotificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    private var isForeground = false
    private var hasSeenTasks = false
    private var emptyStopJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        serviceScope.launch {
            downloadManager.tasks.collectLatest { tasks ->
                render(tasks)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground(downloadManager.tasks.value)
        when (intent?.action) {
            ACTION_PAUSE_ALL -> downloadManager.pauseAll()
            ACTION_RESUME_ALL -> downloadManager.resumeAll()
            ACTION_CANCEL_ALL -> downloadManager.cancelAll()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun render(tasks: List<SongDownloadTask>) {
        if (tasks.isEmpty()) {
            if (hasSeenTasks) {
                removeNotificationAndStop()
            } else {
                scheduleEmptyStop()
            }
            return
        }

        hasSeenTasks = true
        emptyStopJob?.cancel()
        val hasActiveTask = tasks.any { it.isActive }
        if (hasActiveTask) {
            promoteToForeground(tasks)
        } else {
            postRegularNotification(tasks)
            detachForeground()
            stopSelf()
        }
    }

    private fun promoteToForeground(tasks: List<SongDownloadTask>) {
        val notification = buildNotification(tasks, foreground = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun postRegularNotification(tasks: List<SongDownloadTask>) {
        if (!canPostNotifications()) return
        runCatching {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildNotification(tasks, foreground = false),
            )
        }
    }

    private fun buildNotification(
        tasks: List<SongDownloadTask>,
        foreground: Boolean,
    ): Notification {
        val activeTask = tasks.firstOrNull { it.isActive }
        val total = tasks.size
        val completed = tasks.count { it.status == DownloadTaskStatus.Completed }
        val failed = tasks.count {
            it.status == DownloadTaskStatus.Failed || it.status == DownloadTaskStatus.Cancelled
        }
        val paused = tasks.count { it.status == DownloadTaskStatus.Paused }
        val title = when {
            activeTask != null -> "正在下载：${activeTask.title}"
            paused > 0 -> "下载已暂停"
            failed > 0 -> "下载完成，$failed 项失败"
            total > 0 -> "下载完成"
            else -> "下载准备中"
        }
        val text = when {
            total == 0 -> "正在准备下载队列"
            activeTask != null -> "$completed/$total 完成 · ${activeTask.artist}"
            paused > 0 -> "$paused 项暂停 · $completed/$total 完成"
            failed > 0 -> "$completed/$total 完成"
            else -> "$completed/$total 完成"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openDownloadsPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(foreground && activeTask != null)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setShowWhen(false)

        if (activeTask != null) {
            activeTask.progress().let { progress ->
                if (progress == null) {
                    builder.setProgress(0, 0, true)
                } else {
                    builder.setProgress(progress.max, progress.current, false)
                }
            }
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "暂停",
                servicePendingIntent(ACTION_PAUSE_ALL, requestCode = 1),
            )
        }
        if (paused > 0) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "继续",
                servicePendingIntent(ACTION_RESUME_ALL, requestCode = 2),
            )
        }
        if (tasks.any { it.isActive || it.status == DownloadTaskStatus.Paused }) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "取消",
                servicePendingIntent(ACTION_CANCEL_ALL, requestCode = 3),
            )
        }

        return builder.build()
    }

    private fun SongDownloadTask.progress(): Progress? {
        val total = totalBytes?.takeIf { it > 0L } ?: return null
        val current = ((progressBytes.toDouble() / total.toDouble()).coerceIn(0.0, 1.0) * PROGRESS_MAX)
            .roundToInt()
        return Progress(max = PROGRESS_MAX, current = current)
    }

    private fun openDownloadsPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(packageName)
        intent.action = DownloadNotificationIntents.ACTION_OPEN_DOWNLOADS
        intent.putExtra(DownloadNotificationIntents.EXTRA_OPEN_DOWNLOADS, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AndroidDownloadService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    private fun scheduleEmptyStop() {
        if (emptyStopJob?.isActive == true) return
        emptyStopJob = serviceScope.launch {
            delay(EMPTY_QUEUE_STOP_DELAY_MS)
            if (!hasSeenTasks && downloadManager.tasks.value.isEmpty()) {
                removeNotificationAndStop()
            }
        }
    }

    private fun removeNotificationAndStop() {
        emptyStopJob?.cancel()
        if (isForeground) {
            stopForegroundRemovingNotification()
            isForeground = false
        } else {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }

    private fun detachForeground() {
        if (!isForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        isForeground = false
    }

    private fun stopForegroundRemovingNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Download Service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows background music download progress"
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class Progress(
        val max: Int,
        val current: Int,
    )

    companion object {
        const val ACTION_START = "com.leejlredstar.redefinencm.kmp.download.START"
        private const val ACTION_PAUSE_ALL = "com.leejlredstar.redefinencm.kmp.download.PAUSE_ALL"
        private const val ACTION_RESUME_ALL = "com.leejlredstar.redefinencm.kmp.download.RESUME_ALL"
        private const val ACTION_CANCEL_ALL = "com.leejlredstar.redefinencm.kmp.download.CANCEL_ALL"
        private const val CHANNEL_ID = "music_downloads"
        private const val NOTIFICATION_ID = 0x444C4F44 // "DLOD"
        private const val PROGRESS_MAX = 1_000
        private const val EMPTY_QUEUE_STOP_DELAY_MS = 15_000L
    }
}
