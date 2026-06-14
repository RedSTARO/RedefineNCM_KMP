package com.leejlredstar.redefinencm.kmp.lyric

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
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
 * Loads [player.html] from assets and drives it via the WebView bridge:
 * - `AmllBridge.loadLyrics(lrcText)` — feeds raw LRC to the AMLL parser
 * - `AmllBridge.setTime(ms, delta)` — updates playback position (called per frame)
 *
 * Injection must wait for the AMLL engine itself, not merely page load: the
 * engine bundle is imported and initialised asynchronously, so the page can
 * finish loading before `globalThis.AmllBridge` exists. The HTML calls
 * `AmllCallback.onReady()` once `init()` succeeds; only then is [engineReady]
 * flipped and the data effects allowed to run.
 *
 * The pure-Compose [FullLyricScreen] is the fallback on platforms without a WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebViewLyricScreen(onBack: () -> Unit) {
    val viewModel: NowPlayingViewModel = koinInject()
    val rawLyric by viewModel.rawLyric.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()

    val context = LocalContext.current
    var engineReady by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false

            // Transparent so the black backdrop behind the WebView shows through —
            // AMLL renders light lyric text with no background of its own, which
            // would be invisible on the WebView's default opaque white.
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // Surface JS console output (incl. engine load/init errors) to logcat.
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    Log.d("AMLL", "${cm.message()} @ ${cm.sourceId()}:${cm.lineNumber()}")
                    return true
                }
            }
            webViewClient = WebViewClient()

            // @JavascriptInterface methods run on a private bridge thread; hop to
            // the WebView's (main) thread via post before touching Compose state.
            addJavascriptInterface(
                AmllCallback(onReady = {
                    post {
                        Log.d("AMLL", "onReady received -> engineReady=true")
                        engineReady = true
                    }
                }),
                "AmllCallback",
            )

            loadUrl("file:///android_asset/amll/player.html")
        }
    }

    // Feed raw LRC to AMLL once the engine is ready and whenever the track changes.
    LaunchedEffect(engineReady, rawLyric) {
        if (!engineReady) return@LaunchedEffect
        if (rawLyric.isEmpty()) {
            Log.d("AMLL", "engineReady but rawLyric is EMPTY (no lyrics fetched)")
            return@LaunchedEffect
        }
        Log.d("AMLL", "feeding lyrics, len=${rawLyric.length}")
        val escaped = rawLyric
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        webView.evaluateJavascript("AmllBridge.loadLyrics('$escaped');", null)
    }

    // Push the playback position as it changes; the page's rAF loop animates.
    LaunchedEffect(engineReady, currentPosition) {
        if (!engineReady) return@LaunchedEffect
        webView.evaluateJavascript("AmllBridge.setTime($currentPosition);", null)
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

/** JS → Kotlin bridge for the AMLL player in the WebView. */
private class AmllCallback(private val onReady: () -> Unit) {
    /** Called from player.html once the AMLL engine has mounted. */
    @JavascriptInterface
    fun onReady() = onReady.invoke()

    @JavascriptInterface
    fun onLyricLineClicked(timeMs: Long) {
        // Future: wire to player.seekTo(timeMs)
    }
}
