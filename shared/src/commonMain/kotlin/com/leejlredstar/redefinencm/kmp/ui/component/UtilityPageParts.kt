package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import kotlin.math.roundToInt

/** Reflow two related pieces without replacing their layout nodes (in particular, editor focus targets). */
@Composable
internal fun UtilityReflow(
    besideAt: Dp,
    leadingFraction: Float,
    modifier: Modifier = Modifier,
    gap: Dp = 24.dp,
    leading: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box { leading() }
            Box { detail() }
        },
    ) { (leadingSlot, detailSlot), constraints ->
        val width = constraints.maxWidth
        val space = gap.roundToPx()
        val beside = width >= besideAt.roundToPx()
        val leadingWidth = if (beside) ((width - space) * leadingFraction).roundToInt() else width
        val detailWidth = if (beside) width - space - leadingWidth else width
        val leadingPlaceable = leadingSlot.measure(constraints.copy(minWidth = leadingWidth, maxWidth = leadingWidth, minHeight = 0))
        val detailPlaceable = detailSlot.measure(constraints.copy(minWidth = detailWidth, maxWidth = detailWidth, minHeight = 0))
        val height = if (beside) maxOf(leadingPlaceable.height, detailPlaceable.height)
        else leadingPlaceable.height + space + detailPlaceable.height
        layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            leadingPlaceable.placeRelative(0, 0)
            detailPlaceable.placeRelative(
                x = if (beside) leadingWidth + space else 0,
                y = if (beside) 0 else leadingPlaceable.height + space,
            )
        }
    }
}

/** Inline outcomes stay near the action; errors are identified by text, an icon, and a tonal role. */
@Composable
internal fun UtilityFeedback(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    icon: ImageVector = if (isError) AppIcons.ErrorOutline else AppIcons.Info,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = if (isError) scheme.errorContainer else scheme.surfaceContainerHighest,
        contentColor = if (isError) scheme.onErrorContainer else scheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
