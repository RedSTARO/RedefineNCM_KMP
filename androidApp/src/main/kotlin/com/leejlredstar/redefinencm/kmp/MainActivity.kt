package com.leejlredstar.redefinencm.kmp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, PlaybackService::class.java))
        } else {
            startService(Intent(this, PlaybackService::class.java))
        }

        requestNotificationPermission()

        setContent { App() }
    }

    override fun onPause() {
        // 与原版 MainActivity.onPause 一致：离开前台时保存播放状态（队列/索引/进度/随机）
        runCatching { get<MainViewModel>().savePlayerStatus() }
        super.onPause()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return

        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
}
