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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
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

/** App-chosen cover proportions. Only the track previews are deliberately low-profile. */
internal enum class HomeGalleryStyle(val height: Dp, val corner: Dp, val pressedCorner: Dp) {
    Track(112.dp, 16.dp, 8.dp),
    Playlist(184.dp, 28.dp, 16.dp),
}

internal fun homeGalleryPreferredWidth(style: HomeGalleryStyle, width: Dp): Dp = when (style) {
    HomeGalleryStyle.Track -> (width * 0.48f).coerceIn(minOf(width, 112.dp), minOf(width, 156.dp))
    HomeGalleryStyle.Playlist -> (width * 0.58f).coerceIn(minOf(width, 144.dp), minOf(width, 224.dp))
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
    itemContent: @Composable (index: Int, mask: CarouselItemScope?) -> Unit,
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
                                Box(Modifier.width(width)) { itemContent(index, null) }
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
                        ) { index -> itemContent(index, this) }
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

/** Captions keep a fixed inner measure and fade out before a moving mask can cut through type. */
internal fun homeGalleryCaptionAlpha(width: Float, fullWidth: Float): Float =
    if (fullWidth <= 0f) 0f else ((width / fullWidth - 0.84f) / 0.12f).coerceIn(0f, 1f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeGalleryCard(
    index: Int,
    itemKey: String,
    mask: CarouselItemScope?,
    style: HomeGalleryStyle,
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
                val corner = style.corner + (style.pressedCorner - style.corner) * press.value.coerceIn(0f, 1f)
                return RoundedCornerShape(corner).createOutline(size, layoutDirection, density)
            }
        }
    }
    val shape = mask?.rememberMaskShape(frame) ?: frame
    val fontExpansion = 36.dp * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    val playbackState = if (isCurrent) {
        if (isPlaying) strings.playing else strings.paused
    } else null
    Box(Modifier.fillMaxWidth().height(style.height + fontExpansion).clip(shape)) {
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
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        border = BorderStroke(2.dp, palette.onQuietContainer),
                    ) {}
                }
                // Opaque, cover-derived color with its matching foreground. The alpha applies
                // to the whole caption (never a white title on a transparent scrim).
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        .fillMaxWidth(0.76f).clearAndSetSemantics {}.graphicsLayer {
                            alpha = if (info == null) 1f else homeGalleryCaptionAlpha(info.size, info.maxSize)
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = palette.quietContainer,
                    contentColor = palette.onQuietContainer,
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(
                            text = title,
                            style = if (style == HomeGalleryStyle.Playlist) MaterialTheme.typography.titleMediumEmphasized
                            else MaterialTheme.typography.titleSmallEmphasized,
                            maxLines = if (subtitle.isNullOrBlank()) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryOnQuietContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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
