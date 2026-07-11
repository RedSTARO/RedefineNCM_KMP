package com.leejlredstar.redefinencm.kmp

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.leejlredstar.redefinencm.kmp.download.DownloadNotificationIntents
import com.leejlredstar.redefinencm.kmp.download.SongDownloadManager
import com.leejlredstar.redefinencm.kmp.notification.NowPlayingNotificationIntents
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.requiresLegacyDownloadWritePermission
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {

    /**
     * 官方 Media3 模式（也是原版的做法）：UI 侧建立 MediaController 连接。
     * 连接会 bind 起 PlaybackService，并把会话置入"用户参与"生命周期 ——
     * Media3 的原生媒体通知（MediaStyle 封面/进度条/控制）与前台升降级
     * 由此驱动。没有任何 controller 连接时通知管理不会激活，这正是
     * 之前"只有歌词通知、没有媒体通知"的原因。
     * UI 的播放控制仍走进程内共享的 ExoPlayer（Koin 单例），controller
     * 只为生命周期而存在。
     */
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        lifecycleScope.launch {
            get<PlatformSettings>().awaitLoaded()
            runCatching { get<SongDownloadManager>().syncWithLocalLibrary() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            get<PlatformSettings>().awaitLoaded()

            // 服务、播放器和 UI 都只能在 DataStore 初始快照就绪后创建；否则同步 getter
            // 会合法地返回默认值，并把错误的音量/服务器配置固化进进程级单例。
            startService(Intent(this@MainActivity, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(
                this@MainActivity,
                SessionToken(
                    this@MainActivity,
                    ComponentName(this@MainActivity, PlaybackService::class.java),
                ),
            ).buildAsync()

            requestRuntimePermissions()
            handleNavigationIntent(intent)
            setContent { App() }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            get<PlatformSettings>().awaitLoaded()
            runCatching { get<SongDownloadManager>().syncWithLocalLibrary() }
        }
    }

    override fun onPause() {
        // 与原版 MainActivity.onPause 一致：离开前台时保存播放状态（队列/索引/进度/随机）
        lifecycleScope.launch {
            val settings = get<PlatformSettings>()
            settings.awaitLoaded()
            runCatching {
                settings.flush()
                get<MainViewModel>().savePlayerStatus()
            }.onFailure { error ->
                System.err.println("Failed to flush settings before pause: ${error.message}")
            }
        }
        super.onPause()
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) {
                    add(Manifest.permission.READ_MEDIA_AUDIO)
                }
            } else {
                if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (
                    requiresLegacyDownloadWritePermission(
                        sdkInt = Build.VERSION.SDK_INT,
                        permissionGranted = hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    )
                ) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
        if (permissions.isEmpty()) return

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun handleNavigationIntent(intent: Intent?) {
        if (intent == null) return
        var consumed = false
        when {
            intent.action == DownloadNotificationIntents.ACTION_OPEN_DOWNLOADS ||
                intent.getBooleanExtra(DownloadNotificationIntents.EXTRA_OPEN_DOWNLOADS, false) -> {
                AppNavigationRequests.openDownloads()
                consumed = true
            }
            intent.action == NowPlayingNotificationIntents.ACTION_OPEN_NOW_PLAYING ||
                intent.getBooleanExtra(
                    NowPlayingNotificationIntents.EXTRA_OPEN_NOW_PLAYING,
                    false,
                ) -> {
                AppNavigationRequests.openNowPlaying()
                consumed = true
            }
        }
        if (consumed) {
            // Navigation intents are one-shot. Leaving them on the Activity would reopen the
            // full-screen player/downloads after a configuration change or process recreation.
            intent.action = null
            intent.removeExtra(DownloadNotificationIntents.EXTRA_OPEN_DOWNLOADS)
            intent.removeExtra(NowPlayingNotificationIntents.EXTRA_OPEN_NOW_PLAYING)
        }
    }
}
