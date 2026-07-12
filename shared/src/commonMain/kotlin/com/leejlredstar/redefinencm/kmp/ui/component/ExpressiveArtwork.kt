package com.leejlredstar.redefinencm.kmp.ui.component

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
    onImageLoaded: (Image) -> Unit = {},
) {
    Surface(
        modifier = modifier,
        shape = shape,
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
