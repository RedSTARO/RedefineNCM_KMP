package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.Frame
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.math.max
import kotlin.math.min

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

    val visualSpec = remember(showBadge) {
        nativeDynamicCoverVisualSpec(
            showBadge = showBadge,
            androidPresentation = false,
        )
    }
    val lifecycleActive by DesktopDynamicCoverWindowLifecycle.lifecycleActive.collectAsState()
    val decoderShouldPlay = nativeDynamicCoverShouldPlay(
        requestedPlay = play,
        lifecycleActive = lifecycleActive,
    )
    val decoder = remember(videoUrl) {
        JvmDynamicCoverDecoder(
            url = videoUrl,
            initiallyPlaying = decoderShouldPlay,
        )
    }
    val frame by decoder.frames.collectAsState()

    LaunchedEffect(decoder, decoderShouldPlay) {
        decoder.setPlaying(decoderShouldPlay)
    }
    DisposableEffect(decoder) {
        onDispose {
            decoder.release()
        }
    }

    val visible = nativeDynamicCoverIsVisible(
        hasPresentedFrame = frame != null,
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
        label = "jvm-native-dynamic-cover",
    )
    val latestOnVisibilityChanged by rememberUpdatedState(onVisibilityChanged)
    LaunchedEffect(visible) {
        latestOnVisibilityChanged(visible)
    }
    DisposableEffect(Unit) {
        onDispose { latestOnVisibilityChanged(false) }
    }

    Box(modifier = modifier) {
        frame?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix().apply {
                        setToSaturation(visualSpec.saturation)
                    },
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = videoAlpha
                    },
            )
        }
        if (showBadge && frame != null && play) {
            DynamicCoverBadge(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }
}

/**
 * FFmpeg runs off the Compose thread and publishes immutable Skia-backed frame copies.
 * A single decoder stays alive while [setPlaying] is false: no frame is cleared or
 * advanced, which preserves the exact last picture just like HTMLVideoElement.pause().
 */
private class JvmDynamicCoverDecoder(
    private val url: String,
    initiallyPlaying: Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _frames = MutableStateFlow<androidx.compose.ui.graphics.ImageBitmap?>(null)
    val frames = _frames.asStateFlow()

    @Volatile
    private var playing = initiallyPlaying

    @Volatile
    private var released = false

    private val decodeJob: Job = scope.launch {
        decodeOnce()
    }

    fun setPlaying(value: Boolean) {
        if (!released) playing = value
    }

    fun release() {
        if (released) return
        released = true
        playing = false
        decodeJob.cancel()
        scope.cancel()
    }

    private suspend fun decodeOnce() {
        val grabber = FFmpegFrameGrabber(url).apply {
            // Bound source replacement if the CDN stalls, but do not reconnect:
            // player.html's video.onerror clears this source for the rest of its
            // lifecycle and falls back to the static artwork.
            setOption("rw_timeout", NETWORK_TIMEOUT_MICROS.toString())
        }
        val converter = Java2DFrameConverter()
        try {
            grabber.start()
            decodeLoop(grabber, converter)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            _frames.value = null
        } finally {
            runCatching { grabber.stop() }
            runCatching { grabber.release() }
            converter.close()
        }
    }

    private suspend fun decodeLoop(
        grabber: FFmpegFrameGrabber,
        converter: Java2DFrameConverter,
    ) {
        var previousTimestampMicros = -1L
        while (currentCoroutineContext().isActive && !released) {
            awaitPlayback()

            val nativeFrame = grabber.grabImage()
            if (nativeFrame == null) {
                // Dynamic covers are finite MP4s with loop=true in player.html.
                // Seeking the same native decoder retains buffers and avoids a
                // black surface between the last and first frame.
                val rewound = runCatching {
                    grabber.setTimestamp(0L)
                    true
                }.getOrDefault(false)
                if (!rewound) return
                previousTimestampMicros = -1L
                continue
            }

            val currentTimestampMicros = grabber.timestamp
            if (previousTimestampMicros >= 0L) {
                val fallbackFrameMicros =
                    (MICROS_PER_SECOND / grabber.frameRate.takeIf { it > 0.0 }
                        .orDefault(DEFAULT_FRAME_RATE)).toLong()
                val frameDeltaMicros =
                    (currentTimestampMicros - previousTimestampMicros)
                        .takeIf { it > 0L }
                        ?: fallbackFrameMicros
                delayWhilePlaying(
                    frameDeltaMicros.coerceIn(
                        MIN_FRAME_DELAY_MICROS,
                        MAX_FRAME_DELAY_MICROS,
                    ),
                )
            }
            awaitPlayback()

            val bufferedImage = converter.convert(nativeFrame) ?: continue
            // toComposeImageBitmap copies BufferedImage pixels, so FFmpeg may safely
            // reuse its native Frame storage on the next grabImage() call.
            _frames.value = bufferedImage.toComposeImageBitmap()
            previousTimestampMicros = currentTimestampMicros
        }
    }

    private suspend fun awaitPlayback() {
        while (currentCoroutineContext().isActive && !released && !playing) {
            delay(PAUSE_POLL_MILLIS)
        }
    }

    /**
     * Count only time spent in the playing state. If the wiki opens halfway
     * through a frame interval, the remaining interval resumes after close.
     */
    private suspend fun delayWhilePlaying(durationMicros: Long) {
        var remainingNanos = durationMicros * NANOS_PER_MICRO
        var lastTick = System.nanoTime()
        while (
            currentCoroutineContext().isActive &&
            !released &&
            remainingNanos > 0L
        ) {
            if (!playing) {
                awaitPlayback()
                lastTick = System.nanoTime()
                continue
            }
            val sliceMillis = max(
                1L,
                min(
                    FRAME_DELAY_SLICE_MILLIS,
                    remainingNanos / NANOS_PER_MILLI,
                ),
            )
            delay(sliceMillis)
            val now = System.nanoTime()
            if (playing) {
                remainingNanos -= (now - lastTick).coerceAtLeast(0L)
            }
            lastTick = now
        }
    }

    private companion object {
        const val NETWORK_TIMEOUT_MICROS = 5_000_000L
        const val PAUSE_POLL_MILLIS = 16L
        const val FRAME_DELAY_SLICE_MILLIS = 16L
        const val MICROS_PER_SECOND = 1_000_000.0
        const val NANOS_PER_MICRO = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val DEFAULT_FRAME_RATE = 30.0
        const val MIN_FRAME_DELAY_MICROS = 1_000L
        const val MAX_FRAME_DELAY_MICROS = 250_000L
    }
}

private fun Double?.orDefault(defaultValue: Double): Double = this ?: defaultValue

/**
 * The desktop launcher owns the public Compose [androidx.compose.ui.window.WindowScope.window]
 * reference. Binding it here avoids relying on Compose Desktop's internal LocalWindow while
 * still letting the shared JVM leaf suspend native decoding when that exact window is hidden
 * or iconified.
 */
object DesktopDynamicCoverWindowLifecycle {
    private val _lifecycleActive = MutableStateFlow(false)
    internal val lifecycleActive = _lifecycleActive.asStateFlow()
    private var binding: WindowLifecycleBinding? = null

    @Synchronized
    fun bind(window: Window): AutoCloseable {
        binding?.dispose()
        val nextBinding = WindowLifecycleBinding(
            window = window,
            onLifecycleChanged = { active -> _lifecycleActive.value = active },
        )
        binding = nextBinding
        nextBinding.install()
        return AutoCloseable {
            synchronized(this) {
                if (binding === nextBinding) {
                    nextBinding.dispose()
                    binding = null
                    _lifecycleActive.value = false
                }
            }
        }
    }
}

private class WindowLifecycleBinding(
    private val window: Window,
    private val onLifecycleChanged: (Boolean) -> Unit,
) {
    private val windowListener = object : WindowAdapter() {
        override fun windowOpened(event: WindowEvent) = publishCurrentState()

        override fun windowIconified(event: WindowEvent) {
            onLifecycleChanged(false)
        }

        override fun windowDeiconified(event: WindowEvent) = publishCurrentState()

        override fun windowStateChanged(event: WindowEvent) = publishCurrentState()

        override fun windowClosing(event: WindowEvent) {
            onLifecycleChanged(false)
        }

        override fun windowClosed(event: WindowEvent) {
            onLifecycleChanged(false)
        }
    }
    private val componentListener = object : ComponentAdapter() {
        override fun componentShown(event: ComponentEvent) = publishCurrentState()

        override fun componentHidden(event: ComponentEvent) {
            onLifecycleChanged(false)
        }
    }

    fun install() {
        window.addWindowListener(windowListener)
        window.addWindowStateListener(windowListener)
        window.addComponentListener(componentListener)
        publishCurrentState()
    }

    fun dispose() {
        window.removeComponentListener(componentListener)
        window.removeWindowStateListener(windowListener)
        window.removeWindowListener(windowListener)
    }

    private fun publishCurrentState() {
        onLifecycleChanged(window.isDynamicCoverLifecycleActive())
    }
}

private fun Window.isDynamicCoverLifecycleActive(): Boolean {
    val extendedState = (this as? Frame)?.extendedState ?: Frame.NORMAL
    return desktopDynamicCoverLifecycleActive(
        isVisible = isVisible,
        extendedState = extendedState,
    )
}

internal fun desktopDynamicCoverLifecycleActive(
    isVisible: Boolean,
    extendedState: Int,
): Boolean = isVisible && extendedState.and(Frame.ICONIFIED) == 0

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
