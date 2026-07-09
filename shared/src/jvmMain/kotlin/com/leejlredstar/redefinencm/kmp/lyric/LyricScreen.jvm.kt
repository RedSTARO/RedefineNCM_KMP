package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.sun.jna.Native
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.koin.compose.koinInject
import java.awt.Canvas
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import java.awt.Color as AwtColor

/**
 * Desktop (JVM) actual: AMLL lyric engine in the **system WebView**
 * (Windows = WebView2 / Edge Chromium)，通过 [WebviewJna]（直传 HWND 的精简绑定）
 * 嵌入 Compose [SwingPanel] 里的 AWT [Canvas]。
 *
 * 为什么是系统 WebView：AMLL 需要现代内核 + GPU 合成（弹簧动画、逐行模糊、全屏
 * 背景 blur）。JavaFX WebKit 无 GPU 合成——字体/布局/动画均不完整且帧率个位数；
 * KCEF 已归档且在本机 native init 崩溃。WebView2 是 Windows 系统内置常青内核。
 *
 * 就绪信号：player.html 的 signalReady() 调用 window.amllReady()（webview bind）。
 * 所有 eval 经 webview_dispatch 派发到事件循环线程执行（jobs 队列）。
 */
@Composable
actual fun WebViewLyricScreen(onBack: () -> Unit) {
    val viewModel: NowPlayingViewModel = koinInject()
    val settings: PlatformSettings = koinInject()
    val rawLyric by viewModel.rawLyric.collectAsState()
    val rawWordLyric by viewModel.rawWordLyric.collectAsState()
    val rawTranslatedLyric by viewModel.rawTranslatedLyric.collectAsState()
    val rawRomanLyric by viewModel.rawRomanLyric.collectAsState()
    val lyricMediaId by viewModel.lyricMediaId.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()

    val engineReadyFlow = remember { MutableStateFlow(false) }
    val engineReady by engineReadyFlow.collectAsState()
    val showTranslatedLyric = remember {
        settings.getBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, false)
    }
    val showRomanLyric = remember {
        settings.getBoolean(SettingKeys.SHOW_ROMAN_LYRIC, false)
    }

    // 资产解包很快（3 个小文件拷贝），首帧前同步执行即可
    val assetsDir = remember { extractAmllAssets() }
    val canvas = remember { Canvas().apply { background = AwtColor.BLACK } }
    val session = remember(viewModel) {
        WebviewSession(
            readyFlow = engineReadyFlow,
            onLineClicked = { timeMs, mediaId -> viewModel.onLyricLineClick(mediaId, timeMs) },
            onBack = onBack,
        )
    }

    // Canvas 拿到原生句柄（displayable + 有尺寸）后，在专用线程启动 webview 事件循环
    LaunchedEffect(session) {
        while (!canvas.isDisplayable || canvas.width <= 0 || canvas.height <= 0) delay(30)
        session.start(canvas, "${fileUrl(File(assetsDir, "player.html"))}?platform=desktop")
    }

    LaunchedEffect(engineReady, lyricMediaId) {
        if (!engineReady) return@LaunchedEffect
        val mediaId = lyricMediaId ?: return@LaunchedEffect
        session.eval("AmllBridge.resetTrack('${mediaId.escapeJsSingleQuoted()}'); AmllBridge.setTime(0);")
    }

    // Feed raw LRC once the engine is ready and whenever the track changes.
    LaunchedEffect(
        engineReady,
        lyricMediaId,
        rawWordLyric,
        rawLyric,
        rawTranslatedLyric,
        rawRomanLyric,
    ) {
        if (!engineReady) return@LaunchedEffect
        val mediaId = lyricMediaId ?: return@LaunchedEffect
        if (rawWordLyric.isEmpty() && rawLyric.isEmpty()) {
            println("AMLL[wv2] engineReady but rawLyric is EMPTY (no lyrics fetched)")
            return@LaunchedEffect
        }
        val lyricOptions = buildLyricOptionsJs(
            translatedLyric = rawTranslatedLyric,
            romanLyric = rawRomanLyric,
            showTranslatedLyric = showTranslatedLyric,
            showRomanLyric = showRomanLyric,
        )
        if (rawWordLyric.isNotEmpty()) {
            println("AMLL[wv2] feeding word lyrics media=$mediaId, len=${rawWordLyric.length}")
            session.eval(
                "AmllBridge.loadWordLyrics('${rawWordLyric.escapeJsSingleQuoted()}', '${mediaId.escapeJsSingleQuoted()}', $lyricOptions); AmllBridge.setTime($currentPosition);",
            )
        } else {
            println("AMLL[wv2] feeding lyrics media=$mediaId, len=${rawLyric.length}")
            session.eval(
                "AmllBridge.loadLyrics('${rawLyric.escapeJsSingleQuoted()}', '${mediaId.escapeJsSingleQuoted()}', $lyricOptions); AmllBridge.setTime($currentPosition);",
            )
        }
    }

    // Push playback position; the page's rAF loop animates between updates.
    LaunchedEffect(engineReady, currentPosition) {
        if (!engineReady) return@LaunchedEffect
        session.eval("AmllBridge.setTime($currentPosition);")
    }

    // Set the blurred album-art background for the current track (full-res:
    // WebView2 的 GPU 合成下全屏 CSS blur 是免费的).
    LaunchedEffect(engineReady, metadata?.artworkUri) {
        if (!engineReady) return@LaunchedEffect
        val art = metadata?.artworkUri?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val safe = art.replace("\\", "\\\\").replace("'", "\\'")
        session.eval("AmllBridge.setBackground('$safe');")
    }

    DisposableEffect(session) {
        onDispose {
            session.stop()
        }
    }

    SwingPanel(
        factory = { canvas },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * 一次歌词页会话：持有 webview 句柄、事件循环线程与 JNA 回调的强引用
 * （native 侧存了回调指针，Java 侧必须防 GC）。
 */
private class WebviewSession(
    private val readyFlow: MutableStateFlow<Boolean>,
    private val onLineClicked: (Long, String?) -> Unit,
    private val onBack: () -> Unit,
) {
    private val handle = AtomicLong(0)
    private val jobs = ConcurrentLinkedQueue<String>()
    private val callbackScope = MainScope()

    // 强引用回调，防止 JNA 回调桩被 GC（native 正持有其指针）
    private val bindCallback = object : WebviewJna.BindCallback {
        override fun callback(seq: Long, req: String?, arg: Long) {
            println("AMLL[wv2] onReady received -> engineReady=true")
            readyFlow.value = true
            runCatching { WebviewJna.N.webview_return(handle.get(), seq, 0, "null") }
        }
    }
    private val seekCallback = object : WebviewJna.BindCallback {
        override fun callback(seq: Long, req: String?, arg: Long) {
            val (timeMs, mediaId) = parseAmllSeekRequest(req)
            println("AMLL[wv2] line click seek media=$mediaId to $timeMs")
            onLineClicked(timeMs, mediaId)
            runCatching { WebviewJna.N.webview_return(handle.get(), seq, 0, "null") }
        }
    }
    private val backCallback = hostCallback("back") { onBack() }
    private val dispatchCallback = object : WebviewJna.DispatchCallback {
        override fun callback(w: Long, arg: Long) {
            while (true) {
                val js = jobs.poll() ?: break
                runCatching { WebviewJna.N.webview_eval(w, js) }
            }
        }
    }

    fun start(canvas: Canvas, url: String) {
        if (handle.get() != 0L) return
        val canvasHwnd = resolveCanvasHwnd(canvas)
        if (canvasHwnd == null) {
            println("AMLL[wv2] no valid HWND for canvas — cannot embed")
            return
        }
        // AWT 报告逻辑尺寸（DIP），MoveWindow 要物理像素 —— 乘每屏 DPI 缩放
        val width = canvas.physicalWidth()
        val height = canvas.physicalHeight()
        thread(name = "amll-webview2", isDaemon = true) {
            val n = WebviewJna.N
            // 这版 dll 的嵌入分支（window 参数非 null）必崩，只能走自建窗口路径，
            // 然后用 SetParent 把它收编为 Canvas 的子窗口 —— dll 自己的 WndProc、
            // DPI 与 WM_SIZE→resize_webview 逻辑全部保留，尺寸联动免费获得。
            val w = n.webview_create(0, null)
            if (w == 0L) {
                println("AMLL[wv2] webview_create failed (WebView2 runtime missing?)")
                return@thread
            }
            handle.set(w)
            val childHwnd = n.webview_get_window(w)
            if (childHwnd != null) {
                runCatching {
                    reparentIntoCanvas(childHwnd, canvasHwnd, width, height)
                }.onFailure { error ->
                    println("AMLL[wv2] failed to reparent webview window: ${error.message}")
                    n.webview_destroy(w)
                    handle.set(0)
                    return@thread
                }
                println("AMLL[wv2] webview window $childHwnd reparented into canvas $canvasHwnd (${width}x$height)")
                // Canvas 尺寸变化（窗口拉伸/最大化）时让子窗口跟随铺满；
                // dll 的 WndProc 收到 WM_SIZE 后会自行调整 WebView2 controller bounds。
                canvas.addComponentListener(object : java.awt.event.ComponentAdapter() {
                    override fun componentResized(e: java.awt.event.ComponentEvent) {
                        val u32 = com.sun.jna.platform.win32.User32.INSTANCE
                        u32.MoveWindow(
                            com.sun.jna.platform.win32.WinDef.HWND(childHwnd),
                            0, 0, canvas.physicalWidth(), canvas.physicalHeight(), true,
                        )
                    }
                })
            } else {
                println("AMLL[wv2] webview_get_window returned null — closing standalone window")
                n.webview_destroy(w)
                handle.set(0)
                return@thread
            }

            n.webview_bind(w, "amllReady", bindCallback, 0)
            n.webview_bind(w, "amllSeek", seekCallback, 0)
            n.webview_bind(w, "amllBack", backCallback, 0)
            n.webview_navigate(w, url)
            n.webview_run(w) // 阻塞直到 terminate
            n.webview_destroy(w)
            handle.set(0)
            println("AMLL[wv2] webview loop exited")
        }
    }

    /** 把 webview 自建的顶层窗口改造成 Canvas 的无边框子窗口并铺满。 */
    private fun reparentIntoCanvas(
        child: com.sun.jna.Pointer,
        parent: com.sun.jna.Pointer,
        width: Int,
        height: Int,
    ) {
        val u32 = com.sun.jna.platform.win32.User32.INSTANCE
        val childH = com.sun.jna.platform.win32.WinDef.HWND(child)
        val parentH = com.sun.jna.platform.win32.WinDef.HWND(parent)

        val gwlExStyle = -20
        val gwlStyle = com.sun.jna.platform.win32.WinUser.GWL_STYLE
        val swHide = 0
        val swShowna = 8
        val swpNoZOrder = 0x0004
        val swpNoActivate = 0x0010
        val swpFrameChanged = 0x0020
        val swpShowWindow = 0x0040
        val wsChild = 0x40000000L
        val wsVisible = 0x10000000L
        val wsClipChildren = 0x02000000L
        val wsClipSiblings = 0x04000000L
        val wsExToolWindow = 0x00000080L
        val childStyle = wsChild or wsVisible or wsClipChildren or wsClipSiblings

        u32.ShowWindow(childH, swHide)
        u32.SetWindowLongPtr(
            childH,
            gwlStyle,
            com.sun.jna.Pointer.createConstant(childStyle),
        )
        u32.SetWindowLongPtr(
            childH,
            gwlExStyle,
            com.sun.jna.Pointer.createConstant(wsExToolWindow),
        )
        u32.SetParent(childH, parentH)
        if (u32.GetParent(childH)?.pointer != parentH.pointer) {
            u32.ShowWindow(childH, swHide)
            u32.DestroyWindow(childH)
            error("WebView2 host window failed to reparent into AMLL canvas")
        }
        u32.SetWindowPos(
            childH,
            null,
            0,
            0,
            width,
            height,
            swpNoZOrder or swpNoActivate or swpFrameChanged or swpShowWindow,
        )
        u32.MoveWindow(childH, 0, 0, width, height, true)
        u32.ShowWindow(childH, swShowna)
    }

    fun eval(js: String) {
        val w = handle.get()
        if (w == 0L) return
        jobs.add(js)
        runCatching { WebviewJna.N.webview_dispatch(w, dispatchCallback, 0) }
    }

    fun stop() {
        val w = handle.get()
        if (w != 0L) runCatching { WebviewJna.N.webview_terminate(w) }
        callbackScope.cancel()
    }

    /**
     * 取 Canvas 的原生 HWND。webview 的 win32 实现会先 IsWindow 校验，无效则按
     * HWND* 解引用（老语义兼容）——所以绝不能把无效句柄传下去（会 access violation）。
     * JAWT（getComponentPointer）在 Compose SwingPanel 的层次里可能返回非窗口句柄，
     * 此时回退：从顶层窗口 EnumChildWindows 找 AWT Canvas 的原生子窗口（类名 SunAwt*）。
     */
    private fun resolveCanvasHwnd(canvas: Canvas): com.sun.jna.Pointer? {
        val u32 = com.sun.jna.platform.win32.User32.INSTANCE

        fun isRealWindow(p: com.sun.jna.Pointer?): Boolean {
            if (p == null) return false
            val buf = CharArray(64)
            return u32.GetClassName(com.sun.jna.platform.win32.WinDef.HWND(p), buf, 64) > 0
        }

        val direct = runCatching { Native.getComponentPointer(canvas) }.getOrNull()
        if (isRealWindow(direct)) {
            println("AMLL[wv2] canvas hwnd via JAWT: $direct")
            return direct
        }
        println("AMLL[wv2] JAWT gave invalid handle ($direct); enumerating child windows…")

        val window = javax.swing.SwingUtilities.getWindowAncestor(canvas) ?: return null
        val top = runCatching { Native.getComponentPointer(window) }.getOrNull() ?: return null
        if (!isRealWindow(top)) return null

        var found: com.sun.jna.Pointer? = null
        u32.EnumChildWindows(
            com.sun.jna.platform.win32.WinDef.HWND(top),
            { child, _ ->
                val buf = CharArray(64)
                val n = u32.GetClassName(child, buf, 64)
                val cls = String(buf, 0, n.coerceAtLeast(0))
                if (cls.startsWith("SunAwt")) {
                    found = child.pointer
                    false
                } else true
            },
            null,
        )
        println("AMLL[wv2] canvas hwnd via EnumChildWindows: $found")
        return found
    }

    private fun hostCallback(name: String, action: () -> Unit) = object : WebviewJna.BindCallback {
        override fun callback(seq: Long, req: String?, arg: Long) {
            callbackScope.launch {
                println("AMLL[wv2] host action $name")
                action()
            }
            runCatching { WebviewJna.N.webview_return(handle.get(), seq, 0, "null") }
        }
    }
}

private val amllSeekJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun parseAmllSeekRequest(req: String?): Pair<Long, String?> {
    val text = req.orEmpty().trim()
    if (text.isEmpty()) return 0L to null

    runCatching { amllSeekJson.parseToJsonElement(text) }
        .getOrNull()
        ?.let { element ->
            when (element) {
                is JsonArray -> {
                    val time = element.getOrNull(0)?.asLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    val mediaId = element.getOrNull(1)?.asStringOrNull()
                    return time to mediaId
                }
                is JsonObject -> {
                    val time = listOf("timeMs", "positionMs", "time", "position")
                        .firstNotNullOfOrNull { key -> element[key]?.asLongOrNull() }
                        ?.coerceAtLeast(0L)
                        ?: 0L
                    val mediaId = listOf("mediaId", "songId", "id")
                        .firstNotNullOfOrNull { key -> element[key]?.asStringOrNull() }
                    return time to mediaId
                }
                else -> Unit
            }
        }

    Regex("""seekTo:(-?\d+):([^,\]\s]+)""")
        .find(text)
        ?.let { match ->
            val time = match.groupValues[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val mediaId = match.groupValues[2].takeIf { it.isNotBlank() }
            return time to mediaId
        }

    val timeText = Regex("""-?\d+""").find(text)?.value
    val time = timeText?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val mediaId = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"""")
        .findAll(text)
        .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
        .firstOrNull { it.isNotBlank() && it != timeText }
    return time to mediaId
}

private fun kotlinx.serialization.json.JsonElement.asStringOrNull(): String? =
    (this as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

private fun kotlinx.serialization.json.JsonElement.asLongOrNull(): Long? =
    (this as? JsonPrimitive)
        ?.let { primitive ->
            primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
        }

/** AWT 逻辑尺寸 × 每屏 DPI 变换 = Win32 物理像素。 */
private fun Canvas.physicalWidth(): Int =
    (width * (graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0)).toInt()

private fun Canvas.physicalHeight(): Int =
    (height * (graphicsConfiguration?.defaultTransform?.scaleY ?: 1.0)).toInt()

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

private fun String.escapeJsSingleQuoted(): String =
    replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

private fun buildLyricOptionsJs(
    translatedLyric: String,
    romanLyric: String,
    showTranslatedLyric: Boolean,
    showRomanLyric: Boolean,
): String =
    "{translatedLyric:'${translatedLyric.escapeJsSingleQuoted()}'," +
        "romanLyric:'${romanLyric.escapeJsSingleQuoted()}'," +
        "showTranslation:$showTranslatedLyric," +
        "showRoman:$showRomanLyric}"
