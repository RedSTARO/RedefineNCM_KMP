package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.Image
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/**
 * A song transition as screens draw it: the two tracks, and how much of the picture belongs to
 * the incoming one.
 *
 * The players report the blend in steps (every 20 ms on Android and the web, every 100 ms on the
 * desktop); [weight] glides between them and eases in and out, so the picture is exactly half and
 * half at the swap, where the title changes. Read it in draw and layer blocks: a blend then
 * redraws without recomposing the screen.
 */
@Stable
class TransitionVisual internal constructor(
    val outgoing: MediaInfo,
    val incoming: MediaInfo,
    private val progress: State<Float>,
) {
    /** The incoming track's share of the picture, 0 to 1. */
    val weight: Float
        get() = progress.value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}

/** The transition [player] is blending, or null while it is not blending. */
@Composable
fun rememberTransitionVisual(player: PlatformPlayer): TransitionVisual? {
    val blend = player.transitionBlend.collectAsState()
    val tracks by remember { derivedStateOf { blend.value?.let { it.outgoing to it.incoming } } }
    val pair = tracks ?: return null
    val progress = remember(pair) { Animatable(blend.value?.progress ?: 0f) }
    LaunchedEffect(pair) {
        snapshotFlow { blend.value?.progress }.filterNotNull().collectLatest { target ->
            progress.animateTo(target, tween(PROGRESS_GLIDE_MILLIS, easing = LinearEasing))
        }
    }
    return remember(pair) { TransitionVisual(pair.first, pair.second, progress.asState()) }
}

/**
 * Covers for a screen that shows a transition: [current] alone, or through a blend the outgoing
 * cover under the incoming one, which dissolves in with [visual]'s weight. With [focusPull] it
 * also settles into focus as it comes, from slightly enlarged and soft to sharp; the blur is
 * lost on Android before 12, where the dissolve remains.
 *
 * Layers are keyed by track, so when the blend ends the incoming cover's layer simply stays,
 * already loaded, and nothing reloads or flashes. [layer] draws one cover: it receives the track,
 * a modifier for the whole layer, one for the image inside the frame, and the callback to call
 * with the loaded image. [onCurrentImage] gets the current track's image, also when it finished
 * loading while the track was still the incoming one; [onIncomingImage] gets the incoming one's.
 */
@Composable
fun TransitionArtworkStack(
    current: MediaInfo?,
    visual: TransitionVisual?,
    modifier: Modifier = Modifier,
    focusPull: Boolean = false,
    onCurrentImage: (Image) -> Unit = {},
    onIncomingImage: (Image) -> Unit = {},
    layer: @Composable BoxScope.(
        media: MediaInfo?,
        layerModifier: Modifier,
        imageModifier: Modifier,
        onLoaded: (Image) -> Unit,
    ) -> Unit,
) {
    val layers = if (visual != null) listOf(visual.outgoing, visual.incoming) else listOf(current)
    val loaded = remember { mutableStateMapOf<String, Image>() }
    val layerIds = layers.mapNotNull { it?.id }
    LaunchedEffect(layerIds) { loaded.keys.retainAll(layerIds.toSet()) }
    val currentImage = current?.id?.let { loaded[it] }
    LaunchedEffect(current?.id, currentImage) { currentImage?.let(onCurrentImage) }
    Box(modifier) {
        layers.forEachIndexed { index, media ->
            key(media?.id) {
                val incoming = visual != null && index == 1
                val layerModifier = if (incoming) {
                    // Nothing shows until the cover has loaded: a half-faded placeholder would
                    // read as a flash of grey.
                    Modifier.graphicsLayer { alpha = if (media?.id in loaded) visual!!.weight else 0f }
                } else {
                    Modifier
                }
                val imageModifier = if (incoming && focusPull) {
                    Modifier.graphicsLayer {
                        val away = 1f - visual!!.weight
                        val scale = 1f + FOCUS_PULL_SCALE * away
                        scaleX = scale
                        scaleY = scale
                        val blur = FOCUS_PULL_BLUR.toPx() * away
                        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Clamp) else null
                    }
                } else {
                    Modifier
                }
                layer(media, layerModifier, imageModifier) { image ->
                    media?.id?.let { loaded[it] = image }
                    if (incoming) onIncomingImage(image)
                }
            }
        }
    }
}

private const val PROGRESS_GLIDE_MILLIS = 110

/** How much bigger, and how soft, the incoming cover starts. */
private const val FOCUS_PULL_SCALE = 0.06f
private val FOCUS_PULL_BLUR: Dp = 14.dp
