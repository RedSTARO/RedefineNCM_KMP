package com.leejlredstar.redefinencm.kmp.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.leejlredstar.redefinencm.kmp.i18n.AppLanguage
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.canPostNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

    /** The tasks this run of the service has worked on; see [tasksOfRun]. */
    private val runTaskIds = mutableSetOf<Long>()
    private var emptyStopJob: Job? = null

    /** What the foreground notification last showed, less the progress, and when; see [promoteThrottled]. */
    private var postedShape: Any? = null
    private var postedAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        serviceScope.launch {
            KoinPlatform.getKoin().get<PlatformSettings>().awaitLoaded()
            downloadManager.awaitRestored()
            combine(
                downloadManager.tasks,
                downloadManager.destructiveTaskIdsInProgress,
                downloadManager.pendingEnqueues,
            ) { tasks, destructiveTaskIds, pendingEnqueues ->
                DownloadServiceSnapshot(
                    tasks = tasks,
                    // A request still being prepared keeps the service up like a destructive
                    // operation: it was started for that work and could not start again later.
                    hasDestructiveOperations = destructiveTaskIds.isNotEmpty() || pendingEnqueues > 0,
                )
            }.collectLatest { snapshot ->
                render(snapshot)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService 的 5 秒门限不能等待磁盘；先发不依赖设置的空状态通知，
        // 设置快照就绪后再解析 manager 和执行命令。
        promoteToForeground(analyzeDownloadTasks(emptyList()))
        serviceScope.launch {
            KoinPlatform.getKoin().get<PlatformSettings>().awaitLoaded()
            downloadManager.awaitRestored()
            promoteToForeground(analyzeDownloadTasks(tasksOfRun(downloadManager.tasks.value, runTaskIds)))
            when (intent?.action) {
                ACTION_PAUSE_ALL -> downloadManager.pauseAll()
                ACTION_RESUME_ALL -> downloadManager.resumeAll()
                ACTION_CANCEL_ALL -> downloadManager.cancelAll()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ gives dataSync services only a few seconds to stop after their allowance
        // expires. Stop observers first: their normal persistence-retry path would otherwise try
        // to promote this service again after the system has revoked its foreground allowance.
        serviceScope.cancel()
        try {
            downloadManager.pauseAll()
        } catch (failure: Exception) {
            System.err.println("Could not pause timed-out downloads: ${failure::class.simpleName}")
        } finally {
            removeNotificationAndStop()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun render(snapshot: DownloadServiceSnapshot) {
        val tasks = snapshot.tasks
        if (snapshot.hasDestructiveOperations) {
            if (tasks.isNotEmpty()) hasSeenTasks = true
            emptyStopJob?.cancel()
            promoteToForeground(analyzeDownloadTasks(tasksOfRun(tasks, runTaskIds)))
            return
        }
        if (tasks.isEmpty()) {
            if (hasSeenTasks) {
                stopAfterPersisting(tasks, removeNotification = true)
            } else {
                scheduleEmptyStop()
            }
            return
        }

        hasSeenTasks = true
        emptyStopJob?.cancel()
        val statistics = analyzeDownloadTasks(tasksOfRun(tasks, runTaskIds))
        if (statistics.firstActiveTask != null) {
            promoteThrottled(statistics)
        } else {
            stopAfterPersisting(tasks, removeNotification = false)
        }
    }

    private suspend fun stopAfterPersisting(
        observedTasks: List<SongDownloadTask>,
        removeNotification: Boolean,
    ) {
        val statistics = analyzeDownloadTasks(tasksOfRun(observedTasks, runTaskIds))
        while (true) {
            val persisted = downloadManager.flushPersistentDownloadQueue()

            // collectLatest can finish a non-cancellable database write after a newer snapshot
            // has arrived. Never detach or stop using stale task/destructive-operation state.
            if (
                downloadManager.tasks.value !== observedTasks ||
                downloadManager.destructiveTaskIdsInProgress.value.isNotEmpty() ||
                downloadManager.pendingEnqueues.value > 0
            ) return

            if (persisted) break

            // Keep the foreground guarantee while durable queue storage is unavailable. A state
            // change cancels this delay through collectLatest; otherwise retry until storage works.
            promoteToForeground(statistics)
            delay(PERSISTENCE_RETRY_DELAY_MS)
        }

        if (statistics.firstActiveTask != null) return

        // Nothing finished or failed in this run: the user cancelled it, and a "finished"
        // notification would only contradict that.
        val reportable = statistics.summary.completed + statistics.summary.failed + statistics.summary.paused
        if (removeNotification || reportable == 0) {
            removeNotificationAndStop()
        } else {
            // The ongoing notification goes first and the summary is a new, dismissible one.
            // Posted over the ongoing one and then detached, a dropped update left "Downloading"
            // with Pause and Cancel on screen, which before Android 14 cannot be swiped away.
            if (isForeground) {
                stopForegroundRemovingNotification()
                isForeground = false
            }
            postRegularNotification(statistics)
            stopSelf()
        }
    }

    /**
     * The notification follows a status change at once, and progress at most once a second.
     * Rebuilding and re-posting it for every progress step made binder calls on the main thread
     * many times a second, and the system drops updates beyond about five a second, which could
     * drop the last one.
     */
    private fun promoteThrottled(statistics: DownloadTaskStatistics) {
        val shape = statistics.copy(
            firstActiveTask = statistics.firstActiveTask?.copy(progressBytes = 0L),
        )
        val nowMs = SystemClock.elapsedRealtime()
        if (shape == postedShape && nowMs - postedAtMs < PROGRESS_NOTIFICATION_INTERVAL_MS) return
        postedShape = shape
        postedAtMs = nowMs
        promoteToForeground(statistics)
    }

    private fun promoteToForeground(statistics: DownloadTaskStatistics) {
        val notification = buildNotification(statistics, foreground = true)
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

    private fun postRegularNotification(statistics: DownloadTaskStatistics) {
        if (!canPostNotifications(this)) return
        runCatching {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                buildNotification(statistics, foreground = false),
            )
        }
    }

    private fun buildNotification(
        statistics: DownloadTaskStatistics,
        foreground: Boolean,
    ): Notification {
        val activeTask = statistics.firstActiveTask
        val (total, _, completed, failed, _, paused) = statistics.summary
        val title = when {
            activeTask != null -> strings.downloadingTitle(activeTask.title)
            paused > 0 -> strings.downloadsPaused
            failed > 0 -> strings.downloadsFinishedWithFailures(failed)
            total > 0 -> strings.downloadsFinished
            else -> strings.downloadsPreparing
        }
        val text = when {
            total == 0 -> strings.downloadQueuePreparing
            activeTask != null -> strings.downloadProgressWithArtist(completed, total, activeTask.artist)
            paused > 0 -> strings.downloadProgressWithPaused(paused, completed, total)
            failed > 0 -> strings.downloadProgress(completed, total)
            else -> strings.downloadProgress(completed, total)
        }

        ensureChannel()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openDownloadsPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(foreground)
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
                strings.pause,
                servicePendingIntent(ACTION_PAUSE_ALL, requestCode = 1),
            )
        }
        if (paused > 0) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                strings.resume,
                servicePendingIntent(ACTION_RESUME_ALL, requestCode = 2),
            )
        }
        if (statistics.summary.active > 0 || paused > 0) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                strings.cancel,
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
            val tasks = downloadManager.tasks.value
            if (!hasSeenTasks && tasks.isEmpty()) {
                stopAfterPersisting(tasks, removeNotification = true)
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

    private fun stopForegroundRemovingNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    /**
     * Creates the channel, or renames it after a language switch: creating a channel that exists
     * only updates its name and description, and keeps the settings the user gave it.
     */
    private var channelLanguage: AppLanguage? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val language = I18n.language
        if (channelLanguage == language) return
        channelLanguage = language
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.downloadChannelName,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = strings.downloadChannelDescription
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private data class Progress(
        val max: Int,
        val current: Int,
    )

    private data class DownloadServiceSnapshot(
        val tasks: List<SongDownloadTask>,
        val hasDestructiveOperations: Boolean,
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
        private const val PERSISTENCE_RETRY_DELAY_MS = 2_000L
    }
}

/** The least time between two progress updates of the download notification. */
private const val PROGRESS_NOTIFICATION_INTERVAL_MS = 1_000L
