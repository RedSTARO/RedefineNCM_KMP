package com.leejlredstar.redefinencm.kmp.lyric

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import org.koin.compose.koinInject

/**
 * Android actual: AMLL lyric engine in a WebView.
 *
 * Loads [player.html] from assets and drives it via WebView bridge:
 * - `AmllBridge.loadLyrics(lrcText)` — feeds raw LRC to the AMLL parser
 * - `AmllBridge.setTime(ms, delta)` — updates playback position (called per frame)
 * - `AmllBridge.setTheme("dark")` — sets dark theme
 *
 * The pure-Compose [FullLyricScreen] is also available via its commonMain definition.
 */
@SuppressLint("SetJavaScriptEnabled")
actual @Composable fun WebViewLyricScreen(onBack: () -> Unit) {
    val viewModel: NowPlayingViewModel = koinInject()
    val rawLyric by viewModel.rawLyric.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val context = LocalContext.current
    var bridgeReady by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false

            webChromeClient = WebChromeClient()

            addJavascriptInterface(AmllCallback(), "AmllCallback")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    bridgeReady = true
                }
            }

            loadUrl("file:///android_asset/amll/player.html")
        }
    }

    // Send raw LRC lyrics when they change
    LaunchedEffect(rawLyric) {
        if (!bridgeReady || rawLyric.isEmpty()) return@LaunchedEffect
        val escaped = rawLyric
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        webView.evaluateJavascript("AmllBridge.loadLyrics('$escaped');", null)
    }

    // Drive playback position (approx 60 fps while playing, one-shot when paused)
    LaunchedEffect(currentPosition, isPlaying, bridgeReady) {
        if (!bridgeReady) return@LaunchedEffect
        val delta = if (isPlaying) 16 else 0
        webView.evaluateJavascript("AmllBridge.setTime($currentPosition, $delta);", null)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

/** JS bridge — called from the AMLL player in the WebView. */
private class AmllCallback {
    @JavascriptInterface
    fun onLyricLineClicked(timeMs: Long) {
        // Future: wire to player.seekTo(timeMs)
    }
}
