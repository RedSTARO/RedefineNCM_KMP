package com.leejlredstar.redefinencm.kmp.ui.component

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import android.view.TextureView
import androidx.annotation.OptIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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

@OptIn(UnstableApi::class)
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

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val visualSpec = remember(showBadge) {
        nativeDynamicCoverVisualSpec(
            showBadge = showBadge,
            androidPresentation = true,
        )
    }
    val textureLayerPaint = remember(visualSpec.saturation) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply {
                    setSaturation(visualSpec.saturation)
                },
            )
        }
    }
    val textureView = remember(context, videoUrl) {
        TextureView(context).apply {
            // TextureView is opaque by default, which exposes a black surface while
            // ExoPlayer swaps or resizes buffers. CSS video stays transparent until
            // its first decoded frame, so make the native surface follow that rule.
            isOpaque = false
            setBackgroundColor(AndroidColor.TRANSPARENT)
            alpha = 0f
        }
    }
    var firstFrameRendered by remember(videoUrl) { mutableStateOf(false) }
    val shouldPlay by rememberUpdatedState(play)
    val player = remember(context, videoUrl) {
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

    DisposableEffect(player, textureView, lifecycleOwner) {
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
                Lifecycle.Event.ON_START -> player.playWhenReady = shouldPlay
                Lifecycle.Event.ON_STOP -> player.playWhenReady = false
                else -> Unit
            }
        }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        player.setVideoTextureView(textureView)
        player.playWhenReady =
            shouldPlay && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        player.prepare()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.clearVideoTextureView(textureView)
            player.release()
        }
    }
    LaunchedEffect(player, play, lifecycleOwner) {
        player.playWhenReady =
            play && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    DisposableEffect(textureView, textureLayerPaint) {
        // Hardware-layer paint is the native equivalent of `filter: saturate(...)`.
        textureView.setLayerPaint(textureLayerPaint)
        onDispose {
            textureView.setLayerPaint(null)
        }
    }

    val visible = nativeDynamicCoverIsVisible(
        hasPresentedFrame = firstFrameRendered,
        play = play,
        showBadge = showBadge,
    )
    val videoAlpha by animateFloatAsState(
        // `setDynamicBackgroundSuppressed(true)` in player.html pauses `#dynamic-bg`
        // without removing its `.visible` class. Keep the decoded frame visible while
        // playback is paused (for example while the song-wiki dialog is open).
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
        label = "native-dynamic-cover",
    )
    val latestOnVisibilityChanged by rememberUpdatedState(onVisibilityChanged)
    LaunchedEffect(visible) {
        latestOnVisibilityChanged(visible)
    }
    DisposableEffect(Unit) {
        onDispose { latestOnVisibilityChanged(false) }
    }
    Box(modifier = modifier) {
        key(textureView) {
            AndroidView(
                factory = { textureView },
                update = { view ->
                    // TextureView natively supports alpha; applying opacity on the video
                    // view itself matches CSS and avoids an extra Compose offscreen layer.
                    view.alpha = videoAlpha
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showBadge && firstFrameRendered && play) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
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
    }
}
