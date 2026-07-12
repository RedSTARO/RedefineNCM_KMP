package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette

/** Shared dimensions for the app's Material 3 Expressive layout language. */
object ExpressiveLayout {
    val PageHorizontalPadding = 16.dp
    val PageVerticalPadding = 16.dp
    val SectionSpacing = 24.dp
    val ConnectedItemGap = 1.5.dp
    val ConnectedOuterCorner = 28.dp
    val ConnectedInnerCorner = 6.dp
    val MinimumTouchTarget = 48.dp
    val ReadingContentMaxWidth = 840.dp
    val BrowseContentMaxWidth = 1200.dp
}

/** Shared timing tokens for custom transitions not owned by a Material component. */
object ExpressiveMotion {
    const val FastMillis = 120
    const val QuickMillis = 160
    const val ShortMillis = 180
    const val StandardMillis = 220
    const val MediumMillis = 260
    const val EmphasizedMillis = 280
    const val LongMillis = 320
    const val EnterDelayMillis = 60
    const val StaggerDelayMillis = 90
}

@Immutable
enum class ExpressiveStateTone {
    Neutral,
    Error,
}

/**
 * Compute the connected-list item shape: large outer corners, tight inner corners.
 * This is a core Material 3 Expressive pattern used across all list screens.
 */
fun connectedListItemShape(index: Int, count: Int): RoundedCornerShape {
    val big = ExpressiveLayout.ConnectedOuterCorner
    val small = ExpressiveLayout.ConnectedInnerCorner
    return when {
        count <= 1 -> RoundedCornerShape(big)
        index == 0 -> RoundedCornerShape(
            topStart = big,
            topEnd = big,
            bottomStart = small,
            bottomEnd = small,
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = small,
            topEnd = small,
            bottomStart = big,
            bottomEnd = big,
        )
        else -> RoundedCornerShape(small)
    }
}

/**
 * Expressive section title — used as a heading above content groups.
 */
@Composable
fun ExpressiveSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            supportingText?.let { supporting ->
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

/** Small provenance hint shown while visible content still comes from SQLDelight. */
@Composable
fun ExpressiveCacheHint(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = if (isRefreshing) "缓存数据 · 正在更新" else "当前为缓存数据"
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            stateDescription = label
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** Full-window page background with a bounded content pane on larger windows. */
@Composable
fun ExpressivePage(
    accentPalette: ContentAccentPalette,
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = ExpressiveLayout.BrowseContentMaxWidth,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentPalette.pageStart,
                        accentPalette.pageMiddle,
                        accentPalette.pageEnd,
                    ),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val boundedWidth = minOf(maxWidth, maxContentWidth)
            Box(
                modifier = Modifier
                    .width(boundedWidth)
                    .fillMaxHeight()
                    .windowInsetsPadding(contentWindowInsets)
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}

/** Unified Material state panel for empty, error and informational screen states. */
@Composable
fun ExpressiveStatePanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: ExpressiveStateTone = ExpressiveStateTone.Neutral,
    accentPalette: ContentAccentPalette? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val isError = tone == ExpressiveStateTone.Error
    val containerColor = if (isError) scheme.errorContainer else scheme.surfaceContainerHigh
    val contentColor = if (isError) scheme.onErrorContainer else scheme.onSurface
    val supportingContentColor = if (isError) scheme.onErrorContainer else scheme.onSurfaceVariant
    val accentColor = if (isError) scheme.error else accentPalette?.accent ?: scheme.primary
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = if (isError) "错误：$title" else title
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = accentColor,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = supportingContentColor,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentPalette?.accent ?: scheme.primary,
                        contentColor = accentPalette?.onAccent ?: scheme.onPrimary,
                    ),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/** Consistent loading presentation; centralized so the expressive indicator can be swapped safely. */
@Composable
fun ExpressiveLoadingState(
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = label
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LoadingIndicator(color = accentColor)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
