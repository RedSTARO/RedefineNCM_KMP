package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons

/**
 * Shared image surface with a tonal loading/error fallback.
 *
 * Palette extraction stays tied to this visible request through [onImageLoaded]; callers do not
 * need a second hidden image request.
 *
 * Passing [pressInteractionSource] — the same source given to the enclosing clickable — opts the
 * artwork into the expressive shape morph: the frame blooms from a soft squircle into a
 * scalloped cookie while held, then springs back on release. It is off by default so callers
 * that are not interactive keep a plain rectangle and pay nothing for the morph path.
 */
@Composable
fun ExpressiveArtwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: ImageVector = AppIcons.GraphicEq,
    pressInteractionSource: InteractionSource? = null,
    morphPair: ExpressiveMorphPair = ExpressiveMorphPair.ArtworkBloom,
    onImageLoaded: (Image) -> Unit = {},
) {
    val morphProgress = pressInteractionSource?.let { rememberPressMorphProgress(it) }
    val resolvedShape = if (morphProgress != null) {
        rememberMorphShape(morphPair, morphProgress.value)
    } else {
        shape
    }
    Surface(
        modifier = modifier,
        shape = resolvedShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state -> onImageLoaded(state.result.image) },
            )
        }
    }
}
