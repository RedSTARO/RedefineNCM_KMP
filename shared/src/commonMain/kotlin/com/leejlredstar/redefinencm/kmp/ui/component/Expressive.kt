package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalBrowseObstructions
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette

/** Shared dimensions for the app's Material 3 Expressive layout language. */
object ExpressiveLayout {
    val PageHorizontalPadding = 16.dp
    val PageVerticalPadding = 16.dp
    val SectionSpacing = 32.dp
    // Material segmented lists: separate the group silhouette from its tight inner joints.
    val ConnectedItemGap = 2.dp
    val ConnectedOuterCorner = 16.dp
    val ConnectedInnerCorner = 4.dp
    val FeatureCorner = 32.dp
    val MinimumTouchTarget = 48.dp
    val ReadingContentMaxWidth = 840.dp
    val BrowseContentMaxWidth = 1200.dp

    /**
     * Bottom inset reserved for the floating nav toolbar.
     *
     * The toolbar is an overlay, not a Scaffold `bottomBar`: content scrolls *underneath* it,
     * which is the point of a floating toolbar. Scaffold therefore contributes no bottom
     * padding of its own, so screens are handed this clearance instead: enough that the last
     * list item can be scrolled clear of the pill rather than being permanently hidden by it.
     */
    val FloatingNavClearance = 96.dp
}

/** Gutters follow the usable pane, not the full window hidden behind rails or a cutout. */
internal fun expressivePageGutter(paneWidth: Dp): Dp = if (paneWidth < 600.dp) 16.dp else 24.dp
internal val LocalExpressivePageGutter = staticCompositionLocalOf { ExpressiveLayout.PageHorizontalPadding }

/** App-specific timings for legacy choreography that does not yet use Material springs. */
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
 *
 * [pressProgress] rounds the *inner* corners up toward the outer radius, so a held row visibly
 * detaches from the rows above and below it instead of staying welded into the stack. At `0f`,
 * the default, the result is the plain connected shape.
 */
fun connectedListItemShape(
    index: Int,
    count: Int,
    pressProgress: Float = 0f,
): RoundedCornerShape {
    val big = ExpressiveLayout.ConnectedOuterCorner
    val tight = ExpressiveLayout.ConnectedInnerCorner
    val small = tight + (big - tight) * pressProgress.coerceIn(0f, 1f)
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
 * Connected-list shape that detaches from its neighbours while [interactionSource] is pressed.
 *
 * Pass the same source to the row's `clickable`/`toggleable` so the shape animation and the
 * ripple share one press stream.
 */
@Composable
fun rememberConnectedListItemShape(
    index: Int,
    count: Int,
    interactionSource: InteractionSource,
): RoundedCornerShape {
    val progress by rememberPressMorphProgress(interactionSource)
    return connectedListItemShape(index, count, progress)
}

/**
 * Expressive section title, used as a heading above content groups.
 *
 * The [action] sits beside the title when both fit on one line and moves under it when they do
 * not. If they stayed side by side regardless, a wide action on a narrow window would squeeze
 * the title into a column one character wide.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    action: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Column(
                modifier = Modifier
                    .layoutId(SectionTitleId)
                    .then(
                        if (onClick == null) Modifier else Modifier
                            .heightIn(min = ExpressiveLayout.MinimumTouchTarget)
                            .clickable(onClickLabel = onClickLabel, onClick = onClick),
                    )
                    .semantics(mergeDescendants = true) { heading() },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // The whole heading opens the list, so it carries the chevron that says so.
                    if (onClick != null) {
                        Icon(
                            imageVector = AppIcons.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp).size(SectionTitleChevron),
                        )
                    }
                }
                supportingText?.let { supporting ->
                    Text(
                        text = supporting,
                        // bodyLarge and Medium, not bodyMedium and Normal. At 14sp on a
                        // dark surface the hinting drops a CJK glyph's thinnest strokes: 共
                        // renders with its upper bar missing wherever this line counts
                        // something, and comes back whole as soon as the size goes up.
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // The title's one-line width alone, to judge whether the action fits beside it; the
            // supporting line may wrap, so it does not count.
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 1,
                // Never placed, and hidden from screen readers so the heading is not read twice.
                modifier = Modifier
                    .layoutId(SectionTitleProbeId)
                    .clearAndSetSemantics {},
            )
            if (action != null) {
                Box(Modifier.layoutId(SectionActionId)) { action() }
            }
        },
    ) { measurables, constraints ->
        val gap = 16.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val actionPlaceable = measurables.firstOrNull { it.layoutId == SectionActionId }?.measure(loose)
        val chevron = if (onClick == null) 0 else (SectionTitleChevron + 2.dp).roundToPx()
        val titleWidth = measurables.first { it.layoutId == SectionTitleProbeId }
            .maxIntrinsicWidth(constraints.maxHeight) + chevron
        val titleMeasurable = measurables.first { it.layoutId == SectionTitleId }
        val sideBySide = actionPlaceable == null ||
            titleWidth + gap + actionPlaceable.width <= constraints.maxWidth
        if (sideBySide) {
            val titlePlaceable = titleMeasurable.measure(
                loose.copy(
                    maxWidth = (constraints.maxWidth - (actionPlaceable?.width?.plus(gap) ?: 0))
                        .coerceAtLeast(0),
                ),
            )
            val height = maxOf(titlePlaceable.height, actionPlaceable?.height ?: 0)
            layout(constraints.maxWidth, height) {
                titlePlaceable.placeRelative(0, (height - titlePlaceable.height) / 2)
                actionPlaceable?.placeRelative(
                    constraints.maxWidth - actionPlaceable.width,
                    (height - actionPlaceable.height) / 2,
                )
            }
        } else {
            val titlePlaceable = titleMeasurable.measure(loose)
            val spacing = 8.dp.roundToPx()
            val height = titlePlaceable.height + spacing + actionPlaceable.height
            layout(constraints.maxWidth, height) {
                titlePlaceable.placeRelative(0, 0)
                actionPlaceable.placeRelative(0, titlePlaceable.height + spacing)
            }
        }
    }
}

/** The chevron that marks a heading as the way into its own list. */
private val SectionTitleChevron = 22.dp

private const val SectionTitleId = "section-title"
private const val SectionTitleProbeId = "section-title-probe"
private const val SectionActionId = "section-action"

/** Small provenance hint shown while visible content still comes from SQLDelight. */
@Composable
fun ExpressiveCacheHint(
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = if (isRefreshing) strings.cachedDataUpdating else strings.showingCachedData
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
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = ExpressiveLayout.BrowseContentMaxWidth,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    headerOwnsTopInsets: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        // The alignment has to be on this box: it fills the page, so the outer box's TopCenter
        // has nothing to center, and without it here the bounded pane sits against the left
        // edge.
        val sides = LocalBrowseObstructions.current?.only(WindowInsetsSides.Horizontal)
        val pane = if (sides != null) Modifier.windowInsetsPadding(sides) else Modifier
        BoxWithConstraints(Modifier.fillMaxSize().then(pane), contentAlignment = Alignment.TopCenter) {
            val top = LocalBrowseObstructions.current?.only(WindowInsetsSides.Top)
            val contentInsets = if (top != null && !headerOwnsTopInsets) contentWindowInsets.union(top) else contentWindowInsets
            val boundedWidth = minOf(maxWidth, maxContentWidth)
            CompositionLocalProvider(LocalExpressivePageGutter provides expressivePageGutter(boundedWidth)) {
                Box(
                    modifier = Modifier
                        .width(boundedWidth)
                        .fillMaxHeight()
                        .windowInsetsPadding(contentInsets),
                    content = content,
                )
            }
        }
    }
}

/** Unified Material state panel for empty, error and informational screen states. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val containerColor = if (isError) scheme.errorContainer else scheme.surfaceContainerLow
    val contentColor = if (isError) scheme.onErrorContainer else scheme.onSurface
    val supportingContentColor = if (isError) scheme.onErrorContainer else scheme.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = if (isError) strings.errorStateDescription(title) else title
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.let {
                ExpressiveFeatureIcon(
                    icon = it,
                    containerColor = if (isError) scheme.error else scheme.secondaryContainer,
                    contentColor = if (isError) scheme.onError else scheme.onSecondaryContainer,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
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
                    modifier = Modifier.heightIn(min = ExpressiveLayout.MinimumTouchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentPalette?.accent ?: scheme.primary,
                        contentColor = accentPalette?.onAccent ?: scheme.onPrimary,
                    ),
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

/** A lightweight in-place status, rather than a second hero competing with the page. */
@Composable
fun ExpressiveLoadingState(
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoadingIndicator(color = accentColor, modifier = Modifier.size(32.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
