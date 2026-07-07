package com.leejlredstar.redefinencm.kmp.lyric

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.component.AutoHideMiniPlayerController
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import org.json.JSONObject
import org.koin.compose.koinInject
import kotlin.math.absoluteValue

/**
 * Android actual: AMLL lyric engine in the system WebView.
 *
 * The desktop path can rely on a current WebView2 runtime. Android devices are
 * more varied, so this host loads the same local bundle but asks player.html for
 * an Android profile: full WebView rendering, lower-cost CSS effects, visible
 * status/error text, and no extra Android WebView dependency.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebViewLyricScreen(onBack: () -> Unit) {
    val viewModel: NowPlayingViewModel = koinInject()
    val platformSettings: PlatformSettings = koinInject()
    val rawLyric by viewModel.rawLyric.collectAsState()
    val rawWordLyric by viewModel.rawWordLyric.collectAsState()
    val rawTranslatedLyric by viewModel.rawTranslatedLyric.collectAsState()
    val rawRomanLyric by viewModel.rawRomanLyric.collectAsState()
    val lyricMap by viewModel.lyricMap.collectAsState()
    val lyricMediaId by viewModel.lyricMediaId.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()

    val context = LocalContext.current
    var engineReady by remember { mutableStateOf(false) }
    val lyricForWeb = remember(rawLyric, lyricMap) {
        rawLyric.takeIf { it.isNotBlank() } ?: lyricMap.toLrcFallback()
    }
    val isWaitingForLyrics = rawLyric.isBlank() &&
        rawWordLyric.isBlank() &&
        lyricMap.size == 1 &&
        lyricMap[0L] == "Loading Lyric"
    val showTranslatedLyric = remember {
        platformSettings.getBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, false)
    }
    val showRomanLyric = remember {
        platformSettings.getBoolean(SettingKeys.SHOW_ROMAN_LYRIC, false)
    }

    val webView = remember {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.setSupportZoom(false)

            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(android.graphics.Color.rgb(10, 10, 10))

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                    Log.d("AMLL", "${cm.message()} @ ${cm.sourceId()}:${cm.lineNumber()}")
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    engineReady = false
                    Log.d("AMLL", "page started: $url")
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    Log.d("AMLL", "page finished: $url")
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        val message = "WebView load error ${error.errorCode}: ${error.description}"
                        Log.e("AMLL", message)
                        view.showAmllError(message)
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame) {
                        val message = "HTTP ${errorResponse.statusCode}: ${errorResponse.reasonPhrase}"
                        Log.e("AMLL", message)
                        view.showAmllError(message)
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Log.e("AMLL", "renderer gone, didCrash=${detail.didCrash()}")
                    engineReady = false
                    view.destroy()
                    return true
                }
            }

            addJavascriptInterface(
                AmllCallback(
                    onReady = {
                        post {
                            Log.d("AMLL", "onReady received -> engineReady=true")
                            engineReady = true
                        }
                    },
                    onLineClicked = { timeMs, mediaId ->
                        post {
                            Log.d("AMLL", "line click seek media=$mediaId to $timeMs")
                            viewModel.onLyricLineClick(mediaId, timeMs)
                        }
                    },
                ),
                "AmllCallback",
            )

            loadUrl("file:///android_asset/amll/player.html?platform=android")
        }
    }

    DisposableEffect(webView) {
        onDispose {
            engineReady = false
            webView.stopLoading()
            webView.removeJavascriptInterface("AmllCallback")
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }

    LaunchedEffect(engineReady, lyricMediaId) {
        if (!engineReady) return@LaunchedEffect
        val mediaId = lyricMediaId ?: return@LaunchedEffect
        Log.d("AMLL", "reset lyric surface for media=$mediaId")
        webView.evaluateJavascript(
            "AmllBridge.resetTrack(${JSONObject.quote(mediaId)}); AmllBridge.setTime(0);",
            null,
        )
        webView.showAmllStatus("Waiting for lyrics...")
    }

    LaunchedEffect(
        engineReady,
        lyricMediaId,
        rawWordLyric,
        lyricForWeb,
        rawTranslatedLyric,
        rawRomanLyric,
        isWaitingForLyrics,
    ) {
        if (!engineReady) return@LaunchedEffect
        if (isWaitingForLyrics || (rawWordLyric.isBlank() && lyricForWeb.isBlank())) {
            Log.d("AMLL", "waiting for lyric media=$lyricMediaId")
            webView.evaluateJavascript("AmllBridge.loadLyrics('');", null)
            webView.showAmllStatus("Waiting for lyrics...")
            return@LaunchedEffect
        }
        val mediaId = lyricMediaId ?: return@LaunchedEffect
        val lyricOptions = buildLyricOptionsJson(
            translatedLyric = rawTranslatedLyric,
            romanLyric = rawRomanLyric,
            showTranslatedLyric = showTranslatedLyric,
            showRomanLyric = showRomanLyric,
        )
        if (rawWordLyric.isNotBlank()) {
            Log.d("AMLL", "feeding word lyrics media=$mediaId, len=${rawWordLyric.length}")
            webView.evaluateJavascript(
                "AmllBridge.loadWordLyrics(${JSONObject.quote(rawWordLyric)}, ${JSONObject.quote(mediaId)}, $lyricOptions); AmllBridge.setTime($currentPosition);",
                null,
            )
        } else {
            Log.d("AMLL", "feeding lyrics media=$mediaId, len=${lyricForWeb.length}")
            webView.evaluateJavascript(
                "AmllBridge.loadLyrics(${JSONObject.quote(lyricForWeb)}, ${JSONObject.quote(mediaId)}, $lyricOptions); AmllBridge.setTime($currentPosition);",
                null,
            )
        }
    }

    LaunchedEffect(engineReady, currentPosition) {
        if (!engineReady) return@LaunchedEffect
        webView.evaluateJavascript("AmllBridge.setTime($currentPosition);", null)
    }

    LaunchedEffect(engineReady, metadata?.artworkUri) {
        if (!engineReady) return@LaunchedEffect
        val art = metadata?.artworkUri?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        webView.evaluateJavascript("AmllBridge.setBackground(${JSONObject.quote(art)});", null)
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
                    AppIcons.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        AutoHideMiniPlayerController(
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private class AmllCallback(
    private val onReady: () -> Unit,
    private val onLineClicked: (Long, String?) -> Unit,
) {
    @JavascriptInterface
    fun onReady() = onReady.invoke()

    @JavascriptInterface
    fun onLyricLineClicked(timeMs: Long, mediaId: String?) {
        onLineClicked(timeMs, mediaId)
    }
}

private fun WebView.showAmllStatus(message: String) {
    evaluateJavascript(
        "if (globalThis.AmllPage) AmllPage.setStatus(${JSONObject.quote(message)});",
        null,
    )
}

private fun WebView.showAmllError(message: String) {
    evaluateJavascript(
        "if (globalThis.AmllPage) AmllPage.showError(${JSONObject.quote(message)});",
        null,
    )
}

private fun buildLyricOptionsJson(
    translatedLyric: String,
    romanLyric: String,
    showTranslatedLyric: Boolean,
    showRomanLyric: Boolean,
): String = JSONObject()
    .put("translatedLyric", translatedLyric)
    .put("romanLyric", romanLyric)
    .put("showTranslation", showTranslatedLyric)
    .put("showRoman", showRomanLyric)
    .toString()

private fun LinkedHashMap<Long?, String?>.toLrcFallback(): String =
    entries
        .mapNotNull { (time, text) ->
            val line = text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "${formatLrcTimestamp(time ?: 0L)}$line"
        }
        .joinToString("\n")

private fun formatLrcTimestamp(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val centiseconds = (safe % 1000L / 10L).absoluteValue
    return "[${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${centiseconds.toString().padStart(2, '0')}]"
}
