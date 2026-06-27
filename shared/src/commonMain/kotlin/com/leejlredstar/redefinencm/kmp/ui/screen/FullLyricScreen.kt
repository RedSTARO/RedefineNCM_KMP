package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Full-screen NetEase-Cloud-Music-style scrolling karaoke lyric display.
 *
 * Features:
 * - Scrollable lyric list; auto-centres current line
 * - Karaoke word-by-word highlight on the current line
 * - Tap any lyric line to seek the player to that timestamp
 * - Manual scrolling pauses auto-centre for 3 s, then resumes
 * - Gradient feather edges at top and bottom
 */
@Composable
fun FullLyricScreen(
    onBack: () -> Unit = {},
    viewModel: NowPlayingViewModel = koinInject(),
) {
    val lyricMap by viewModel.lyricMap.collectAsState()
    val lyricIndex by viewModel.lyricIndex.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val songLength by viewModel.songLength.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()

    val defaultHeroColor = MaterialTheme.colorScheme.primaryContainer
    var themeColor by remember { mutableStateOf(defaultHeroColor) }
    val heroColor by animateColorAsState(
        targetValue = themeColor,
        animationSpec = spring(),
        label = "fullLyricHero",
    )

    // ── Lyric data as indexed list ──
    val lyricEntries = remember(lyricMap) { lyricMap.entries.toList() }
    val timestamps = remember(lyricMap) { lyricEntries.map { it.key } }

    // ── Karaoke progress for the current line ──
    val karaokeProgress = computeKaraokeProgress(lyricIndex, timestamps, currentPosition, songLength)

    // ── Scroll state with manual-scroll detection ──
    val listState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-centre the current lyric line
    LaunchedEffect(lyricIndex, isUserScrolling) {
        if (!isUserScrolling && lyricIndex in lyricEntries.indices) {
            // Small extra delay so Compose has time to lay out after a recomposition
            if (lyricEntries.isNotEmpty()) {
                listState.animateScrollToItem(
                    index = lyricIndex,
                    scrollOffset = -listState.layoutInfo.viewportSize.height / 3,
                )
            }
        }
    }

    // Detect manual scrolling — pause auto-centre
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) {
                    isUserScrolling = true
                } else if (isUserScrolling) {
                    // Wait 3 s after the user stops dragging, then resume auto-centre
                    scope.launch {
                        delay(3_000)
                        isUserScrolling = false
                    }
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(heroColor, heroColor.copy(alpha = 0.3f), Color(0xFF0A0A0A)),
                ),
            ),
    ) {
        // ── Album art background ──
        AsyncImage(
            model = metadata?.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.70f),
                            Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
                ),
        )

        // ── Back button ──
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        // ── Scrollable lyric list ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top spacer — lets the first lyric line scroll into the centre
            item { Spacer(Modifier.height(250.dp)) }

            itemsIndexed(
                items = lyricEntries,
                key = { idx, _ -> idx },
            ) { idx, entry ->
                val isCurrent = idx == lyricIndex
                val dist = kotlin.math.abs(idx - lyricIndex)

                val alpha = when {
                    dist == 0 -> 1f
                    dist <= 2 -> 1f - dist * 0.18f
                    else -> 0.40f
                }
                val fontSize = when {
                    dist == 0 -> 20.sp
                    dist == 1 -> 17.sp
                    else -> 15.sp
                }
                val progress = when {
                    isCurrent -> karaokeProgress
                    idx < lyricIndex -> 1f // already sung
                    else -> 0f // not yet sung
                }

                LyricKaraokeLine(
                    text = entry.value?.ifBlank { "· · ·" } ?: "· · ·",
                    isCurrent = isCurrent,
                    progress = progress,
                    alpha = alpha,
                    fontSize = fontSize,
                    onClick = {
                        entry.key?.let { timestamp ->
                            viewModel.onPositionSeekClick(timestamp)
                            // Resume auto-centre immediately after user-initiated seek
                            isUserScrolling = false
                        }
                    },
                )
            }

            // Bottom spacer
            item { Spacer(Modifier.height(250.dp)) }
        }

        // ── Gradient feather edges ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                    ),
                ),
        )

        // ── Song info at bottom ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = metadata?.title?.ifBlank { "Unknown" } ?: "Unknown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = metadata?.artist?.ifBlank { "Unknown" } ?: "Unknown",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Helpers ──

private fun computeKaraokeProgress(
    idx: Int,
    timestamps: List<Long?>,
    positionMs: Long,
    songLengthMs: Long,
): Float {
    val lineTimestamps = timestamps.mapNotNull { it }
    if (idx !in lineTimestamps.indices) return 0f
    val cur = lineTimestamps[idx]
    val next = lineTimestamps.getOrNull(idx + 1) ?: songLengthMs.coerceAtLeast(cur)
    if (next <= cur) return 1f
    return ((positionMs - cur).toFloat() / (next - cur)).coerceIn(0f, 1f)
}

/**
 * A single lyric line with karaoke progress highlight.
 * Tapping the line seeks to the associated timestamp.
 */
@Composable
private fun LyricKaraokeLine(
    text: String,
    isCurrent: Boolean,
    progress: Float,
    alpha: Float,
    fontSize: TextUnit,
    onClick: () -> Unit,
) {
    val dimColor = Color.White.copy(alpha = 0.45f * alpha)
    val brightColor = Color.White.copy(alpha = alpha)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Base layer: full-dim text
        Text(
            text = text,
            color = dimColor,
            fontSize = fontSize,
            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        // Karaoke overlay
        if (isCurrent && progress in 0f..1f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = brightColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
