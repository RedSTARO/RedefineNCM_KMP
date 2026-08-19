/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Native Compose translation/adaptation of Apple Music-like Lyrics and the former
 * RedefineNCM AMLL host.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.data.SongWikiSection
import com.leejlredstar.redefinencm.kmp.ui.amll.nextAmllArtworkUriAfterFailure
import com.leejlredstar.redefinencm.kmp.ui.theme.DarkColors
import com.leejlredstar.redefinencm.kmp.ui.theme.buildContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.viewmodel.SongWikiUiState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Literal Compose translation of HEAD
 * shared/src/commonMain/amllAssets/amll/player.html (AMLL core 0.5.2),
 * selectors :root and #wiki-info through #wiki-dialog/.wiki-*.
 *
 * Colours are no longer a frozen copy of the dark scheme. That copy was thirteen literals
 * duplicating DarkColors, so this surface could not follow the theme, could not be tinted by the
 * artwork the way every other page is, and drifted silently whenever Color.kt changed.
 *
 * It still pins the dark scheme, because it always sits over the player. The trade-off is that
 * the Desktop Legacy renderer draws this dialog from player.html's fixed CSS instead, so the two
 * now differ there until that CSS is updated to match; every other renderer uses this composable.
 */
private val WikiExpressiveEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val WikiCssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private const val WikiOverlayMillis = 180
private const val WikiDialogTransformMillis = 280
private const val WikiControlBackgroundMillis = 160
private const val WikiControlTransformMillis = 220

internal data class SongWikiDialogGeometry(
    val mobile: Boolean,
    val lowHeight: Boolean,
    val overlayStartDp: Float,
    val overlayTopDp: Float,
    val overlayEndDp: Float,
    val overlayBottomDp: Float,
    val maxDialogHeightDp: Float,
)

/**
 * Resolves the literal `#wiki-overlay`/`#wiki-dialog` media-query geometry from player.html.
 * Safe-area values are explicit inputs so the same calculation is testable on every target.
 */
internal fun songWikiDialogGeometry(
    viewportWidthDp: Float,
    viewportHeightDp: Float,
    safeStartDp: Float = 0f,
    safeTopDp: Float = 0f,
    safeEndDp: Float = 0f,
    safeBottomDp: Float = 0f,
): SongWikiDialogGeometry {
    val mobile = viewportWidthDp <= 600f
    val lowHeight = !mobile && viewportHeightDp <= 560f
    return when {
        mobile -> SongWikiDialogGeometry(
            mobile = true,
            lowHeight = false,
            overlayStartDp = 0f,
            overlayTopDp = 48f + safeTopDp,
            overlayEndDp = 0f,
            overlayBottomDp = 0f,
            maxDialogHeightDp = (viewportHeightDp - 48f - safeTopDp).coerceAtLeast(1f),
        )
        lowHeight -> SongWikiDialogGeometry(
            mobile = false,
            lowHeight = true,
            overlayStartDp = 18f + safeStartDp,
            overlayTopDp = 10f,
            overlayEndDp = 18f + safeEndDp,
            overlayBottomDp = 10f,
            maxDialogHeightDp = (viewportHeightDp - 20f).coerceAtLeast(1f),
        )
        else -> SongWikiDialogGeometry(
            mobile = false,
            lowHeight = false,
            overlayStartDp = 18f + safeStartDp,
            overlayTopDp = 18f + safeTopDp,
            overlayEndDp = 18f + safeEndDp,
            overlayBottomDp = 18f + safeBottomDp,
            maxDialogHeightDp = minOf(viewportHeightDp * 0.86f, 820f).coerceAtLeast(1f),
        )
    }
}

@Composable
fun SongWikiDetailsButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White.copy(alpha = 0.94f),
    reducedMotion: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    WikiIconAction(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.then(
            focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
        ),
        icon = {
            Icon(
                imageVector = AppIcons.Info,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        },
        label = "查看歌曲详细信息",
        baseColor = Color.Transparent,
        hoverColor = Color.White.copy(alpha = 0.12f),
        disabledAlpha = 0.38f,
        reducedMotion = reducedMotion,
    )
}

/**
 * Shared native form of the AMLL music-wiki dialog.
 *
 * [reducedMotion] has a default to preserve existing call sites. AMLL callers should pass their
 * platform preference so the 180/280 ms dialog motion, control scaling, cover fade, and spinner
 * rotation all snap exactly like `@media (prefers-reduced-motion: reduce)`.
 *
 * [returnFocusRequester] should be the same requester attached to the control that opened the
 * dialog. It is optional because callers that do not own a stable opener cannot safely restore
 * focus across a platform Dialog window.
 */
@Composable
fun SongWikiDetailsSheet(
    visible: Boolean,
    songTitle: String?,
    state: SongWikiUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    songArtist: String? = null,
    albumTitle: String? = null,
    artworkUri: String? = null,
    fallbackArtworkUri: String? = null,
    durationMs: Long? = null,
    artworkOverlay: (@Composable BoxScope.() -> Unit)? = null,
    reducedMotion: Boolean = false,
    returnFocusRequester: FocusRequester? = null,
) {
    var keepComposed by remember { mutableStateOf(visible) }
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(visible, reducedMotion) {
        if (visible) {
            keepComposed = true
            open = false
            if (!reducedMotion) withFrameNanos { }
            open = true
        } else if (keepComposed) {
            open = false
            if (!reducedMotion) delay(WikiOverlayMillis.toLong())
            keepComposed = false
        }
    }
    if (!keepComposed) return

    val latestReturnFocusRequester by rememberUpdatedState(returnFocusRequester)
    DisposableEffect(Unit) {
        onDispose {
            latestReturnFocusRequester?.let { requester ->
                runCatching { requester.requestFocus() }
            }
        }
    }

    val requestDismiss = {
        if (open) {
            // CSS removes `.open` (and therefore pointer-events) before its 180 ms fade completes.
            // Flip the local state first so repeated pointer/back events cannot invoke callbacks.
            open = false
            onDismiss()
            // player.html restores the opener immediately, before the 180 ms overlay teardown.
            // Some platform Dialog implementations defer the request; the DisposableEffect
            // fallback below repeats it after the native dialog window has been removed.
            returnFocusRequester?.let { requester ->
                runCatching { requester.requestFocus() }
            }
        }
    }

    // This sheet always sits over the full-screen player, which is a dark surface regardless of
    // the app's theme, so it pins the dark scheme rather than inheriting a light one. It used to
    // pin a *hand-copied* dark palette instead — thirteen literals duplicating DarkColors, which
    // meant the surface could not follow the theme, could not be tinted, and silently drifted
    // whenever Color.kt changed.
    //
    // The primary roles and the tonal surfaces are rebuilt from the artwork, so the panel now
    // picks up the song's colour the same way every other page does.
    val artworkAccentSource = artworkUri ?: fallbackArtworkUri
    var rawWikiAccent by remember(artworkAccentSource) { mutableStateOf(DarkColors.primary) }
    val extractWikiAccent = rememberThemeColorExtractor(artworkAccentSource) { rawWikiAccent = it }
    val wikiAccent by animateColorAsState(
        targetValue = rawWikiAccent,
        animationSpec = spring(),
        label = "wiki-accent",
    )
    val wikiPalette = remember(wikiAccent) { buildContentAccentPalette(wikiAccent, DarkColors) }
    val wikiScheme = remember(wikiPalette) {
        DarkColors.copy(
            primary = wikiPalette.accent,
            onPrimary = wikiPalette.onAccent,
            primaryContainer = wikiPalette.container,
            onPrimaryContainer = wikiPalette.onContainer,
            surfaceContainerHigh = wikiPalette.quietContainer,
            surfaceContainerHighest = wikiPalette.container,
        )
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (open) 0.64f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(WikiOverlayMillis, easing = WikiCssEase),
        label = "wiki-overlay-opacity",
    )
    val dialogAlpha by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(WikiOverlayMillis, easing = WikiCssEase),
        label = "wiki-dialog-opacity",
    )
    val dialogMotion by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(WikiDialogTransformMillis, easing = WikiExpressiveEasing)
        },
        label = "wiki-dialog-transform",
    )

    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // Everything below reads MaterialTheme, so the artwork-tinted dark scheme is installed
        // once here rather than threaded through two dozen call sites.
        MaterialTheme(
            colorScheme = wikiScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .semantics { paneTitle = "歌曲详细信息 · 音乐百科" },
        ) {
            val viewportWidth = maxWidth
            val safeArea = WindowInsets.safeDrawing.asPaddingValues()
            val layoutDirection = LocalLayoutDirection.current
            val safeTop = safeArea.calculateTopPadding()
            val safeBottom = safeArea.calculateBottomPadding()
            val safeStart = safeArea.calculateStartPadding(layoutDirection)
            val safeEnd = safeArea.calculateEndPadding(layoutDirection)
            val geometry = songWikiDialogGeometry(
                viewportWidthDp = maxWidth.value,
                viewportHeightDp = maxHeight.value,
                safeStartDp = safeStart.value,
                safeTopDp = safeTop.value,
                safeEndDp = safeEnd.value,
                safeBottomDp = safeBottom.value,
            )
            val mobile = geometry.mobile
            val lowHeight = geometry.lowHeight
            val overlayPadding = PaddingValues(
                start = geometry.overlayStartDp.dp,
                top = geometry.overlayTopDp.dp,
                end = geometry.overlayEndDp.dp,
                bottom = geometry.overlayBottomDp.dp,
            )
            val dialogShape = if (mobile) {
                RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            } else {
                RoundedCornerShape(36.dp)
            }
            val maxDialogHeight = geometry.maxDialogHeightDp.dp
            val closedTranslation = if (mobile) 48.dp else 24.dp
            val closedScale = if (mobile) 0.98f else 0.94f
            val closeFocusRequester = remember { FocusRequester() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = overlayAlpha))
                    .then(
                        if (open) {
                            Modifier.pointerInput(requestDismiss) {
                                detectTapGestures(onTap = { requestDismiss() })
                            }
                        } else {
                            Modifier
                        },
                    ),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(overlayPadding),
                contentAlignment = if (mobile) Alignment.BottomCenter else Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (mobile) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.widthIn(max = 720.dp).fillMaxWidth()
                            },
                        )
                        .heightIn(max = maxDialogHeight)
                        .graphicsLayer {
                            alpha = dialogAlpha
                            scaleX = closedScale + ((1f - closedScale) * dialogMotion)
                            scaleY = closedScale + ((1f - closedScale) * dialogMotion)
                            translationY = closedTranslation.toPx() * (1f - dialogMotion)
                            transformOrigin = TransformOrigin(0.5f, if (mobile) 1f else 0.5f)
                        },
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxDialogHeight)
                            .dropShadow(
                                shape = dialogShape,
                                shadow = Shadow(
                                    radius = 96.dp,
                                    color = Color.Black,
                                    spread = 0.dp,
                                    offset = DpOffset(x = 0.dp, y = 28.dp),
                                    alpha = 0.56f,
                                ),
                            )
                            .wikiDialogBorder(
                                mobile = mobile,
                                shape = dialogShape,
                            )
                            .then(
                                if (open) {
                                    Modifier.pointerInput(Unit) {
                                        // Keep dialog whitespace from reaching the dismiss layer
                                        // without consuming events handled by child controls.
                                        awaitPointerEventScope {
                                            while (true) awaitPointerEvent()
                                        }
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        shape = dialogShape,
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        WikiDialogContent(
                            songTitle = songTitle,
                            songArtist = songArtist,
                            albumTitle = albumTitle,
                            artworkUri = artworkUri,
                            fallbackArtworkUri = fallbackArtworkUri,
                            durationMs = durationMs,
                            artworkOverlay = artworkOverlay,
                            state = state,
                            onRetry = { if (open) onRetry() },
                            onDismiss = requestDismiss,
                            closeFocusRequester = closeFocusRequester,
                            mobile = mobile,
                            lowHeight = lowHeight,
                            viewportWidth = viewportWidth,
                            reducedMotion = reducedMotion,
                        )
                    }
                }
            }

            LaunchedEffect(open) {
                if (open) {
                    withFrameNanos { }
                    runCatching { closeFocusRequester.requestFocus() }
                }
            }
        }
        }
    }
}

@Composable
private fun WikiDialogContent(
    songTitle: String?,
    songArtist: String?,
    albumTitle: String?,
    artworkUri: String?,
    fallbackArtworkUri: String?,
    durationMs: Long?,
    artworkOverlay: (@Composable BoxScope.() -> Unit)?,
    state: SongWikiUiState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    closeFocusRequester: FocusRequester,
    mobile: Boolean,
    lowHeight: Boolean,
    viewportWidth: Dp,
    reducedMotion: Boolean,
) {
    val validSections = remember(state) {
        (state as? SongWikiUiState.Content)
            ?.summary
            ?.sections
            .orEmpty()
            .filter { it.title.isNotBlank() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        WikiHero(
            songTitle = songTitle,
            songArtist = songArtist,
            albumTitle = albumTitle,
            artworkUri = artworkUri,
            fallbackArtworkUri = fallbackArtworkUri,
            durationMs = durationMs,
            artworkOverlay = artworkOverlay,
            onDismiss = onDismiss,
            closeFocusRequester = closeFocusRequester,
            mobile = mobile,
            lowHeight = lowHeight,
            viewportWidth = viewportWidth,
            reducedMotion = reducedMotion,
        )

        Text(
            text = "歌曲档案",
            modifier = Modifier
                .padding(
                    start = if (mobile) 20.dp else 24.dp,
                    top = when {
                        lowHeight -> 16.dp
                        mobile -> 20.dp
                        else -> 22.dp
                    },
                    bottom = 10.dp,
                )
                .semantics { heading() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.52.sp,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .then(if (mobile) Modifier.navigationBarsPadding() else Modifier),
            contentPadding = PaddingValues(
                start = if (mobile) 16.dp else 24.dp,
                end = if (mobile) 16.dp else 24.dp,
                bottom = if (mobile) 20.dp else 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(1.5.dp),
        ) {
            when {
                state is SongWikiUiState.Idle || state is SongWikiUiState.Loading -> {
                    item(key = "wiki-loading") {
                        WikiLoadingState(reducedMotion = reducedMotion)
                    }
                }
                state is SongWikiUiState.Error -> {
                    item(key = "wiki-error") {
                        WikiStatePanel(
                            kind = WikiStateKind.Error,
                            title = "音乐百科加载失败",
                            message = state.message.ifBlank { "请检查网络后重试" },
                            actionLabel = "重试",
                            onAction = onRetry,
                            reducedMotion = reducedMotion,
                        )
                    }
                }
                state is SongWikiUiState.Empty || validSections.isEmpty() -> {
                    item(key = "wiki-empty") {
                        WikiStatePanel(
                            kind = WikiStateKind.Empty,
                            title = "暂无简要信息",
                            message = "这首歌曲暂未提供音乐百科内容",
                            reducedMotion = reducedMotion,
                        )
                    }
                }
                else -> {
                    itemsIndexed(
                        items = validSections,
                        key = { index, section -> "${section.title}:$index" },
                    ) { index, section ->
                        WikiSectionCard(
                            section = section,
                            index = index,
                            count = validSections.size,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WikiHero(
    songTitle: String?,
    songArtist: String?,
    albumTitle: String?,
    artworkUri: String?,
    fallbackArtworkUri: String?,
    durationMs: Long?,
    artworkOverlay: (@Composable BoxScope.() -> Unit)?,
    onDismiss: () -> Unit,
    closeFocusRequester: FocusRequester,
    mobile: Boolean,
    lowHeight: Boolean,
    viewportWidth: Dp,
    reducedMotion: Boolean,
) {
    val artworkSize = when {
        lowHeight -> 96.dp
        mobile -> 108.dp
        else -> 144.dp
    }
    // Corner radii track the expressive shape scale rather than a MaterialShapes silhouette:
    // this is a large hero cover, and the scalloped silhouettes crop recognisable album art
    // badly at any size — the same reason ArtworkBloom rests on a rounded square.
    val artworkShape = RoundedCornerShape(
        when {
            lowHeight -> 32.dp
            mobile -> 36.dp
            else -> 44.dp
        },
    )
    val heroHorizontalPadding = if (mobile) 20.dp else if (lowHeight) 20.dp else 24.dp
    val heroVerticalPadding = if (lowHeight) 18.dp else if (mobile) 20.dp else 24.dp
    val heroGap = if (mobile || lowHeight) 18.dp else 24.dp
    val titleSize = if (mobile) {
        (viewportWidth.value * 0.07f).coerceIn(23f, 30f)
    } else {
        (viewportWidth.value * 0.04f).coerceIn(25f, 36f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wikiHeroBackground(
                gradientStart = MaterialTheme.colorScheme.surfaceContainerHighest,
                gradientEnd = MaterialTheme.colorScheme.surfaceContainer,
                glow = MaterialTheme.colorScheme.primary,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = heroHorizontalPadding,
                    vertical = heroVerticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(heroGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WikiArtwork(
                songTitle = songTitle,
                artworkUri = artworkUri,
                fallbackArtworkUri = fallbackArtworkUri,
                artworkOverlay = artworkOverlay,
                size = artworkSize,
                shape = artworkShape,
                reducedMotion = reducedMotion,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (mobile) 44.dp else 0.dp),
            ) {
                Text(
                    text = "歌曲详细信息 · 音乐百科",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight(750),
                    letterSpacing = 0.96.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = songTitle?.trim().takeUnless { it.isNullOrEmpty() } ?: "当前歌曲",
                    modifier = Modifier.semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = titleSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = (titleSize * 1.08f).sp,
                    letterSpacing = (titleSize * -0.025f).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                WikiMetadata(
                    artist = songArtist?.trim().takeUnless { it.isNullOrEmpty() } ?: "未知作者",
                    album = albumTitle?.trim().takeUnless { it.isNullOrEmpty() } ?: "未知专辑",
                    duration = durationMs
                        ?.takeIf { it > 0L }
                        ?.let(::formatSongDuration)
                        ?: "未知时长",
                )
            }

            if (!mobile) {
                WikiCloseButton(
                    onDismiss = onDismiss,
                    focusRequester = closeFocusRequester,
                    reducedMotion = reducedMotion,
                    modifier = Modifier.align(Alignment.Top),
                )
            }
        }

        if (mobile) {
            WikiCloseButton(
                onDismiss = onDismiss,
                focusRequester = closeFocusRequester,
                reducedMotion = reducedMotion,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun WikiArtwork(
    songTitle: String?,
    artworkUri: String?,
    fallbackArtworkUri: String?,
    artworkOverlay: (@Composable BoxScope.() -> Unit)?,
    size: Dp,
    shape: RoundedCornerShape,
    reducedMotion: Boolean,
) {
    var displayedArtworkUri by remember(artworkUri, fallbackArtworkUri) {
        mutableStateOf(artworkUri)
    }
    var loaded by remember(displayedArtworkUri) { mutableStateOf(false) }
    val imageAlpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(220, easing = WikiCssEase),
        label = "wiki-cover-opacity",
    )

    Box(
        modifier = Modifier
            .size(size)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 32.dp,
                    color = Color.Black,
                    spread = 0.dp,
                    offset = DpOffset(x = 0.dp, y = 14.dp),
                    alpha = 0.28f,
                ),
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(38.dp).alpha(0.72f),
        )
        AsyncImage(
            model = displayedArtworkUri,
            contentDescription = songTitle
                ?.takeIf(String::isNotBlank)
                ?.let { "$it 的专辑封面" }
                ?: "专辑封面",
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            onError = {
                loaded = false
                nextAmllArtworkUriAfterFailure(
                    failedUri = displayedArtworkUri,
                    primaryUri = artworkUri,
                    fallbackUri = fallbackArtworkUri,
                )?.let { fallback ->
                    displayedArtworkUri = fallback
                }
            },
            modifier = Modifier.fillMaxSize().alpha(imageAlpha),
        )
        if (!reducedMotion) artworkOverlay?.invoke(this)
    }
}

@Composable
private fun WikiMetadata(
    artist: String,
    album: String,
    duration: String,
) {
    val metadata = listOf(
        "作者" to artist,
        "专辑" to album,
        "时长" to duration,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        metadata.forEachIndexed { index, (label, value) ->
            val shape = when (index) {
                0 -> RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 6.dp,
                    bottomEnd = 6.dp,
                )
                metadata.lastIndex -> RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 6.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                )
                else -> RoundedCornerShape(6.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.width(42.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight(750),
                    lineHeight = 18.6.sp,
                )
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.5.sp,
                )
            }
        }
    }
}

@Composable
private fun WikiCloseButton(
    onDismiss: () -> Unit,
    focusRequester: FocusRequester,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    WikiIconAction(
        enabled = true,
        onClick = onDismiss,
        modifier = modifier.focusRequester(focusRequester),
        icon = {
            Icon(
                imageVector = AppIcons.Clear,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        },
        label = "关闭歌曲详细信息",
        baseColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        reducedMotion = reducedMotion,
    )
}

@Composable
private fun WikiIconAction(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    baseColor: Color,
    hoverColor: Color,
    modifier: Modifier = Modifier,
    disabledAlpha: Float = 1f,
    reducedMotion: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val backgroundAlphaTarget = if (hovered && enabled) 1f else 0f
    val backgroundAlpha by animateFloatAsState(
        targetValue = backgroundAlphaTarget,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(WikiControlBackgroundMillis, easing = WikiCssEase)
        },
        label = "wiki-icon-background",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.90f else 1f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(WikiControlTransformMillis, easing = WikiExpressiveEasing)
        },
        label = "wiki-icon-scale",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else disabledAlpha
            }
            .background(
                color = if (backgroundAlpha > 0f) {
                    Color(
                        red = baseColor.red + (hoverColor.red - baseColor.red) * backgroundAlpha,
                        green = baseColor.green + (hoverColor.green - baseColor.green) * backgroundAlpha,
                        blue = baseColor.blue + (hoverColor.blue - baseColor.blue) * backgroundAlpha,
                        alpha = baseColor.alpha + (hoverColor.alpha - baseColor.alpha) * backgroundAlpha,
                    )
                } else {
                    baseColor
                },
                shape = CircleShape,
            )
            .wikiFocusOutline(focused)
            .hoverable(interactionSource, enabled = enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

private enum class WikiStateKind {
    Empty,
    Error,
}

@Composable
private fun WikiLoadingState(reducedMotion: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 164.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
            .padding(28.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "正在加载音乐百科…"
                stateDescription = "正在加载"
                liveRegion = LiveRegionMode.Polite
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        WikiSpinner(reducedMotion = reducedMotion)
        Text(
            text = "正在加载音乐百科…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            lineHeight = 21.7.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WikiSpinner(reducedMotion: Boolean) {
    val rotation = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "wiki-spinner")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
            label = "wiki-spinner-rotation",
        )
        value
    }
    // The draw scope is not composable, so the role is read here and captured.
    val spinnerColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer { rotationZ = rotation },
    ) {
        val strokeWidth = 3.dp.toPx()
        val inset = strokeWidth / 2f
        drawCircle(
            color = spinnerColor.copy(alpha = if (reducedMotion) 0.35f else 0.20f),
            radius = size.minDimension / 2f - inset,
            style = Stroke(strokeWidth),
        )
        drawArc(
            color = spinnerColor,
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            style = Stroke(strokeWidth, cap = StrokeCap.Butt),
        )
    }
}

@Composable
private fun WikiStatePanel(
    kind: WikiStateKind,
    title: String,
    message: String,
    reducedMotion: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val foreground = if (kind == WikiStateKind.Error) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val background = if (kind == WikiStateKind.Error) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 164.dp)
            .background(background, RoundedCornerShape(28.dp))
            .padding(28.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title，$message"
                stateDescription = title
                when (kind) {
                    WikiStateKind.Empty -> {
                        liveRegion = LiveRegionMode.Polite
                    }
                    WikiStateKind.Error -> {
                        liveRegion = LiveRegionMode.Assertive
                        error(message)
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(2.dp, foreground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (kind == WikiStateKind.Error) "!" else "i",
                color = foreground,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 19.sp,
            )
        }
        Text(
            text = title,
            color = foreground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 23.4.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            modifier = Modifier.widthIn(max = 420.dp).alpha(0.84f),
            color = foreground,
            fontSize = 14.sp,
            lineHeight = 21.7.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            WikiRetryButton(
                label = actionLabel,
                onClick = onAction,
                reducedMotion = reducedMotion,
            )
        }
    }
}

@Composable
private fun WikiRetryButton(
    label: String,
    onClick: () -> Unit,
    reducedMotion: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val brightness by animateFloatAsState(
        targetValue = if (hovered) 1.06f else 1f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(WikiControlBackgroundMillis, easing = WikiCssEase)
        },
        label = "wiki-retry-brightness",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = if (reducedMotion) {
            snap()
        } else {
            tween(WikiControlTransformMillis, easing = WikiExpressiveEasing)
        },
        label = "wiki-retry-scale",
    )
    Box(
        modifier = Modifier
            .widthIn(min = 96.dp)
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(MaterialTheme.colorScheme.primary.withBrightness(brightness), CircleShape)
            .wikiFocusOutline(focused)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary.withBrightness(brightness),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WikiSectionCard(
    section: SongWikiSection,
    index: Int,
    count: Int,
) {
    val shape = when {
        count == 1 -> RoundedCornerShape(28.dp)
        index == 0 -> RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
            bottomStart = 6.dp,
            bottomEnd = 6.dp,
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = 6.dp,
            topEnd = 6.dp,
            bottomStart = 28.dp,
            bottomEnd = 28.dp,
        )
        else -> RoundedCornerShape(6.dp)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = section.title.trim(),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight(750),
            lineHeight = 20.8.sp,
        )
        val values = section.values.map(String::trim).filter(String::isNotEmpty)
        val description = section.description?.trim()?.takeIf(String::isNotEmpty)
        // `.wiki-section h3` has an unconditional 12 px bottom margin, including
        // title-only sections emitted by renderSongWiki().
        Spacer(Modifier.height(12.dp))
        if (values.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                values.forEach { value ->
                    Text(
                        text = value,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.9.sp,
                    )
                }
            }
        }
        description?.let {
            if (values.isNotEmpty()) Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 24.08.sp,
            )
        }
    }
}

/**
 * CSS `.wiki-hero`:
 * radial-gradient(circle at 18% 12%, rgba(128,216,197,.20), transparent 44%)
 * over linear-gradient(135deg, surface-container-highest, surface-container).
 */
internal data class WikiCssLinearGradientAxis(
    val start: Offset,
    val end: Offset,
)

/**
 * CSS Images linear-gradient geometry. CSS angles start at the upward axis and increase
 * clockwise; the gradient line crosses the box center and reaches the two "magic corner"
 * perpendiculars. Its length therefore is `|width * sin(a)| + |height * cos(a)|`.
 */
internal fun cssLinearGradientAxis(
    width: Float,
    height: Float,
    angleDegrees: Float,
): WikiCssLinearGradientAxis {
    val radians = angleDegrees.toDouble() * PI / 180.0
    val directionX = sin(radians).toFloat()
    val directionY = -cos(radians).toFloat()
    val halfLength = (
        abs(width * directionX) +
            abs(height * directionY)
        ) / 2f
    val center = Offset(width / 2f, height / 2f)
    val delta = Offset(directionX * halfLength, directionY * halfLength)
    return WikiCssLinearGradientAxis(
        start = center - delta,
        end = center + delta,
    )
}

private fun Modifier.wikiHeroBackground(
    gradientStart: Color,
    gradientEnd: Color,
    glow: Color,
): Modifier = drawWithCache {
    val center = Offset(size.width * 0.18f, size.height * 0.12f)
    val farthestX = max(center.x, size.width - center.x)
    val farthestY = max(center.y, size.height - center.y)
    val radius = sqrt(farthestX * farthestX + farthestY * farthestY)
    val linearAxis = cssLinearGradientAxis(
        width = size.width,
        height = size.height,
        angleDegrees = 135f,
    )
    val linear = Brush.linearGradient(
        colors = listOf(gradientStart, gradientEnd),
        start = linearAxis.start,
        end = linearAxis.end,
    )
    val radial = Brush.radialGradient(
        colorStops = arrayOf(
            0f to glow.copy(alpha = 0.20f),
            0.44f to Color.Transparent,
            1f to Color.Transparent,
        ),
        center = center,
        radius = radius,
    )
    onDrawBehind {
        drawRect(linear)
        drawRect(radial)
    }
}

private fun Modifier.wikiDialogBorder(
    mobile: Boolean,
    shape: RoundedCornerShape,
): Modifier = if (!mobile) {
    border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.10f),
        shape = shape,
    )
} else {
    drawWithContent {
        drawContent()
        val strokeWidth = 1.dp.toPx()
        val inset = strokeWidth / 2f
        val radius = minOf(
            32.dp.toPx(),
            (size.width / 2f - inset).coerceAtLeast(0f),
            (size.height / 2f - inset).coerceAtLeast(0f),
        )
        val right = (size.width - inset).coerceAtLeast(inset)
        val topBorder = Path().apply {
            moveTo(inset, radius)
            quadraticTo(inset, inset, radius, inset)
            lineTo((right - radius).coerceAtLeast(radius), inset)
            quadraticTo(right, inset, right, radius)
        }
        drawPath(
            path = topBorder,
            color = Color.White.copy(alpha = 0.10f),
            style = Stroke(width = strokeWidth),
        )
    }
}

/**
 * CSS focus ring: `outline: 3px solid rgba(255,255,255,.92); outline-offset: 2px`.
 * A regular Compose border is inset into the control and therefore changes the source geometry.
 */
private fun Modifier.wikiFocusOutline(focused: Boolean): Modifier = if (!focused) {
    this
} else {
    drawWithContent {
        drawContent()
        val strokeWidth = 3.dp.toPx()
        val outlineOffset = 2.dp.toPx()
        val outset = outlineOffset + strokeWidth / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(-outset, -outset),
            size = Size(size.width + outset * 2f, size.height + outset * 2f),
            cornerRadius = CornerRadius(
                x = size.height / 2f + outset,
                y = size.height / 2f + outset,
            ),
            style = Stroke(width = strokeWidth),
        )
    }
}

private fun Color.withBrightness(factor: Float): Color = copy(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
)

private fun formatSongDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
