package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val SearchEditorCorner = 20.dp

internal enum class SearchEndpoint { Home, Editor }

/** Window-relative origin and width only; resizing below the header is not a new anchor. */
internal data class SearchContainerAnchor(val origin: Offset, val width: Float, val safeTop: Float)

/** A clipped pill cannot be reproduced by the unoccluded animation layer. Use the page fade. */
internal fun visibleSearchEndpoint(bounds: Rect, clippedBounds: Rect, viewport: Rect): Boolean =
    bounds.width > 0f && bounds.height > 0f && bounds == clippedBounds &&
        bounds.left >= viewport.left && bounds.right <= viewport.right &&
        bounds.top >= viewport.top && bounds.bottom <= viewport.bottom

internal data class SearchContainerFrame(val bounds: Rect, val corner: Float, val editorFraction: Float) {
    fun towards(end: SearchContainerFrame, progress: Float): SearchContainerFrame {
        // The spring may overshoot, but the visible surface stays inside the safe hull of the
        // two endpoints (not across a status bar, cutout, or hinge).
        val t = progress.coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float) = a + (b - a) * t
        return SearchContainerFrame(
            Rect(
                lerp(bounds.left, end.bounds.left), lerp(bounds.top, end.bounds.top),
                lerp(bounds.right, end.bounds.right), lerp(bounds.bottom, end.bounds.bottom),
            ),
            lerp(corner, end.corner),
            lerp(editorFraction, end.editorFraction),
        )
    }
}

/** One browse-viewport visual. Coordinates never become constraints for the native text field. */
@Stable
internal class SearchContainerTransition {
    private class EndpointLayout {
        var page by mutableStateOf<LayoutCoordinates?>(null)
        var entry: LayoutCoordinates? = null
    }

    internal class Flight(
        val target: SearchEndpoint,
        val anchor: SearchContainerAnchor,
        val from: SearchContainerFrame,
        hintAlpha: Float,
    ) {
        var to by mutableStateOf<SearchContainerFrame?>(null)
        // Hand off at the first arrival, not after the spring's invisible, clamped tail.
        val progress = Animatable(0f).apply { updateBounds(upperBound = 1f) }
        val hint = Animatable(hintAlpha)
        val frame get() = to?.let { from.towards(it, progress.value) } ?: from
    }

    private val home = EndpointLayout()
    private val editor = EndpointLayout()
    private fun layout(endpoint: SearchEndpoint) = if (endpoint == SearchEndpoint.Home) home else editor

    private var viewport: LayoutCoordinates? = null
    private var editorCornerPx = 0f
    private var anchor by mutableStateOf<SearchContainerAnchor?>(null)
    private var validatedTarget by mutableStateOf<SearchContainerFrame?>(null)
    private var flight by mutableStateOf<Flight?>(null)
    var settledEndpoint: SearchEndpoint? = null

    fun switch(from: SearchEndpoint?, to: SearchEndpoint?, enabled: Boolean) {
        val previous = flight
        val currentAnchor = anchor
        if (!enabled || from == null || to == null || from == to || currentAnchor == null ||
            (settledEndpoint != from && previous?.target != from)
        ) {
            cancel()
            return
        }
        val interrupted = previous?.takeIf { it.target == from && it.anchor == currentAnchor }
        val source = interrupted?.frame ?: sample(from)
        validatedTarget = null
        flight = source?.let {
            Flight(to, currentAnchor, it, interrupted?.hint?.value ?: (1f - it.editorFraction))
        }
    }

    fun cancel() { flight = null }

    fun activeFlight(target: SearchEndpoint?): Flight? = flight?.takeIf { it.target == target }

    fun viewportModifier(safeTop: Int, editorCorner: Float): Modifier = Modifier.onGloballyPositioned {
        viewport = it
        editorCornerPx = editorCorner
        val origin = it.positionInWindow()
        anchor = SearchContainerAnchor(origin, it.size.width.toFloat(), (safeTop - origin.y).coerceAtLeast(0f))
        flight?.let { run ->
            if (run.anchor == anchor) validatedTarget = sample(run.target) else cancel(run)
        }
    }

    @Composable
    fun Page(endpoint: SearchEndpoint, content: @Composable () -> Unit) {
        val layout = layout(endpoint)
        DisposableEffect(layout) {
            onDispose {
                layout.page = null
                layout.entry = null
            }
        }
        Box(Modifier.fillMaxSize().onGloballyPositioned { layout.page = it }) { content() }
    }

    fun entryModifier(endpoint: SearchEndpoint, activeFlight: Flight?): Modifier = Modifier
        .onGloballyPositioned {
            layout(endpoint).entry = it
            if (flight?.target == endpoint) validatedTarget = sample(endpoint)
        }
        .drawWithContent { if (activeFlight == null) drawContent() }

    suspend fun play(run: Flight, animate: suspend () -> Unit) {
        try {
            // The page's globally-positioned signal arrives after placement, including when its
            // lazy list has no visible pill. Never wait for a matching endpoint indefinitely.
            snapshotFlow { layout(run.target).page?.isAttached == true }.first { it }
            val end = sample(run.target) ?: return
            if (run.anchor != anchor || run.from == end) return
            run.to = end
            validatedTarget = end
            coroutineScope {
                // This is a one-shot from two real samples. Resize/reflow cancels it; no stream
                // of IME-frame coordinates is ever used to retarget the spring.
                val boundsWatch = launch {
                    snapshotFlow { anchor != run.anchor || validatedTarget != end }.first { it }
                    cancel(run)
                }
                try { animate() } finally { boundsWatch.cancel() }
            }
        } finally {
            cancel(run)
        }
    }

    private fun cancel(run: Flight) { if (flight === run) cancel() }

    private fun sample(endpoint: SearchEndpoint): SearchContainerFrame? {
        val host = viewport?.takeIf { it.isAttached } ?: return null
        val entry = layout(endpoint).entry?.takeIf { it.isAttached } ?: return null
        val a = anchor ?: return null
        val safeViewport = Rect(0f, a.safeTop, a.width, host.size.height.toFloat())
        val bounds = host.localBoundingBoxOf(entry, clipBounds = false)
        if (!visibleSearchEndpoint(bounds, host.localBoundingBoxOf(entry, clipBounds = true), safeViewport)) return null
        val isEditor = endpoint == SearchEndpoint.Editor
        return SearchContainerFrame(
            bounds = bounds,
            corner = if (isEditor) editorCornerPx else minOf(bounds.width, bounds.height) / 2f,
            editorFraction = if (isEditor) 1f else 0f,
        )
    }
}

/** Draw the moving surface and its identity, not a second editable/accessible field. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchContainerViewport(
    transition: SearchContainerTransition,
    active: SearchContainerTransition.Flight?,
    safeTopInWindow: Int,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val label = strings.searchSongs
    val textStyle = MaterialTheme.typography.bodyLarge
    val painter = rememberVectorPainter(AppIcons.Search)
    val textMeasurer = rememberTextMeasurer()
    val cornerPx = with(LocalDensity.current) { SearchEditorCorner.toPx() }
    if (active != null) {
        LaunchedEffect(active) {
            transition.play(active) {
                coroutineScope {
                    val hint = launch {
                        val targetAlpha = if (active.target == SearchEndpoint.Home) 1f else 0f
                        active.hint.animateTo(targetAlpha, motion.fastEffectsSpec())
                    }
                    try {
                        active.progress.animateTo(1f, motion.defaultSpatialSpec())
                    } finally {
                        hint.cancel()
                    }
                }
            }
        }
    }
    Box(
        modifier.fillMaxSize().clipToBounds()
            .then(transition.viewportModifier(safeTopInWindow, cornerPx))
            .drawWithContent {
                drawContent()
                if (active == null) return@drawWithContent
                val frame = active.frame
                val bounds = frame.bounds
                if (bounds.bottom > size.height) return@drawWithContent
                val iconSize = 24.dp.toPx()
                val leading = (20f - 8f * frame.editorFraction).dp.toPx()
                val ltr = layoutDirection == LayoutDirection.Ltr
                val iconX = if (ltr) bounds.left + leading else bounds.right - leading - iconSize
                drawRoundRect(
                    color = scheme.surfaceContainerHigh,
                    topLeft = bounds.topLeft,
                    size = bounds.size,
                    cornerRadius = CornerRadius(frame.corner),
                )
                clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                    translate(iconX, bounds.center.y - iconSize / 2f) {
                        with(painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(scheme.onSurfaceVariant)) }
                    }
                    val hintAlpha = active.hint.value
                    if (hintAlpha > 0f) {
                        val labelSpace = (bounds.width - leading - iconSize - 12.dp.toPx() - 20.dp.toPx()).roundToInt()
                        if (labelSpace > 0) {
                            val hint = textMeasurer.measure(
                                text = label, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                constraints = Constraints(maxWidth = labelSpace),
                            )
                            val textX = if (ltr) iconX + iconSize + 12.dp.toPx() else iconX - 12.dp.toPx() - hint.size.width
                            drawText(
                                hint, color = scheme.onSurfaceVariant, alpha = hintAlpha,
                                topLeft = Offset(textX, bounds.center.y - hint.size.height / 2f),
                            )
                        }
                    }
                }
            },
    ) { content() }
}
