@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import com.leejlredstar.redefinencm.kmp.ui.component.AutoHideMiniPlayerController
import com.leejlredstar.redefinencm.kmp.ui.component.NativeDynamicCoverLayer
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsButton
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsSheet
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.util.LocalMediaAssetStorage
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIColor
import platform.WebKit.WKAudiovisualMediaTypeNone
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * Legacy AMLL host for iOS, backed by `WKWebView`.
 *
 * Drives the same `amllAssets/amll/player.html` bundle and the same `AmllBridge.*` calls as the
 * Android System WebView and the Desktop WebView2 hosts. The page calls back through
 * `globalThis.AmllCallback`, which does not exist on WKWebView, so [AMLL_CALLBACK_SHIM] installs
 * one at document start that forwards to a `WKScriptMessageHandler`.
 */
@Composable
actual fun WebViewLyricScreen(onBack: () -> Unit) {
    val viewModel: NowPlayingViewModel = koinInject()
    val rawLyric by viewModel.rawLyric.collectAsState()
    val rawWordLyric by viewModel.rawWordLyric.collectAsState()
    val rawTtmlLyric by viewModel.rawTtmlLyric.collectAsState()
    val rawTranslatedLyric by viewModel.rawTranslatedLyric.collectAsState()
    val rawRomanLyric by viewModel.rawRomanLyric.collectAsState()
    val lyricMap by viewModel.lyricMap.collectAsState()
    val untimedLyricLines by viewModel.untimedLyricLines.collectAsState()
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

    var engineReady by remember { mutableStateOf(false) }
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
        // WKWebView loads player.html from the app bundle, so a file: URI pointing into the
        // Documents container is outside its read scope. Inline the artwork instead.
        val dataUri = withContext(Dispatchers.Default) {
            media.id.toLongOrNull()?.let { songId ->
                LocalMediaAssetStorage.resolveArtworkUri(songId)?.let(::artworkDataUri)
            }
        }
        if (metadata?.id == media.id && localArtworkActive) {
            localAmllArtwork = media.id to (dataUri ?: remoteArtworkUri)
        }
    }

    val lyricForWeb = remember(rawLyric, lyricMap, lyricUiState) {
        if (lyricUiState is LyricUiState.Content) {
            rawLyric.takeIf { it.isNotBlank() } ?: lyricMap.toLrcFallbackText()
        } else {
            ""
        }
    }
    val untimedLyricsForWeb = remember(untimedLyricLines) {
        Json.encodeToString(untimedLyricLines)
    }
    val isUntimedContent =
        (lyricUiState as? LyricUiState.Content)?.capabilityLevel == LyricCapabilityLevel.UNSYNCED

    val currentOnLineClicked = rememberUpdatedState<(Long, String?) -> Unit> { timeMs, mediaId ->
        viewModel.onLyricLineClick(mediaId, timeMs)
    }
    val currentOnReady = rememberUpdatedState { engineReady = true }

    val messageHandler = remember {
        AmllScriptMessageHandler(
            onReady = { currentOnReady.value.invoke() },
            onLineClicked = { timeMs, mediaId ->
                currentOnLineClicked.value.invoke(timeMs, mediaId)
            },
        )
    }

    val webView = remember(messageHandler) { createAmllWebView(messageHandler) }

    DisposableEffect(webView) {
        onDispose {
            engineReady = false
            webView.stopLoading()
            webView.configuration.userContentController
                .removeScriptMessageHandlerForName(AMLL_CALLBACK_NAME)
            webView.loadHTMLString("", baseURL = null)
        }
    }

    LaunchedEffect(engineReady, lyricMediaId) {
        if (!engineReady) return@LaunchedEffect
        val mediaId = lyricMediaId ?: return@LaunchedEffect
        val position = currentPosition.coerceAtLeast(0L)
        webView.runJs(
            "AmllBridge.resetTrack(${AmllWebBridge.quote(mediaId)}); AmllBridge.setTime($position);",
        )
    }

    LaunchedEffect(
        engineReady,
        lyricMediaId,
        rawTtmlLyric,
        rawWordLyric,
        lyricForWeb,
        untimedLyricsForWeb,
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
            webView.runJs("AmllBridge.loadLyrics('');")
            when (val state = lyricUiState) {
                is LyricUiState.Idle -> webView.showAmllStatus(
                    when {
                        metadata != null -> "正在恢复歌词…"
                        playerStatusRestoreState is PlayerStatusRestoreState.Loading -> "正在恢复播放…"
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
        val quotedMediaId = AmllWebBridge.quote(mediaId)
        val lyricOptions = AmllWebBridge.lyricOptionsJson(
            translatedLyric = rawTranslatedLyric,
            romanLyric = rawRomanLyric,
            showTranslatedLyric = showTranslatedLyric,
            showRomanLyric = showRomanLyric,
        )
        when {
            contentState.capabilityLevel == LyricCapabilityLevel.UNSYNCED -> webView.runJs(
                "AmllBridge.loadUntimedLyrics($untimedLyricsForWeb, $quotedMediaId); " +
                    "AmllBridge.setTime(0);",
            )
            rawTtmlLyric.isNotBlank() -> webView.runJs(
                "AmllBridge.loadTtmlLyrics(${AmllWebBridge.quote(rawTtmlLyric)}, $quotedMediaId, " +
                    "$lyricOptions, ${AmllWebBridge.quote(lyricForWeb)}); " +
                    "AmllBridge.setTime($currentPosition);",
            )
            contentState.capabilityLevel == LyricCapabilityLevel.NCM_YRC &&
                rawWordLyric.isNotBlank() -> webView.runJs(
                "AmllBridge.loadWordLyrics(${AmllWebBridge.quote(rawWordLyric)}, $quotedMediaId, " +
                    "$lyricOptions); AmllBridge.setTime($currentPosition);",
            )
            else -> webView.runJs(
                "AmllBridge.loadLyrics(${AmllWebBridge.quote(lyricForWeb)}, $quotedMediaId, " +
                    "$lyricOptions); AmllBridge.setTime($currentPosition);",
            )
        }
    }

    LaunchedEffect(engineReady, currentPosition, isUntimedContent) {
        if (!engineReady) return@LaunchedEffect
        val position = if (isUntimedContent) 0L else currentPosition
        webView.runJs("AmllBridge.setTime($position);")
    }

    LaunchedEffect(engineReady, metadata, amllArtworkUri, dynamicCoverUrl) {
        if (!engineReady) return@LaunchedEffect
        val media = metadata ?: return@LaunchedEffect
        val quotedMediaId = AmllWebBridge.quote(media.id)
        val details = Json.encodeToString(
            media.toAmllSongDetails().copy(artworkUri = amllArtworkUri),
        )
        val dynamicCoverCommand = dynamicCoverUrl
            ?.takeIf(String::isNotBlank)
            ?.let { "AmllPage.setDynamicCover(${AmllWebBridge.quote(it)}, $quotedMediaId);" }
            ?: "AmllPage.clearDynamicCover($quotedMediaId);"
        webView.runJs(
            "if (globalThis.AmllPage) { " +
                "AmllPage.setSongDetails(${AmllWebBridge.quote(details)}); " +
                dynamicCoverCommand +
                " }",
        )
    }

    LaunchedEffect(engineReady, showSongWikiDetails) {
        if (!engineReady) return@LaunchedEffect
        webView.runJs(
            "if (globalThis.AmllPage) { " +
                "AmllPage.setDynamicBackgroundSuppressed($showSongWikiDetails); }",
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
        UIKitView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
            properties = UIKitInteropProperties(isInteractive = true, isNativeAccessibilityEnabled = true),
        )

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

        AutoHideMiniPlayerController(modifier = Modifier.fillMaxSize())
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
            ?.let { videoUrl -> { NativeDynamicCoverLayer(videoUrl) } },
        state = songWikiUiState,
        onDismiss = { showSongWikiDetails = false },
        onRetry = viewModel::getSongWikiSummary,
    )
}

private const val AMLL_CALLBACK_NAME = "AmllCallbackNative"

/**
 * The AMLL page calls `globalThis.AmllCallback.*` (an Android `@JavascriptInterface` shape).
 * WKWebView only exposes `window.webkit.messageHandlers`, so install a matching façade.
 */
private val AMLL_CALLBACK_SHIM = """
    globalThis.AmllCallback = {
      onReady: function () {
        window.webkit.messageHandlers.$AMLL_CALLBACK_NAME.postMessage({ method: 'onReady' });
      },
      onLyricLineClicked: function (timeMs, mediaId) {
        window.webkit.messageHandlers.$AMLL_CALLBACK_NAME.postMessage({
          method: 'onLyricLineClicked',
          timeMs: timeMs,
          mediaId: mediaId == null ? null : String(mediaId)
        });
      },
      onSongWikiRequested: function (mediaId) {
        window.webkit.messageHandlers.$AMLL_CALLBACK_NAME.postMessage({
          method: 'onSongWikiRequested',
          mediaId: mediaId == null ? null : String(mediaId)
        });
      }
    };
""".trimIndent()

/**
 * WKScriptMessageHandler must be a real Obj-C object. It is a class, never a Kotlin `object`:
 * Kotlin/Native cannot lower an Obj-C-backed singleton and aborts codegen on one.
 */
private class AmllScriptMessageHandler(
    private val onReady: () -> Unit,
    private val onLineClicked: (timeMs: Long, mediaId: String?) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        val body = didReceiveScriptMessage.body as? Map<*, *> ?: return
        when (body["method"] as? String) {
            "onReady" -> onReady()
            "onLyricLineClicked" -> {
                val timeMs = (body["timeMs"] as? Number)?.toLong() ?: return
                onLineClicked(timeMs, body["mediaId"] as? String)
            }
            else -> Unit
        }
    }
}

private fun createAmllWebView(handler: AmllScriptMessageHandler): WKWebView {
    val controller = WKUserContentController().apply {
        addScriptMessageHandler(handler, AMLL_CALLBACK_NAME)
        addUserScript(
            WKUserScript(
                source = AMLL_CALLBACK_SHIM,
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = true,
            ),
        )
    }
    val configuration = WKWebViewConfiguration().apply {
        userContentController = controller
        allowsInlineMediaPlayback = true
        // The AMLL page animates artwork video without a tap; iOS blocks that by default.
        mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone
    }

    val webView = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration)
    webView.opaque = false
    webView.backgroundColor = UIColor.blackColor
    webView.scrollView.bounces = false
    webView.scrollView.scrollEnabled = false

    val playerUrl = NSBundle.mainBundle.URLForResource(
        name = "player",
        withExtension = "html",
        subdirectory = AMLL_BUNDLE_SUBDIRECTORY,
    )
    if (playerUrl != null) {
        // Read access must cover the whole amll/ directory so bundle.js and style.css resolve.
        val directoryUrl = playerUrl.URLByDeletingLastPathComponent ?: playerUrl
        webView.loadFileURL(playerUrl, allowingReadAccessToURL = directoryUrl)
    } else {
        webView.showAmllError("AMLL 资源缺失：应用包内没有 $AMLL_BUNDLE_SUBDIRECTORY/player.html")
    }
    return webView
}

private const val AMLL_BUNDLE_SUBDIRECTORY = "amll"

private fun WKWebView.runJs(script: String) {
    evaluateJavaScript(script, null)
}

private fun WKWebView.showAmllStatus(message: String) {
    runJs("if (globalThis.AmllPage) { AmllPage.showStatus(${AmllWebBridge.quote(message)}); }")
}

private fun WKWebView.showAmllError(message: String) {
    runJs("if (globalThis.AmllPage) { AmllPage.showError(${AmllWebBridge.quote(message)}); }")
}

/** Mirrors the Android host's 4 MiB inline-artwork ceiling. */
private const val MAX_AMLL_ARTWORK_BYTES = 4 * 1024 * 1024

private fun artworkDataUri(fileUri: String): String? {
    val url = NSURL.URLWithString(fileUri) ?: return null
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    if (data.length.toLong() > MAX_AMLL_ARTWORK_BYTES) return null
    val base64 = data.base64EncodedStringWithOptions(0uL)
    return "data:image/jpeg;base64,$base64"
}
