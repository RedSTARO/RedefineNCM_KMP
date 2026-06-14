package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.screen.FullLyricScreen
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import dev.datlag.kcef.KCEF
import dev.datlag.kcef.KCEFBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.koin.compose.koinInject
import java.io.File

/**
 * Desktop (JVM) actual: AMLL lyric engine in a KCEF (Chromium) browser, embedded via
 * [SwingPanel]. Falls back to the pure-Compose [FullLyricScreen] if KCEF fails to init.
 *
 * KCEF hosts a heavyweight AWT component, so the back affordance is a real toolbar ABOVE
 * the panel (a Compose overlay would render under the heavyweight surface), not floating.
 * The page is the same player.html the Android WebView uses; it signals readiness through
 * JCEF's `window.cefQuery` message router (vs. Android's @JavascriptInterface).
 */
@Composable
actual fun WebViewLyricScreen(onBack: () -> Unit) {
    val kcefState by KcefManager.state.collectAsState()

    LaunchedEffect(Unit) { KcefManager.ensureInit() }

    when (val s = kcefState) {
        is KcefManager.State.Failed -> FullLyricScreen(onBack = onBack)
        KcefManager.State.Ready -> KcefLyricView(onBack)
        else -> KcefLoading(s, onBack)
    }
}

@Composable
private fun KcefLyricView(onBack: () -> Unit) {
    val viewModel: NowPlayingViewModel = koinInject()
    val rawLyric by viewModel.rawLyric.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()

    // Set from a CEF bridge thread → use a thread-safe flow, observed as Compose state.
    val engineReadyFlow = remember { MutableStateFlow(false) }
    val engineReady by engineReadyFlow.collectAsState()
    var browser by remember { mutableStateOf<KCEFBrowser?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dir = extractAmllAssets()
            val client = KCEF.newClient()
            // Mirror the Android logcat lever: surface JS console + load errors to stdout
            // (tag "AMLL"), since the desktop GUI can't be inspected here.
            client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                override fun onConsoleMessage(
                    b: CefBrowser?,
                    level: CefSettings.LogSeverity?,
                    message: String?,
                    source: String?,
                    line: Int,
                ): Boolean {
                    println("AMLL[console] $message @ $source:$line")
                    return false
                }
            })
            client.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadError(
                    b: CefBrowser?,
                    frame: CefFrame?,
                    errorCode: CefLoadHandler.ErrorCode?,
                    errorText: String?,
                    failedUrl: String?,
                ) {
                    println("AMLL[loadError] $errorText ($errorCode) @ $failedUrl")
                }
            })
            // JS → Kotlin: player.html calls window.cefQuery({request:"onReady"}) once mounted.
            val router = CefMessageRouter.create()
            router.addHandler(
                object : CefMessageRouterHandlerAdapter() {
                    override fun onQuery(
                        b: CefBrowser?,
                        frame: CefFrame?,
                        queryId: Long,
                        request: String?,
                        persistent: Boolean,
                        callback: CefQueryCallback?,
                    ): Boolean {
                        if (request == "onReady") {
                            engineReadyFlow.value = true
                            callback?.success("")
                            return true
                        }
                        return false
                    }
                },
                true,
            )
            client.addMessageRouter(router)
            browser = client.createBrowser(fileUrl(File(dir, "player.html")))
        }
    }

    // Feed raw LRC once the engine is ready and whenever the track changes.
    LaunchedEffect(engineReady, rawLyric) {
        val b = browser ?: return@LaunchedEffect
        if (!engineReady || rawLyric.isEmpty()) return@LaunchedEffect
        val escaped = rawLyric
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        b.executeJavaScript("AmllBridge.loadLyrics('$escaped');", "", 0)
    }

    // Push playback position; the page's rAF loop animates between updates.
    LaunchedEffect(engineReady, currentPosition) {
        val b = browser ?: return@LaunchedEffect
        if (!engineReady) return@LaunchedEffect
        b.executeJavaScript("AmllBridge.setTime($currentPosition);", "", 0)
    }

    DisposableEffect(Unit) {
        // Dispose only the browser — never KCEF.dispose() (process-global, no clean re-init).
        onDispose { browser?.dispose() }
    }

    Column(Modifier.fillMaxSize()) {
        LyricToolbar(onBack)
        val b = browser
        if (b != null) {
            SwingPanel(
                factory = { b.uiComponent },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun KcefLoading(state: KcefManager.State, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LyricToolbar(onBack)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                val msg = when (state) {
                    is KcefManager.State.Downloading -> "正在下载歌词渲染引擎 ${state.pct}%"
                    else -> "正在初始化歌词引擎…"
                }
                Text(msg, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LyricToolbar(onBack: () -> Unit) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
        }
    }
}

/** Extract the bundled AMLL assets to a temp dir so KCEF can load them over file://. */
private fun extractAmllAssets(): File {
    val dir = File(System.getProperty("java.io.tmpdir"), "redefinencm-amll").apply { mkdirs() }
    for (name in listOf("player.html", "bundle.js", "style.css")) {
        val res = KcefManager::class.java.getResourceAsStream("/amll/$name")
            ?: error("Missing classpath resource: /amll/$name")
        res.use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
    }
    return dir
}

/** Normalize to the three-slash `file:///` form Chromium expects (Windows toURI() gives one). */
private fun fileUrl(file: File): String {
    val raw = file.toURI().toString()
    return if (raw.startsWith("file://")) raw else raw.replaceFirst("file:/", "file:///")
}
