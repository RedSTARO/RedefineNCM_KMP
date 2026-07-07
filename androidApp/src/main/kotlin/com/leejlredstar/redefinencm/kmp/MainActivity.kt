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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // started 状态兜底：即使 controller 断开（Activity 销毁）且未在播放，
        // 服务也不随 unbind 立即销毁，避免会话反复重建。
        // 播放中的保活与此无关 —— Media3 在播放开始时会自行把服务升为
        // started + foreground（原生媒体通知），Activity 死活不影响。
        startService(Intent(this, PlaybackService::class.java))

        controllerFuture = MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java)),
        ).buildAsync()

        requestRuntimePermissions()

        setContent { App() }
    }

    override fun onPause() {
        // 与原版 MainActivity.onPause 一致：离开前台时保存播放状态（队列/索引/进度/随机）
        runCatching { get<MainViewModel>().savePlayerStatus() }
        super.onPause()
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
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
            } else if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isEmpty()) return

        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            Thread({ DownloadedSongsCache.refresh() }, "download-cache-permission-refresh").apply {
                isDaemon = true
                start()
            }
        }.launch(permissions.toTypedArray())
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
