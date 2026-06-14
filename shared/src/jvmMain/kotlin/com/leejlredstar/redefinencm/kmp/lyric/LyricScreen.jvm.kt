package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import netscape.javascript.JSObject
import org.koin.compose.koinInject
import java.io.File

/**
 * Desktop (JVM) actual: AMLL lyric engine in a JavaFX [WebView] (WebKit), hosted in a
 * [JFXPanel] and embedded via Compose [SwingPanel].
 *
 * JavaFX is used instead of a Chromium/JCEF embed (whose native CefApp init crashes on this
 * JDK/Windows). All WebView/WebEngine access must happen on the JavaFX Application Thread
 * ([Platform.runLater]); creating a JFXPanel boots that runtime. The same player.html the
 * Android WebView uses is loaded over file://; readiness is signalled back through a
 * `window.amllHost` Java bridge object (vs. Android's @JavascriptInterface).
 */
@Composable
actual fun WebViewLyricScreen(onBack: () -> Unit) {
    val viewModel: NowPlayingViewModel = koinInject()
    val rawLyric by viewModel.rawLyric.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()

    // engineReady is set from the JavaFX thread → use a thread-safe flow.
    val engineReadyFlow = remember { MutableStateFlow(false) }
    val engineReady by engineReadyFlow.collectAsState()
    val engineState = remember { mutableStateOf<WebEngine?>(null) }
    val panel = remember { JFXPanel() }

    LaunchedEffect(Unit) {
        // Keep the JavaFX runtime alive across screen open/close.
        Platform.setImplicitExit(false)
        val dir = withContext(Dispatchers.IO) { extractAmllAssets() }
        Platform.runLater {
            val webView = WebView()
            val engine = webView.engine
            engine.loadWorker.stateProperty().addListener { _, _, state ->
                if (state == Worker.State.SUCCEEDED) {
                    // Expose window.amllHost so player.html's signalReady() can call back.
                    println("AMLL[jfx] page loaded; injecting amllHost bridge")
                    val window = engine.executeScript("window") as JSObject
                    window.setMember("amllHost", AmllJsHost {
                        println("AMLL[jfx] onReady received -> engineReady=true")
                        engineReadyFlow.value = true
                    })
                } else if (state == Worker.State.FAILED) {
                    println("AMLL[jfx] page load failed: ${engine.loadWorker.exception?.message}")
                }
            }
            engine.load(fileUrl(File(dir, "player.html")))
            panel.scene = Scene(webView)
            engineState.value = engine
        }
    }

    // Feed raw LRC once the engine is ready and whenever the track changes.
    LaunchedEffect(engineReady, rawLyric) {
        val engine = engineState.value ?: return@LaunchedEffect
        if (!engineReady) return@LaunchedEffect
        if (rawLyric.isEmpty()) {
            println("AMLL[jfx] engineReady but rawLyric is EMPTY (no lyrics fetched)")
            return@LaunchedEffect
        }
        println("AMLL[jfx] feeding lyrics, len=${rawLyric.length}")
        val escaped = rawLyric
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        Platform.runLater { runCatching { engine.executeScript("AmllBridge.loadLyrics('$escaped');") } }
    }

    // Push playback position; the page's rAF loop animates between updates.
    LaunchedEffect(engineReady, currentPosition) {
        val engine = engineState.value ?: return@LaunchedEffect
        if (!engineReady) return@LaunchedEffect
        Platform.runLater { runCatching { engine.executeScript("AmllBridge.setTime($currentPosition);") } }
    }

    // Set the blurred album-art background for the current track.
    LaunchedEffect(engineReady, metadata?.artworkUri) {
        val engine = engineState.value ?: return@LaunchedEffect
        if (!engineReady) return@LaunchedEffect
        val art = metadata?.artworkUri?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val safe = art.replace("\\", "\\\\").replace("'", "\\'")
        Platform.runLater { runCatching { engine.executeScript("AmllBridge.setBackground('$safe');") } }
    }

    DisposableEffect(Unit) {
        onDispose {
            val engine = engineState.value
            Platform.runLater { runCatching { engine?.load("about:blank") } }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LyricToolbar(onBack)
        SwingPanel(factory = { panel }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

/** Bridge object exposed to JS as `window.amllHost`. Public method is callable from the page. */
class AmllJsHost(private val onReadyCallback: () -> Unit) {
    @Suppress("unused") // called from JavaScript
    fun onReady() {
        onReadyCallback()
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

/** Extract the bundled AMLL assets to a temp dir so the WebView can load them over file://. */
private fun extractAmllAssets(): File {
    val dir = File(System.getProperty("java.io.tmpdir"), "redefinencm-amll").apply { mkdirs() }
    for (name in listOf("player.html", "bundle.js", "style.css")) {
        val res = object {}.javaClass.getResourceAsStream("/amll/$name")
            ?: error("Missing classpath resource: /amll/$name")
        res.use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
    }
    return dir
}

/** Normalize to the three-slash `file:///` form (Windows toURI() gives one slash). */
private fun fileUrl(file: File): String {
    val raw = file.toURI().toString()
    return if (raw.startsWith("file://")) raw else raw.replaceFirst("file:/", "file:///")
}
