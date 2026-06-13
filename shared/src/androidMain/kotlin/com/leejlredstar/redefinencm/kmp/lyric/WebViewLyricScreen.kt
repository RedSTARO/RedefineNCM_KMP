package com.leejlredstar.redefinencm.kmp.lyric

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import org.koin.compose.koinInject

/**
 * Full-screen lyric display using Apple Music-Like Lyrics WebView engine.
 *
 * Loads the AMLL HTML player from assets and drives it via
 * [WebView.evaluateJavascript] — raw LRC text and current position
 * updates are injected each frame.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewLyricScreen(
    onBack: () -> Unit = {},
    viewModel: NowPlayingViewModel = koinInject(),
) {
    val rawLyric by viewModel.rawLyric.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ) = false
            }
            webChromeClient = WebChromeClient()

            addJavascriptInterface(AmllBridgeCallback(), "AmllCallback")

            loadUrl("file:///android_asset/amll/player.html")
        }
    }

    // Push lyric data + position when lyrics or position changes.
    // Only inject after the page has loaded (amllBridgeReady flag).
    var bridgeReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                bridgeReady = true
            }
        }
    }

    LaunchedEffect(rawLyric) {
        if (!bridgeReady || rawLyric.isEmpty()) return@LaunchedEffect
        val escaped = rawLyric
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        webView.evaluateJavascript(
            "AmllBridge.loadLyrics('$escaped');",
            null,
        )
    }

    LaunchedEffect(currentPosition, isPlaying) {
        if (!bridgeReady) return@LaunchedEffect
        val delta = if (isPlaying) 16 else 0 // ~60fps frame advance
        webView.evaluateJavascript(
            "AmllBridge.setTime($currentPosition, $delta);",
            null,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )

        // Back button — overlay on top of WebView
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
            ) {
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

/**
 * Bridge for JS → Kotlin communication.
 * Currently empty — can be extended for tap-to-seek, lyric line clicks, etc.
 */
private class AmllBridgeCallback {
    @JavascriptInterface
    fun onLyricLineClicked(timeMs: Long) {
        // TODO: wire up to player.seekTo(timeMs) when ready
    }
}
