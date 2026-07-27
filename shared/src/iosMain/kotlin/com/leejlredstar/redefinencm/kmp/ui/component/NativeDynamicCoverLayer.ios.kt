@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVMutableVideoComposition
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.videoComposition
import platform.AVFoundation.videoCompositionWithAsset
import platform.AVFoundation.volume
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.kCIInputSaturationKey
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObjectProtocol

@Composable
internal actual fun NativeDynamicCoverLayer(
    url: String,
    modifier: Modifier,
    play: Boolean,
    showBadge: Boolean,
    reducedMotion: Boolean,
    onVisibilityChanged: (Boolean) -> Unit,
) {
    val videoUrl = url.trim()
    if (videoUrl.isEmpty()) return
    val sourceUrl = remember(videoUrl) { NSURL.URLWithString(videoUrl) } ?: return

    val visualSpec = remember(showBadge) {
        nativeDynamicCoverVisualSpec(
            showBadge = showBadge,
            androidPresentation = false,
        )
    }
    val videoView = remember(sourceUrl, visualSpec.saturation) {
        IosDynamicCoverView(
            sourceUrl = sourceUrl,
            saturation = visualSpec.saturation,
        )
    }
    var firstFrameRendered by remember(videoView) { mutableStateOf(false) }

    LaunchedEffect(videoView) {
        // AVPlayerItemStatusReadyToPlay can precede the first decoded picture. The
        // AVPlayerLayer KVO-compatible readyForDisplay flag is the native equivalent
        // of HTMLVideoElement's loadeddata/first-presentable-frame boundary.
        while (isActive && !firstFrameRendered) {
            if (videoView.isFirstFrameReady) {
                firstFrameRendered = true
            } else {
                delay(16L)
            }
        }
    }
    LaunchedEffect(videoView, play) {
        videoView.setPlaying(play)
    }

    val visible = nativeDynamicCoverIsVisible(
        hasPresentedFrame = firstFrameRendered,
        play = play,
        showBadge = showBadge,
    )
    val videoAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(
                durationMillis = visualSpec.fadeDurationMillis,
                easing = CubicBezierEasing(
                    visualSpec.easingX1,
                    visualSpec.easingY1,
                    visualSpec.easingX2,
                    visualSpec.easingY2,
                ),
            )
        },
        label = "ios-native-dynamic-cover",
    )
    val latestOnVisibilityChanged by rememberUpdatedState(onVisibilityChanged)
    LaunchedEffect(visible) {
        latestOnVisibilityChanged(visible)
    }
    DisposableEffect(Unit) {
        onDispose { latestOnVisibilityChanged(false) }
    }

    Box(modifier = modifier) {
        key(videoView) {
            UIKitView(
                factory = { videoView },
                update = { view ->
                    view.setPlaying(play)
                    view.setVideoOpacity(videoAlpha)
                },
                onRelease = { view -> view.release() },
                properties = UIKitInteropProperties(
                    isInteractive = false,
                    isNativeAccessibilityEnabled = false,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showBadge && firstFrameRendered && play) {
            DynamicCoverBadge(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

/**
 * UIKitView is inserted in Compose's background interop container. Compose clears
 * only this node's pixels and then draws later siblings (scrims, lyrics, controls)
 * above it, so AVPlayerLayer never becomes a full-window native overlay.
 */
private class IosDynamicCoverView(
    sourceUrl: NSURL,
    saturation: Float,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val asset = AVURLAsset(uRL = sourceUrl, options = null)
    private val playerItem = AVPlayerItem(asset = asset).apply {
        if (saturation != 1f) {
            videoComposition = AVMutableVideoComposition.videoCompositionWithAsset(
                asset = asset,
                applyingCIFiltersWithHandler = { request ->
                    request ?: return@videoCompositionWithAsset
                    /*
                     * AVFoundation's forward declaration and CoreImage's full declaration are
                     * emitted as two Kotlin types by the Native commonizer. Both wrap the same
                     * Objective-C CIImage class, so these bridge casts do not change the object.
                     */
                    @Suppress("CAST_NEVER_SUCCEEDS")
                    val source = request.sourceImage as platform.CoreImage.CIImage
                    val filtered = source.imageByApplyingFilter(
                        filterName = "CIColorControls",
                        withInputParameters = mapOf(
                            kCIInputSaturationKey to saturation,
                        ),
                    )
                    @Suppress("CAST_NEVER_SUCCEEDS")
                    request.finishWithImage(
                        filteredImage = filtered as objcnames.classes.CIImage,
                        context = null,
                    )
                },
            )
        }
    }
    private val player = AVPlayer(playerItem = playerItem).apply {
        volume = 0f
    }
    private val playerLayer = AVPlayerLayer().apply {
        this.player = this@IosDynamicCoverView.player
        videoGravity = AVLayerVideoGravityResizeAspectFill
        opacity = 0f
    }
    private var requestedPlay = false
    private var lifecycleActive =
        UIApplication.sharedApplication.applicationState !=
            UIApplicationState.UIApplicationStateBackground
    private var released = false
    private var endObserver: NSObjectProtocol? = null
    private var backgroundObserver: NSObjectProtocol? = null
    private var foregroundObserver: NSObjectProtocol? = null

    val isFirstFrameReady: Boolean
        get() = !released && playerLayer.readyForDisplay

    init {
        opaque = false
        backgroundColor = UIColor.clearColor
        userInteractionEnabled = false
        clipsToBounds = true
        layer.addSublayer(playerLayer)
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = playerItem,
            queue = NSOperationQueue.mainQueue,
        ) {
            if (!released) {
                player.seekToTime(CMTimeMake(0L, 1))
                applyPlaybackState()
            }
        }
        backgroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            setLifecycleActive(false)
        }
        foregroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            setLifecycleActive(true)
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.frame = bounds
    }

    fun setPlaying(value: Boolean) {
        if (released) return
        requestedPlay = value
        applyPlaybackState()
    }

    fun setVideoOpacity(value: Float) {
        if (!released) playerLayer.opacity = value.coerceIn(0f, 1f)
    }

    private fun setLifecycleActive(value: Boolean) {
        if (released || lifecycleActive == value) return
        lifecycleActive = value
        applyPlaybackState()
    }

    private fun applyPlaybackState() {
        if (released) return
        if (
            nativeDynamicCoverShouldPlay(
                requestedPlay = requestedPlay,
                lifecycleActive = lifecycleActive,
            )
        ) {
            player.play()
        } else {
            player.pause()
        }
    }

    fun release() {
        if (released) return
        released = true
        requestedPlay = false
        lifecycleActive = false
        player.pause()
        endObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        backgroundObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        foregroundObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        endObserver = null
        backgroundObserver = null
        foregroundObserver = null
        player.replaceCurrentItemWithPlayerItem(null)
        playerLayer.player = null
        playerLayer.removeFromSuperlayer()
    }
}

@Composable
private fun DynamicCoverBadge(
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color(0xFF005144).copy(alpha = 0.92f),
        contentColor = Color(0xFF9CF2DC),
    ) {
        Text(
            text = "动态封面",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.02.em,
                lineHeight = TextUnit.Unspecified,
            ),
        )
    }
}
