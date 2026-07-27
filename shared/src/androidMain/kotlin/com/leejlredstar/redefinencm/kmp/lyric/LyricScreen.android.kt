package com.leejlredstar.redefinencm.kmp.lyric

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
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
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.component.AutoHideMiniPlayerController
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveMotion
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsButton
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsSheet
import com.leejlredstar.redefinencm.kmp.util.LyricParser
import com.leejlredstar.redefinencm.kmp.util.DOWNLOAD_RELATIVE_PATH
import com.leejlredstar.redefinencm.kmp.util.DOWNLOAD_SUBDIR
import com.leejlredstar.redefinencm.kmp.util.isLocalArtworkSidecarFileName
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream
import java.io.File

actual val supportsDynamicNowPlayingCover: Boolean = true

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
    val rawLyric by viewModel.rawLyric.collectAsState()
    val rawWordLyric by viewModel.rawWordLyric.collectAsState()
    val rawTtmlLyric by viewModel.rawTtmlLyric.collectAsState()
    val rawTranslatedLyric by viewModel.rawTranslatedLyric.collectAsState()
    val rawRomanLyric by viewModel.rawRomanLyric.collectAsState()
    val lyricMap by viewModel.lyricMap.collectAsState()
    val lyricUiState by viewModel.lyricUiState.collectAsState()
    val lyricMediaId by viewModel.lyricMediaId.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()
    val playerStatusRestoreState by viewModel.playerStatusRestoreState.collectAsState()
    val localArtworkActive by viewModel.localArtworkActive.collectAsState()
    val remoteArtworkUri by viewModel.remoteArtworkUri.collectAsState()
    val dynamicCoverUiState by viewModel.dynamicCoverUiState.collectAsState()
    val dynamicCoverUrl = dynamicCoverUiState.urlFor(metadata?.id)
    val songWikiUiState by viewModel.songWikiUiState.collectAsState()
    val showTranslatedLyric by viewModel.showTranslatedLyric.collectAsState()
    val showRomanLyric by viewModel.showRomanLyric.collectAsState()

    val context = LocalContext.current
    var engineReady by remember { mutableStateOf(false) }
    var rendererGeneration by remember { mutableIntStateOf(0) }
    var showSongWikiDetails by remember { mutableStateOf(false) }
    var localAmllArtwork by remember { mutableStateOf<Pair<String, String>?>(null) }
    val amllArtworkUri = metadata?.let { media ->
        if (!localArtworkActive) {
            media.artworkUri
        } else {
            localAmllArtwork
                ?.takeIf { (mediaId, _) -> mediaId == media.id }
                ?.second
                ?: remoteArtworkUri
        }
    }.orEmpty()
    LaunchedEffect(metadata?.id, metadata?.artworkUri, localArtworkActive, remoteArtworkUri) {
        val media = metadata
        if (media == null || !localArtworkActive) {
            localAmllArtwork = null
            return@LaunchedEffect
        }
        val dataUri = withContext(Dispatchers.IO) {
            localArtworkDataUri(
                context = context,
                songId = media.id.toLongOrNull(),
                uriText = media.artworkUri,
            )
        }
        if (metadata?.id == media.id && localArtworkActive) {
            localAmllArtwork = media.id to (dataUri ?: remoteArtworkUri)
        }
    }
    val lyricForWeb = remember(rawLyric, lyricMap, lyricUiState) {
        if (lyricUiState is LyricUiState.Content) {
            rawLyric.takeIf { it.isNotBlank() } ?: lyricMap.toLrcFallback()
        } else {
            ""
        }
    }
    val webView = remember(context, rendererGeneration) {
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
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

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
                    view.post {
                        (view.parent as? ViewGroup)?.removeView(view)
                        rendererGeneration += 1
                    }
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
        val position = currentPosition.coerceAtLeast(0L)
        webView.evaluateJavascript(
            "AmllBridge.resetTrack(${JSONObject.quote(mediaId)}); AmllBridge.setTime($position);",
            null,
        )
    }

    LaunchedEffect(
        engineReady,
        lyricMediaId,
        rawTtmlLyric,
        rawWordLyric,
        lyricForWeb,
        rawTranslatedLyric,
        rawRomanLyric,
        showTranslatedLyric,
        showRomanLyric,
        lyricUiState,
        metadata?.id,
        playerStatusRestoreState,
    ) {
        if (!engineReady) return@LaunchedEffect
        if (lyricUiState !is LyricUiState.Content) {
            Log.d("AMLL", "waiting for lyric media=$lyricMediaId")
            webView.evaluateJavascript("AmllBridge.loadLyrics('');", null)
            when (val state = lyricUiState) {
                is LyricUiState.Idle -> webView.showAmllStatus(
                    when {
                        metadata != null -> "正在恢复歌词…"
                        playerStatusRestoreState is PlayerStatusRestoreState.Loading ->
                            "正在恢复播放…"
                        else -> "等待播放…"
                    },
                )
                is LyricUiState.Loading -> webView.showAmllStatus("正在加载歌词…")
                is LyricUiState.Empty -> webView.showAmllStatus(
                    if (state.capabilityLevel == LyricCapabilityLevel.UNSYNCED) {
                        "歌词无时间戳"
                    } else {
                        "暂无歌词"
                    },
                )
                is LyricUiState.Error -> webView.showAmllError(state.message)
                is LyricUiState.Content -> Unit
            }
            return@LaunchedEffect
        }
        val contentState = lyricUiState as LyricUiState.Content
        val mediaId = lyricMediaId ?: return@LaunchedEffect
        val lyricOptions = buildLyricOptionsJson(
            translatedLyric = rawTranslatedLyric,
            romanLyric = rawRomanLyric,
            showTranslatedLyric = showTranslatedLyric,
            showRomanLyric = showRomanLyric,
        )
        if (rawTtmlLyric.isNotBlank()) {
            Log.d("AMLL", "feeding TTML media=$mediaId, len=${rawTtmlLyric.length}")
            webView.evaluateJavascript(
                "AmllBridge.loadTtmlLyrics(${JSONObject.quote(rawTtmlLyric)}, ${JSONObject.quote(mediaId)}, $lyricOptions, ${JSONObject.quote(lyricForWeb)}); AmllBridge.setTime($currentPosition);",
                null,
            )
        } else if (
            contentState.capabilityLevel == LyricCapabilityLevel.NCM_YRC &&
            rawWordLyric.isNotBlank()
        ) {
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

    LaunchedEffect(engineReady, metadata, amllArtworkUri, dynamicCoverUrl) {
        if (!engineReady) return@LaunchedEffect
        val mediaId = metadata?.id.orEmpty()
        val details = Json.encodeToString(
            metadata.toAmllSongDetails().copy(artworkUri = amllArtworkUri),
        )
        val dynamicCoverCommand = dynamicCoverUrl
            ?.takeIf(String::isNotBlank)
            ?.let {
                "AmllPage.setDynamicCover(${JSONObject.quote(it)}, ${JSONObject.quote(mediaId)});"
            }
            ?: "AmllPage.clearDynamicCover(${JSONObject.quote(mediaId)});"
        webView.evaluateJavascript(
            "if (globalThis.AmllPage) { " +
                "AmllPage.setSongDetails(${JSONObject.quote(details)}); " +
                dynamicCoverCommand +
                " }",
            null,
        )
    }

    LaunchedEffect(engineReady, showSongWikiDetails) {
        if (!engineReady) return@LaunchedEffect
        webView.evaluateJavascript(
            "if (globalThis.AmllPage) {" +
                " AmllPage.setDynamicBackgroundSuppressed($showSongWikiDetails);" +
                " }",
            null,
        )
    }

    LaunchedEffect(metadata?.id) {
        showSongWikiDetails = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        key(rendererGeneration) {
            AndroidView(
                factory = { webView },
                update = { view ->
                    val showNativeLyrics = lyricUiState is LyricUiState.Content
                    view.visibility = if (showNativeLyrics) View.VISIBLE else View.INVISIBLE
                    view.importantForAccessibility = if (showNativeLyrics) {
                        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    } else {
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f)) {
                    Icon(
                        AppIcons.KeyboardArrowDown,
                        contentDescription = "收起播放页",
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            SongWikiDetailsButton(
                enabled = metadata != null,
                onClick = {
                    showSongWikiDetails = true
                    viewModel.getSongWikiSummary()
                },
                tint = Color.White,
            )
        }

        LyricStateOverlay(
            state = lyricUiState,
            hasMedia = metadata != null,
            isPlayerRestoring = playerStatusRestoreState is PlayerStatusRestoreState.Loading,
            onRetry = viewModel::retryLyrics,
        )

        AutoHideMiniPlayerController(
            modifier = Modifier.fillMaxSize(),
        )
    }

    SongWikiDetailsSheet(
        visible = showSongWikiDetails,
        songTitle = metadata?.title,
        songArtist = metadata?.artist,
        albumTitle = metadata?.albumTitle,
        artworkUri = metadata?.artworkUri,
        durationMs = metadata?.duration,
        artworkOverlay = dynamicCoverUrl
            ?.takeIf(String::isNotBlank)
            ?.let { videoUrl ->
                { AndroidDynamicCoverArtwork(videoUrl) }
            },
        state = songWikiUiState,
        onDismiss = { showSongWikiDetails = false },
        onRetry = viewModel::getSongWikiSummary,
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.AndroidDynamicCoverArtwork(videoUrl: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val textureView = remember(context) { TextureView(context) }
    var firstFrameRendered by remember(videoUrl) { mutableStateOf(false) }
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            trackSelectionParameters = trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            setMediaItem(MediaItem.fromUri(videoUrl))
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }

            override fun onPlayerError(error: PlaybackException) {
                firstFrameRendered = false
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.playWhenReady = true
                Lifecycle.Event.ON_STOP -> player.playWhenReady = false
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        player.setVideoTextureView(textureView)
        player.playWhenReady = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        player.prepare()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.clearVideoTextureView(textureView)
            player.release()
        }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrameRendered) 1f else 0f,
        animationSpec = tween(ExpressiveMotion.StandardMillis),
        label = "song-details-dynamic-cover",
    )
    AndroidView(
        factory = { textureView },
        modifier = Modifier.matchParentSize().alpha(videoAlpha),
    )
    if (firstFrameRendered) {
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            shape = CircleShape,
            color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = "动态封面",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
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

private fun localArtworkDataUri(
    context: android.content.Context,
    songId: Long?,
    uriText: String,
): String? = try {
    localArtworkDataUriOrNull(context, songId, uriText)
} catch (_: Exception) {
    null
}

private fun localArtworkDataUriOrNull(
    context: android.content.Context,
    songId: Long?,
    uriText: String,
): String? {
    val id = songId?.takeIf { it > 0L } ?: return null
    val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return null
    val fileName = when (uri.scheme?.lowercase()) {
        "content" -> {
            if (
                uri.authority != MediaStore.AUTHORITY ||
                uri.pathSegments.getOrNull(1) != "downloads"
            ) return null
            context.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val pathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                if (
                    nameIndex >= 0 &&
                    pathIndex >= 0 &&
                    cursor.moveToFirst() &&
                    cursor.getString(pathIndex) == DOWNLOAD_RELATIVE_PATH
                ) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        }
        "file" -> {
            val file = uri.path?.let(::File)?.canonicalFile ?: return null
            val expectedDirectory = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS + "/$DOWNLOAD_SUBDIR",
            ).canonicalFile
            if (file.parentFile != expectedDirectory || !file.isFile) return null
            file.name
        }
        else -> null
    } ?: return null
    if (!isLocalArtworkSidecarFileName(id, fileName)) return null
    val mimeType = webArtworkMimeType(fileName) ?: return null
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_AMLL_ARTWORK_BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_AMLL_ARTWORK_BYTES) return null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }?.takeIf { it.isNotEmpty() } ?: return null
    return "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
}

private fun webArtworkMimeType(fileName: String): String? =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "avif" -> "image/avif"
        // HEIC/HEIF support is not consistent across Android System WebView versions.
        else -> null
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
            "${LyricParser.formatLrcTimestamp(time ?: 0L)}$line"
        }
        .joinToString("\n")

// Keep the WebView bridge far below the durable 16 MiB download cap: Base64 plus JSON/JS
// escaping creates several in-memory copies. Larger local covers fall back to the remote URI.
private const val MAX_AMLL_ARTWORK_BYTES = 4 * 1024 * 1024
private const val DEFAULT_AMLL_ARTWORK_BUFFER_BYTES = 16 * 1024
