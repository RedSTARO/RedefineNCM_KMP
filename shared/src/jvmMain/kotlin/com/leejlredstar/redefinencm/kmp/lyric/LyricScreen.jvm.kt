package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.leejlredstar.redefinencm.kmp.ui.component.AutoHideMiniPlayerController
import com.leejlredstar.redefinencm.kmp.ui.component.NativeSurfaceOverlayCoordinator
import com.leejlredstar.redefinencm.kmp.ui.component.NativeSurfaceOwner
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsSheet
import com.leejlredstar.redefinencm.kmp.ui.screen.FullLyricScreen
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.sun.jna.Native
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.koin.compose.koinInject
import java.awt.Canvas
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import java.awt.Color as AwtColor

private const val AMLL_READY_TIMEOUT_MILLIS = 10_000L
private const val NATIVE_HOST_LAYOUT_TIMEOUT_MILLIS = 1_000L

actual val supportsDynamicNowPlayingCover: Boolean
    get() = desktopEmbeddedWebViewSupported()

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
    if (!desktopEmbeddedWebViewSupported()) {
        FullLyricScreen(onBack = onBack)
        return
    }

    val viewModel: NowPlayingViewModel = koinInject()
    val settings: PlatformSettings = koinInject()
    val rawLyric by viewModel.rawLyric.collectAsState()
    val rawWordLyric by viewModel.rawWordLyric.collectAsState()
    val rawTranslatedLyric by viewModel.rawTranslatedLyric.collectAsState()
    val rawRomanLyric by viewModel.rawRomanLyric.collectAsState()
    val lyricUiState by viewModel.lyricUiState.collectAsState()
    val lyricMediaId by viewModel.lyricMediaId.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()
    val dynamicCoverUrl by viewModel.dynamicCoverUrl.collectAsState()
    val songWikiUiState by viewModel.songWikiUiState.collectAsState()

    val engineReadyFlow = remember { MutableStateFlow(false) }
    val engineReady by engineReadyFlow.collectAsState()
    val engineErrorFlow = remember { MutableStateFlow<String?>(null) }
    val engineError by engineErrorFlow.collectAsState()
    if (engineError != null) {
        FullLyricScreen(onBack = onBack)
        return
    }
    val showTranslatedLyric = remember {
        settings.getBoolean(SettingKeys.SHOW_TRANSLATED_LYRIC, false)
    }
    val showRomanLyric = remember {
        settings.getBoolean(SettingKeys.SHOW_ROMAN_LYRIC, false)
    }
    val activeOverlaySources by NativeSurfaceOverlayCoordinator.activeSources.collectAsState()
    val externalOverlayActive = activeOverlaySources.isNotEmpty()
    var controlsRevealRequest by remember { mutableIntStateOf(0) }
    var inPageOverlayActive by remember { mutableStateOf(false) }
    var showSongWikiDetails by remember { mutableStateOf(false) }
    val lyricStateOverlayActive = lyricUiState !is LyricUiState.Content
    val nativeSurfaceVisible = engineReady &&
        !inPageOverlayActive &&
        !externalOverlayActive &&
        !showSongWikiDetails &&
        !lyricStateOverlayActive

    // 只在进程内首次打开时解包；反复开关页面不再同步重写约 440 KiB 资产。
    val assetsDir = remember { extractedAmllAssets }
    val canvas = remember { Canvas().apply { background = AwtColor.BLACK } }
    val session = remember(viewModel) {
        WebviewSession(
            readyFlow = engineReadyFlow,
            errorFlow = engineErrorFlow,
            initiallyVisible = nativeSurfaceVisible,
            onLineClicked = { timeMs, mediaId -> viewModel.onLyricLineClick(mediaId, timeMs) },
            onBack = onBack,
            onControlsRequested = { controlsRevealRequest++ },
            onSongWikiRequested = {
                showSongWikiDetails = true
                viewModel.getSongWikiSummary()
            },
        )
    }
    var nativeHostBootstrapComplete by remember(session) { mutableStateOf(false) }
    val nativeHostVisible = !nativeHostBootstrapComplete || nativeSurfaceVisible
    var nativeSurfaceOwner by remember(session) { mutableStateOf<NativeSurfaceOwner?>(null) }

    // Canvas 拿到原生句柄（displayable + 有尺寸）后，在专用线程启动 webview 事件循环
    LaunchedEffect(session) {
        while (!canvas.isDisplayable || canvas.width <= 0 || canvas.height <= 0) delay(30)
        session.start(canvas, "${fileUrl(File(assetsDir, "player.html"))}?platform=desktop")
        // start() 已同步取得 Canvas HWND；之后宿主可在 WebView2 后台初始化时安全折叠。
        nativeHostBootstrapComplete = true
        delay(AMLL_READY_TIMEOUT_MILLIS)
        if (!engineReadyFlow.value && engineErrorFlow.value == null) {
            engineErrorFlow.value = "AMLL 页面初始化超时"
        }
    }

    LaunchedEffect(engineReady) {
        if (engineReady) session.installControlsRevealHook()
    }

    LaunchedEffect(nativeSurfaceVisible, nativeHostVisible, nativeSurfaceOwner) {
        val nativeWindowUpdated = if (nativeSurfaceVisible) {
            canvas.isVisible = true
            awaitNativeHostLayout(canvas, visible = true) &&
                session.resizeNativeWindow(canvas) &&
                session.setNativeWindowVisible(true)
        } else {
            val hidden = session.setNativeWindowVisible(false)
            // 初始化前保留一次有尺寸的 Canvas 以取得稳定 HWND；之后连同 Swing 宿主一起折叠。
            canvas.isVisible = nativeHostVisible
            val hostUpdated = awaitNativeHostLayout(canvas, visible = nativeHostVisible)
            hidden && hostUpdated
        }
        val owner = nativeSurfaceOwner
        if (owner != null && nativeWindowUpdated) {
            NativeSurfaceOverlayCoordinator.reportNativeSurfaceVisible(owner, nativeHostVisible)
        }
        if (nativeHostBootstrapComplete && !nativeWindowUpdated && engineErrorFlow.value == null) {
            engineErrorFlow.value = if (nativeSurfaceVisible) {
                "AMLL 原生窗口恢复超时"
            } else {
                "AMLL 原生窗口隐藏超时"
            }
        }
    }

    LaunchedEffect(engineReady, lyricMediaId) {
        if (!engineReady) return@LaunchedEffect
        val mediaId = lyricMediaId ?: return@LaunchedEffect
        val position = currentPosition.coerceAtLeast(0L)
        session.eval("AmllBridge.resetTrack('${mediaId.escapeJsSingleQuoted()}'); AmllBridge.setTime($position);")
    }

    // Feed raw LRC once the engine is ready and whenever the track changes.
    LaunchedEffect(
        engineReady,
        lyricMediaId,
        rawWordLyric,
        rawLyric,
        rawTranslatedLyric,
        rawRomanLyric,
        lyricUiState,
    ) {
        if (!engineReady) return@LaunchedEffect
        val mediaId = lyricMediaId ?: return@LaunchedEffect
        if (lyricUiState !is LyricUiState.Content) {
            session.eval("AmllBridge.loadLyrics('');")
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
        session.evalLatest("AmllBridge.setTime($currentPosition);")
    }

    // Set the blurred album-art background for the current track (full-res:
    // WebView2 的 GPU 合成下全屏 CSS blur 是免费的).
    LaunchedEffect(engineReady, metadata?.artworkUri) {
        if (!engineReady) return@LaunchedEffect
        val art = metadata?.artworkUri?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        session.eval("AmllBridge.setBackground('${art.escapeJsSingleQuoted()}');")
    }

    LaunchedEffect(engineReady, dynamicCoverUrl) {
        if (!engineReady) return@LaunchedEffect
        val command = dynamicCoverUrl
            ?.takeIf(String::isNotBlank)
            ?.let { "AmllPage.setDynamicCover('${it.escapeJsSingleQuoted()}');" }
            ?: "AmllPage.clearDynamicCover();"
        session.eval("if (globalThis.AmllPage) $command")
    }

    LaunchedEffect(engineReady, showSongWikiDetails) {
        if (engineReady) {
            session.eval("if (globalThis.AmllPage) AmllPage.resetSongWiki();")
        }
    }

    LaunchedEffect(metadata?.id) {
        showSongWikiDetails = false
    }

    DisposableEffect(session) {
        val owner = NativeSurfaceOverlayCoordinator.attachNativeSurface(nativeHostVisible)
        nativeSurfaceOwner = owner
        onDispose {
            session.setNativeWindowVisible(false)
            NativeSurfaceOverlayCoordinator.detachNativeSurface(owner)
            session.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black),
    ) {
        SwingPanel(
            background = Color.Black,
            factory = { canvas },
            modifier = Modifier
                .collapseNativeHostWhenHidden(nativeHostVisible)
                .fillMaxSize(),
        )

        LyricStateOverlay(
            state = if (!engineReady && lyricUiState is LyricUiState.Content) {
                LyricUiState.Loading
            } else {
                lyricUiState
            },
            onRetry = viewModel::retryLyrics,
        )

        AutoHideMiniPlayerController(
            modifier = Modifier.fillMaxSize(),
            initialExpanded = false,
            showCollapsedWhenHidden = false,
            externalRevealRequest = controlsRevealRequest,
            onOverlayVisibilityChanged = { inPageOverlayActive = it },
        )
    }

    SongWikiDetailsSheet(
        visible = showSongWikiDetails,
        songTitle = metadata?.title,
        state = songWikiUiState,
        onDismiss = { showSongWikiDetails = false },
        onRetry = viewModel::getSongWikiSummary,
    )
}

/**
 * 一次歌词页会话：持有 webview 句柄、事件循环线程与 JNA 回调的强引用
 * （native 侧存了回调指针，Java 侧必须防 GC）。
 */
private class WebviewSession(
    private val readyFlow: MutableStateFlow<Boolean>,
    private val errorFlow: MutableStateFlow<String?>,
    initiallyVisible: Boolean,
    private val onLineClicked: (Long, String?) -> Unit,
    private val onBack: () -> Unit,
    private val onControlsRequested: () -> Unit,
    private val onSongWikiRequested: () -> Unit,
) {
    private val handle = AtomicLong(0)
    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val eventThread = AtomicReference<Thread?>(null)
    private val jobsLock = Any()
    private val jobs = ArrayDeque<String>()
    private val latestEval = AtomicReference<String?>(null)
    private val desiredNativeWindowVisible = AtomicBoolean(initiallyVisible)
    private val nativeVisibilityLock = Any()
    private val callbackScope = MainScope()
    @Volatile private var childWindow: com.sun.jna.Pointer? = null

    // 强引用回调，防止 JNA 回调桩被 GC（native 正持有其指针）
    private val bindCallback = object : WebviewJna.BindCallback {
        override fun callback(seq: Long, req: String?, arg: Long) {
            callbackScope.launch {
                println("AMLL[wv2] onReady received -> engineReady=true")
                readyFlow.value = true
            }
            runCatching { WebviewJna.N.webview_return(handle.get(), seq, 0, "null") }
        }
    }
    private val seekCallback = object : WebviewJna.BindCallback {
        override fun callback(seq: Long, req: String?, arg: Long) {
            val (timeMs, mediaId) = parseAmllSeekRequest(req)
            callbackScope.launch {
                println("AMLL[wv2] line click seek media=$mediaId to $timeMs")
                onLineClicked(timeMs, mediaId)
            }
            runCatching { WebviewJna.N.webview_return(handle.get(), seq, 0, "null") }
        }
    }
    private val backCallback = hostCallback("back") { onBack() }
    private val controlsCallback = hostCallback("controls") { onControlsRequested() }
    private val songWikiCallback = hostCallback("song wiki") { onSongWikiRequested() }
    private val dispatchCallback = object : WebviewJna.DispatchCallback {
        override fun callback(w: Long, arg: Long) {
            while (true) {
                val js = synchronized(jobsLock) {
                    if (jobs.isEmpty()) null else jobs.removeFirst()
                } ?: break
                runCatching { WebviewJna.N.webview_eval(w, js) }
            }
            latestEval.getAndSet(null)?.let { js ->
                runCatching { WebviewJna.N.webview_eval(w, js) }
            }
        }
    }

    fun start(canvas: Canvas, url: String) {
        if (!started.compareAndSet(false, true) || stopped.get()) return
        val canvasHwnd = resolveCanvasHwnd(canvas)
        if (canvasHwnd == null) {
            errorFlow.value = "无法获取桌面窗口句柄"
            started.set(false)
            return
        }
        // AWT 报告逻辑尺寸（DIP），MoveWindow 要物理像素 —— 乘每屏 DPI 缩放
        val width = canvas.physicalWidth()
        val height = canvas.physicalHeight()
        val worker = thread(name = "amll-webview2", isDaemon = true, start = false) {
            var native: WebviewJna? = null
            var webview = 0L
            var resizeListener: java.awt.event.ComponentListener? = null
            try {
                if (stopped.get()) return@thread
                native = WebviewJna.N
                webview = native.webview_create(0, null)
                if (webview == 0L) error("WebView2 runtime unavailable")
                if (stopped.get()) return@thread
                if (!handle.compareAndSet(0L, webview)) error("WebView session already owns a handle")

                val childHwnd = native.webview_get_window(webview)
                    ?: error("webview_get_window returned null")
                reparentIntoCanvas(childHwnd, canvasHwnd, width, height)
                println("AMLL[wv2] webview window $childHwnd reparented into canvas $canvasHwnd (${width}x$height)")

                resizeListener = object : java.awt.event.ComponentAdapter() {
                    override fun componentResized(e: java.awt.event.ComponentEvent) {
                        resizeNativeWindow(canvas)
                    }
                }
                canvas.addComponentListener(resizeListener)

                if (stopped.get()) {
                    setNativeWindowVisible(false)
                    return@thread
                }

                native.webview_bind(webview, "amllReady", bindCallback, 0)
                native.webview_bind(webview, "amllSeek", seekCallback, 0)
                native.webview_bind(webview, "amllBack", backCallback, 0)
                native.webview_bind(webview, "amllControls", controlsCallback, 0)
                native.webview_bind(webview, "amllSongWikiRequested", songWikiCallback, 0)
                native.webview_navigate(webview, url)
                if (stopped.get()) native.webview_terminate(webview)
                native.webview_run(webview)
            } catch (error: Throwable) {
                if (!stopped.get()) {
                    System.err.println("AMLL[wv2] session failed: ${error.message}")
                    errorFlow.value = error.message ?: "WebView2 启动失败"
                }
            } finally {
                resizeListener?.let(canvas::removeComponentListener)
                readyFlow.value = false
                synchronized(nativeVisibilityLock) {
                    childWindow = null
                }
                synchronized(jobsLock) { jobs.clear() }
                latestEval.set(null)
                if (webview != 0L) {
                    handle.compareAndSet(webview, 0L)
                    runCatching { native?.webview_destroy(webview) }
                }
                started.set(false)
                eventThread.compareAndSet(Thread.currentThread(), null)
                println("AMLL[wv2] webview loop exited")
            }
        }
        eventThread.set(worker)
        if (stopped.get()) {
            eventThread.compareAndSet(worker, null)
            started.set(false)
            return
        }
        worker.start()
    }

    /** 把 webview 自建的顶层窗口改造成 Canvas 的无边框子窗口并铺满。 */
    private fun reparentIntoCanvas(
        child: com.sun.jna.Pointer,
        parent: com.sun.jna.Pointer,
        width: Int,
        height: Int,
    ) = synchronized(nativeVisibilityLock) {
        val u32 = com.sun.jna.platform.win32.User32.INSTANCE
        val childH = com.sun.jna.platform.win32.WinDef.HWND(child)
        val parentH = com.sun.jna.platform.win32.WinDef.HWND(parent)

        val gwlExStyle = -20
        val gwlStyle = com.sun.jna.platform.win32.WinUser.GWL_STYLE
        val swHide = 0
        val swpNoZOrder = 0x0004
        val swpNoActivate = 0x0010
        val swpFrameChanged = 0x0020
        val wsChild = 0x40000000L
        val wsClipChildren = 0x02000000L
        val wsClipSiblings = 0x04000000L
        val wsExToolWindow = 0x00000080L
        val childStyle = wsChild or wsClipChildren or wsClipSiblings

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
            swpNoZOrder or swpNoActivate or swpFrameChanged,
        )
        u32.MoveWindow(childH, 0, 0, width, height, true)
        if (!applyNativeWindowVisibilityLocked(child, desiredNativeWindowVisible.get())) {
            error("WebView2 host window visibility did not reach the requested state")
        }
        childWindow = child
    }

    fun eval(js: String) {
        if (stopped.get()) return
        val w = handle.get()
        if (w == 0L) return
        synchronized(jobsLock) {
            if (jobs.size >= MAX_PENDING_EVALS) jobs.removeFirst()
            jobs.addLast(js)
        }
        dispatch(w)
    }

    fun evalLatest(js: String) {
        if (stopped.get()) return
        val w = handle.get()
        if (w == 0L) return
        latestEval.set(js)
        dispatch(w)
    }

    private fun dispatch(w: Long) {
        runCatching { WebviewJna.N.webview_dispatch(w, dispatchCallback, 0) }
    }

    fun installControlsRevealHook() {
        eval(
            """
            if (!globalThis.__redefineControlsRevealBound) {
              globalThis.__redefineControlsRevealBound = true;
              const revealRedefineControls = (event) => {
                const target = event && event.target;
                const wikiOverlay = document.getElementById('wiki-overlay');
                if ((target && target.closest && target.closest('#wiki-info, #wiki-overlay')) ||
                    (wikiOverlay && !wikiOverlay.hidden)) return;
                try {
                  if (typeof globalThis.amllControls === 'function') globalThis.amllControls();
                } catch (_) {}
              };
              document.addEventListener('pointerdown', revealRedefineControls, true);
              document.addEventListener('keydown', revealRedefineControls, true);
            }
            """.trimIndent(),
        )
    }

    fun setNativeWindowVisible(visible: Boolean): Boolean {
        desiredNativeWindowVisible.set(visible)
        return synchronized(nativeVisibilityLock) {
            val child = childWindow ?: return@synchronized true
            applyNativeWindowVisibilityLocked(child, desiredNativeWindowVisible.get())
        }
    }

    fun resizeNativeWindow(canvas: Canvas): Boolean = synchronized(nativeVisibilityLock) {
        val child = childWindow ?: return@synchronized false
        runCatching {
            com.sun.jna.platform.win32.User32.INSTANCE.MoveWindow(
                com.sun.jna.platform.win32.WinDef.HWND(child),
                0,
                0,
                canvas.physicalWidth(),
                canvas.physicalHeight(),
                true,
            )
        }.getOrDefault(false)
    }

    private fun applyNativeWindowVisibilityLocked(
        child: com.sun.jna.Pointer,
        visible: Boolean,
    ): Boolean {
        val cmd = if (visible) 8 else 0
        return runCatching {
            val user32 = com.sun.jna.platform.win32.User32.INSTANCE
            val hwnd = com.sun.jna.platform.win32.WinDef.HWND(child)
            user32.ShowWindow(hwnd, cmd)
            user32.IsWindowVisible(hwnd) == visible
        }.getOrDefault(false)
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        readyFlow.value = false
        // 原生子窗口不受 Compose 的离场绘制约束；先隐藏，避免销毁期间盖住新页面。
        setNativeWindowVisible(false)
        synchronized(jobsLock) { jobs.clear() }
        latestEval.set(null)
        val w = handle.get()
        if (w != 0L) runCatching { WebviewJna.N.webview_terminate(w) }
        callbackScope.cancel()
        // webview_terminate 会唤醒事件循环；finally 在专用线程完成 destroy。
        // DisposableEffect 运行在 Compose/AWT UI 线程，不能在这里 join。
    }

    /**
     * 取 Canvas 的原生 HWND。webview 的 win32 实现会先 IsWindow 校验，无效则按
     * HWND* 解引用（老语义兼容）——所以绝不能把无效句柄传下去（会 access violation）。
     * JAWT（getComponentPointer）在 Compose SwingPanel 的层次里可能返回非窗口句柄。
     * 回退枚举时必须同时匹配 Canvas 类名与物理尺寸；取第一个 SunAwt 子窗口可能会
     * 错把 WebView2 挂到 Compose 自己的根 Canvas，继而覆盖整个应用。
     */
    private fun resolveCanvasHwnd(canvas: Canvas): com.sun.jna.Pointer? {
        val u32 = com.sun.jna.platform.win32.User32.INSTANCE

        fun inspectWindow(pointer: com.sun.jna.Pointer?): CanvasWindowCandidate<com.sun.jna.Pointer>? {
            if (pointer == null) return null
            val hwnd = com.sun.jna.platform.win32.WinDef.HWND(pointer)
            val buf = CharArray(64)
            val length = u32.GetClassName(hwnd, buf, buf.size)
            if (length <= 0) return null
            val rect = com.sun.jna.platform.win32.WinDef.RECT()
            if (!u32.GetWindowRect(hwnd, rect)) return null
            return CanvasWindowCandidate(
                handle = pointer,
                className = String(buf, 0, length),
                width = rect.right - rect.left,
                height = rect.bottom - rect.top,
            )
        }

        val expectedWidth = canvas.physicalWidth()
        val expectedHeight = canvas.physicalHeight()

        val direct = runCatching { Native.getComponentPointer(canvas) }.getOrNull()
        val directMatch = selectCanvasWindowCandidate(
            inspectWindow(direct)?.let(::listOf).orEmpty(),
            expectedWidth,
            expectedHeight,
        )
        if (directMatch != null) {
            println("AMLL[wv2] canvas hwnd via JAWT: $direct")
            return directMatch
        }
        println("AMLL[wv2] JAWT did not identify the AMLL canvas ($direct); enumerating exact-size child windows…")

        val window = javax.swing.SwingUtilities.getWindowAncestor(canvas) ?: return null
        val top = runCatching { Native.getComponentPointer(window) }.getOrNull() ?: return null
        if (inspectWindow(top) == null) return null

        val candidates = mutableListOf<CanvasWindowCandidate<com.sun.jna.Pointer>>()
        u32.EnumChildWindows(
            com.sun.jna.platform.win32.WinDef.HWND(top),
            { child, _ ->
                inspectWindow(child.pointer)?.let(candidates::add)
                true
            },
            null,
        )
        val found = selectCanvasWindowCandidate(candidates, expectedWidth, expectedHeight)
        println(
            "AMLL[wv2] canvas hwnd via exact EnumChildWindows match: $found " +
                "(expected=${expectedWidth}x$expectedHeight, candidates=${candidates.size})",
        )
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

    private companion object {
        const val MAX_PENDING_EVALS = 64
    }
}

internal data class CanvasWindowCandidate<T>(
    val handle: T,
    val className: String,
    val width: Int,
    val height: Int,
)

internal fun <T> selectCanvasWindowCandidate(
    candidates: List<CanvasWindowCandidate<T>>,
    expectedWidth: Int,
    expectedHeight: Int,
    tolerance: Int = 2,
): T? = candidates
    .filter { candidate ->
        candidate.className.startsWith("SunAwt") &&
            candidate.className.contains("Canvas", ignoreCase = true) &&
            kotlin.math.abs(candidate.width - expectedWidth) <= tolerance &&
            kotlin.math.abs(candidate.height - expectedHeight) <= tolerance
    }
    .singleOrNull()
    ?.handle

private fun Modifier.collapseNativeHostWhenHidden(visible: Boolean): Modifier =
    layout { measurable, constraints ->
        val measuredConstraints = if (visible) constraints else Constraints.fixed(0, 0)
        val placeable = measurable.measure(measuredConstraints)
        val width = if (visible) placeable.width else 0
        val height = if (visible) placeable.height else 0
        layout(width, height) {
            // 仍放置同一个 SwingPanel，避免移除 Canvas peer 时连带销毁其 WebView2 子 HWND。
            placeable.place(0, 0)
        }
    }

private suspend fun awaitNativeHostLayout(canvas: Canvas, visible: Boolean): Boolean =
    withTimeoutOrNull(NATIVE_HOST_LAYOUT_TIMEOUT_MILLIS) {
        var settled = false
        while (!settled) {
            withFrameNanos { }
            val host = canvas.parent
            settled = if (visible) {
                host != null &&
                    host.isShowing &&
                    host.width > 0 &&
                    host.height > 0 &&
                    canvas.isShowing &&
                    canvas.width > 0 &&
                    canvas.height > 0
            } else {
                host == null || !host.isShowing || host.width <= 0 || host.height <= 0
            }
        }
        true
    } ?: false

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

    SEEK_COMMAND_PATTERN
        .find(text)
        ?.let { match ->
            val time = match.groupValues[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val mediaId = match.groupValues[2].takeIf { it.isNotBlank() }
            return time to mediaId
        }

    val timeText = INTEGER_PATTERN.find(text)?.value
    val time = timeText?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val mediaId = QUOTED_STRING_PATTERN
        .findAll(text)
        .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
        .firstOrNull { it.isNotBlank() && it != timeText }
    return time to mediaId
}

private fun kotlinx.serialization.json.JsonElement.asStringOrNull(): String? =
    (this as? JsonPrimitive)
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

private val SEEK_COMMAND_PATTERN = Regex("""seekTo:(-?\d+):([^,\]\s]+)""")
private val INTEGER_PATTERN = Regex("""-?\d+""")
private val QUOTED_STRING_PATTERN = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"""")

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
private val extractedAmllAssets: File by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    extractAmllAssets()
}

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

internal fun desktopEmbeddedWebViewSupported(
    osName: String = System.getProperty("os.name").orEmpty(),
    osArch: String = System.getProperty("os.arch").orEmpty(),
): Boolean =
    osName.contains("Windows", ignoreCase = true) &&
        (osArch.equals("amd64", ignoreCase = true) || osArch.equals("x86_64", ignoreCase = true))
