package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveArtwork
import com.leejlredstar.redefinencm.kmp.ui.component.rememberPressMorphProgress
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.LocalReducedMotion
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberArtworkAccent
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class HomeGalleryStyle { Track, Playlist }

internal fun homeGalleryPreferredWidth(style: HomeGalleryStyle, width: Dp): Dp = when (style) {
    HomeGalleryStyle.Track -> (width * 0.48f).coerceIn(minOf(width, 112.dp), minOf(width, 156.dp))
    HomeGalleryStyle.Playlist -> (width * 0.58f).coerceIn(minOf(width, 144.dp), minOf(width, 224.dp))
}

// Keep the full playlist cover a horizontal capsule on both the outer and inner screen.
internal fun HomeGalleryStyle.baseHeight(preferredWidth: Dp): Dp = when (this) {
    HomeGalleryStyle.Track -> 112.dp
    HomeGalleryStyle.Playlist -> preferredWidth * 0.64f
}

private fun HomeGalleryStyle.cornerRadius(size: Size, density: Density): Float =
    (minOf(size.width, size.height) / 2f).let { full ->
        if (this == HomeGalleryStyle.Playlist) full else minOf(full, with(density) { 16.dp.toPx() })
    }

private fun HomeGalleryStyle.captionInset(bounds: Rect, corner: Float, density: Density): Float = with(density) {
    if (this@captionInset == HomeGalleryStyle.Playlist) {
        // Lift the caption toward the wider part of a capsule as it becomes taller than wide.
        val squeeze = (1f - bounds.width / bounds.height).coerceAtLeast(0f)
        maxOf(10.dp.toPx(), corner * (0.32f + 0.95f * squeeze))
    } else 8.dp.toPx()
}

/**
 * Home's two galleries share Material's keylines, masks and snapping, not a fixed card row.
 * Reduced motion alone uses same-size tiles with direct scrolling and no scroll/press effects.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeGallery(
    count: Int,
    style: HomeGalleryStyle,
    gutter: Dp,
    modifier: Modifier = Modifier,
    header: @Composable (pager: (@Composable () -> Unit)?) -> Unit,
    itemContent: @Composable (index: Int, mask: CarouselItemScope?, height: Dp) -> Unit,
) {
    val carousel = rememberCarouselState { count }
    val still = rememberLazyListState()
    val reducedMotion = LocalReducedMotion.current
    val usePager = !getPlatform().isMobile && count > 1
    val scope = rememberCoroutineScope()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val canBack = if (reducedMotion) still.canScrollBackward else carousel.currentItem > 0
    val canForward = if (reducedMotion) still.canScrollForward else carousel.currentItem < count - 1
    val advance: (Int) -> Boolean = { direction ->
        if ((direction < 0 && !canBack) || (direction > 0 && !canForward)) {
            false
        } else {
            if (reducedMotion) {
                val target = still.firstVisibleItemIndex + direction
                scope.launch { still.scrollToItem(target.coerceIn(0, count - 1)) }
            } else if (!carousel.isScrollInProgress) {
                val target = carousel.currentItem + direction
                scope.launch { carousel.animateScrollToItem(target.coerceIn(0, count - 1), spatial) }
            }
            true
        }
    }
    val latestAdvance = rememberUpdatedState(advance)
    Column(modifier) {
        header(if (usePager) { { HomeGalleryPager(canBack, canForward, advance) } } else null)
        if (count > 0) {
            // Give each rail its own exact viewport inset; don't subtract the gutter a second
            // time from the Material keylines (which would shrink the first small peek).
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = gutter)) {
                if (maxWidth > 0.dp) {
                    val width = homeGalleryPreferredWidth(style, maxWidth)
                    val height = style.baseHeight(width) + 36.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
                    val rail = Modifier.fillMaxWidth().focusGroup()
                        .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = count) }
                        .then(if (usePager) Modifier.homeGalleryWheel { latestAdvance.value(it) } else Modifier)
                    val padding = PaddingValues(vertical = 8.dp)
                    if (reducedMotion) {
                        LazyRow(
                            state = still,
                            modifier = rail,
                            contentPadding = padding,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(count) { index ->
                                Box(Modifier.width(width)) { itemContent(index, null, height) }
                            }
                        }
                    } else {
                        HorizontalMultiBrowseCarousel(
                            state = carousel,
                            preferredItemWidth = width,
                            modifier = rail,
                            itemSpacing = 8.dp,
                            // Restrict the small keyline to the accessible end of M3's 40–56dp.
                            minSmallItemWidth = 48.dp,
                            maxSmallItemWidth = 56.dp,
                            contentPadding = padding,
                            flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(carousel, snapAnimationSpec = spatial),
                        ) { index -> itemContent(index, this, height) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeGalleryPager(canBack: Boolean, canForward: Boolean, advance: (Int) -> Boolean) {
    Row {
        IconButton(onClick = { advance(-1) }, enabled = canBack) {
            Icon(AppIcons.KeyboardArrowLeft, contentDescription = strings.carouselScrollBack)
        }
        IconButton(onClick = { advance(1) }, enabled = canForward) {
            Icon(AppIcons.KeyboardArrowRight, contentDescription = strings.carouselScrollForward)
        }
    }
}

/** Leave ordinary vertical scrolling to the feed, and native horizontal deltas to Material. */
private fun Modifier.homeGalleryWheel(advance: (Int) -> Boolean): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll || !event.keyboardModifiers.isShiftPressed) continue
            val mouse = event.changes.firstOrNull { !it.isConsumed && it.type == PointerType.Mouse } ?: continue
            val (dx, dy) = mouse.scrollDelta
            if (abs(dy) > abs(dx) && advance(if (dy > 0f) 1 else -1)) mouse.consume()
        }
    }
}

/** Horizontal room at the narrowest cross-section of the rounded caption area. */
internal fun homeGalleryCaptionWidth(mask: Rect, radius: Float, top: Float, bottom: Float, padding: Float): Int {
    if (mask.isEmpty || top < mask.top || bottom > mask.bottom || top >= bottom) return 0
    val edgeDistance = minOf(top - mask.top, mask.bottom - bottom)
    val delta = (radius - edgeDistance).coerceAtLeast(0f)
    val cut = radius - kotlin.math.sqrt((radius * radius - delta * delta).coerceAtLeast(0f))
    return ((mask.width - (cut + padding) * 2f).toInt()).coerceAtLeast(0)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.galleryCaption(
    info: CarouselItemDrawInfo?,
    style: HomeGalleryStyle,
    title: String,
    subtitle: String?,
): Modifier {
    val measurer = rememberTextMeasurer()
    val playlist = style == HomeGalleryStyle.Playlist
    val align = if (playlist) TextAlign.Center else TextAlign.Start
    val titleStyle = (if (playlist) MaterialTheme.typography.titleMediumEmphasized
        else MaterialTheme.typography.titleSmallEmphasized).copy(textAlign = align)
    val subtitleStyle = MaterialTheme.typography.bodySmall.copy(textAlign = align)
    return drawWithCache {
        val card = Rect(Offset.Zero, size)
        val bounds = info?.maskRect?.intersect(card) ?: card
        if (bounds.isEmpty) return@drawWithCache onDrawBehind {}
        // Fit the text inside the actual mask and the rounded ends, not inside an invisible full-width card.
        val corner = style.cornerRadius(bounds.size, this)
        val captionBottom = bounds.bottom - style.captionInset(bounds, corner, this)
        val availableHeight = captionBottom - (bounds.top + 4.dp.toPx())
        val titleLineHeight = titleStyle.lineHeight.toPx()
        val subtitleHeight = subtitleStyle.lineHeight.toPx()
        val artist = subtitle?.takeIf { it.isNotBlank() && availableHeight >= titleLineHeight + subtitleHeight }
        val reservedSubtitle = if (artist == null) 0f else subtitleHeight
        // A squeezed playlist still shows its title, but doesn't break a word across two lines.
        val lineLimit = if (playlist && bounds.width > bounds.height) 2 else 1
        val maxLines = ((availableHeight - reservedSubtitle) / titleLineHeight).toInt().coerceIn(0, lineLimit)
        val reservedTop = captionBottom - maxLines * titleLineHeight - reservedSubtitle
        val width = homeGalleryCaptionWidth(bounds, corner, reservedTop, captionBottom, 8.dp.toPx())
        // Only the genuinely tiny edge peeks are artwork-only. A medium card ellipsizes its
        // title at full opacity; its complete name remains on the clickable semantics node.
        val showCaption = maxLines > 0 && width >= titleStyle.fontSize.toPx() * 3.5f
        val nameLayout = if (showCaption) measurer.measure(
            text = title, style = titleStyle, overflow = TextOverflow.Ellipsis, maxLines = maxLines,
            constraints = Constraints.fixedWidth(width),
        ) else null
        val artistLayout = if (showCaption && artist != null) measurer.measure(
            text = artist, style = subtitleStyle, overflow = TextOverflow.Ellipsis, maxLines = 1,
            constraints = Constraints.fixedWidth(width),
        ) else null
        val captionTop = captionBottom - (nameLayout?.size?.height ?: 0) - (artistLayout?.size?.height ?: 0)
        val captionLeft = bounds.center.x - width / 2f
        // Like the original gallery, darken the cover itself instead of drawing a label tile.
        // Reach the contrast floor before the first glyph, even over a white cover at large type.
        val darkAt = if (nameLayout == null) {
            bounds.top + bounds.height * 0.70f
        } else captionTop.coerceAtLeast(bounds.top)
        val fadeStart = (darkAt - bounds.height * 0.32f).coerceAtLeast(bounds.top)
        val range = bounds.bottom - fadeStart
        val scrim = if (range > 0f) Brush.verticalGradient(
            0f to if (darkAt == fadeStart) Color.Black.copy(alpha = 0.60f) else Color.Transparent,
            ((darkAt - fadeStart) / range) to Color.Black.copy(alpha = 0.60f),
            1f to Color.Black.copy(alpha = 0.90f),
            startY = fadeStart, endY = bounds.bottom,
        ) else null
        onDrawBehind {
            if (scrim != null) drawRect(scrim, Offset(bounds.left, fadeStart), Size(bounds.width, range))
            if (nameLayout != null) drawText(nameLayout, color = Color.White, topLeft = Offset(captionLeft, captionTop))
            if (artistLayout != null) drawText(
                artistLayout, color = Color.White,
                topLeft = Offset(captionLeft, captionBottom - artistLayout.size.height),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeGalleryCard(
    index: Int,
    itemKey: String,
    mask: CarouselItemScope?,
    style: HomeGalleryStyle,
    height: Dp,
    cover: String,
    title: String,
    subtitle: String? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
) = key(itemKey) {
    val scheme = MaterialTheme.colorScheme
    val artworkAccent = rememberArtworkAccent(
        requestKey = cover,
        fallback = if (style == HomeGalleryStyle.Playlist) scheme.tertiaryContainer else scheme.secondaryContainer,
        label = "homeGalleryAccent",
    )
    val palette = artworkAccent.palette
    val info = mask?.carouselItemDrawInfo
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState()
    val press = rememberPressMorphProgress(interaction)
    val frame = remember(style, press) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                // Read progress in the outline: the carousel remembers its mask Shape.
                val radius = CornerRadius(style.cornerRadius(size, density) * (1f - 0.35f * press.value.coerceIn(0f, 1f)))
                return Outline.Rounded(RoundRect(Rect(Offset.Zero, size), radius))
            }
        }
    }
    val shape = mask?.rememberMaskShape(frame) ?: frame
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val playbackState = if (isCurrent) {
        if (isPlaying) strings.playing else strings.paused
    } else null
    Box(Modifier.fillMaxWidth().height(height).clip(shape)) {
        ExpressiveArtwork(
            model = cover,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            shape = RectangleShape,
            containerColor = palette.quietContainer,
            contentColor = palette.onQuietContainer,
            placeholderIcon = if (style == HomeGalleryStyle.Playlist) AppIcons.QueueMusic else AppIcons.MusicNote,
            onImageLoaded = artworkAccent.extract,
            imageModifier = if (info == null) Modifier else Modifier.graphicsLayer {
                // A small counter-motion inside the native moving window; no exposed image edges.
                val squeeze = if (info.maxSize > 0f) (1f - info.size / info.maxSize).coerceIn(0f, 1f) else 0f
                scaleX = 1f + 0.06f * squeeze
                scaleY = scaleX
                translationX = direction * size.width * 0.025f * squeeze
            },
        )
        // Decorative artwork continues beneath the status bar. Only the foreground ripple,
        // captions, focus indicator and action are clipped by Home's foreground ruler.
        CompositionLocalProvider(LocalContentColor provides palette.onQuietContainer) {
            Box(
                Modifier.matchParentSize().homeControl()
                    .clickable(interactionSource = interaction, role = Role.Button, onClick = onClick)
                    .galleryCaption(info, style, title, subtitle)
                    .semantics {
                        collectionItemInfo = CollectionItemInfo(0, 1, index, 1)
                        contentDescription = if (subtitle.isNullOrBlank()) title else "$title\n$subtitle"
                        if (playbackState != null) stateDescription = playbackState
                    },
            ) {
                if (focused.value) {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        shape = shape,
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, palette.onQuietContainer),
                    ) {}
                }
                if (isCurrent) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(28.dp),
                        shape = CircleShape,
                        color = palette.accent,
                        contentColor = palette.onAccent,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) AppIcons.GraphicEq else AppIcons.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
