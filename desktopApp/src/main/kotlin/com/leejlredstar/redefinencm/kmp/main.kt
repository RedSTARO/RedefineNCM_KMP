package com.leejlredstar.redefinencm.kmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.notification.LyricNotificationController
import com.leejlredstar.redefinencm.kmp.smtc.WindowsMediaControls
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme

fun main() {
    // AMLL 歌词页现在跑在系统 WebView（Windows=WebView2）里，见 LyricScreen.jvm.kt。
    // 历史教训（勿回退）：JavaFX WebKit 需要 prism.maxvram 调大才不白屏，且无 GPU 合成，
    // 字体/布局/动画均残缺；prism.order=sw 会打满 CPU 饿死网络协程。
    initKoin()
    // Observe now-playing metadata and forward to OS media controls (Windows SMTC binding is a
    // documented native TODO; this is a no-op until the helper exists — see WindowsMediaControls).
    WindowsMediaControls().start()
    application {
        val mainWindowState = rememberWindowState(
            size = DpSize(1280.dp, 820.dp),
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = mainWindowState,
            title = "RedefineNCM",
        ) {
            App()
        }

        // Desktop floating-lyrics window (goal #2: the desktop equivalent of the Android
        // notification / iOS Live Activity). It is a second, frameless, always-on-top window
        // driven entirely by the shared LyricNotificationController (JVM actual): the playback
        // pipeline calls updateLyric(...) + show(), and this window mirrors that state.
        FloatingLyricWindow()
    }
}

@Composable
private fun ApplicationScope.FloatingLyricWindow() {
    val visible by LyricNotificationController.isWindowVisible.collectAsState()
    if (!visible) return

    val data by LyricNotificationController.floatingLyricData.collectAsState()
    val windowState = rememberWindowState(
        size = DpSize(720.dp, 140.dp),
        position = WindowPosition(Alignment.BottomCenter),
    )

    Window(
        onCloseRequest = { LyricNotificationController.hide() },
        state = windowState,
        title = "Desktop Lyrics",
        undecorated = true,   // frameless
        transparent = true,   // translucent (requires undecorated)
        alwaysOnTop = true,
        resizable = false,
    ) {
        RedefineNCMTheme {
            Box(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = data?.currentLyric?.ifBlank { data?.title.orEmpty() }
                                ?: "RedefineNCM",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val next = data?.nextLyric.orEmpty()
                        if (next.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = next,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.alpha(0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}
