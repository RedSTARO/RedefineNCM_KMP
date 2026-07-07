package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.util.LyricParser
import com.leejlredstar.redefinencm.kmp.util.themeColorFromCoilImage
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * NetEase-Cloud-Music-style scrolling lyric display.
 *
 * • A fixed cursor line is drawn on the main canvas via [drawWithContent].
 * • Lyrics scroll past the cursor; the current line auto-lands on it.
 * • Tapping a line seeks to that timestamp and snaps it to the cursor.
 * • Manual scrolling pauses auto-cursor for 3 s, then snaps back.
 */
@Composable
fun FullLyricScreen(
    onBack: () -> Unit = {},
    viewModel: NowPlayingViewModel = koinInject(),
) {
    val lyricMap by viewModel.lyricMap.collectAsState()
    val lyricIndex by viewModel.lyricIndex.collectAsState()
    val wordLyricLines by viewModel.wordLyricLines.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val songLength by viewModel.songLength.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()

    val defaultHeroColor = MaterialTheme.colorScheme.primaryContainer
    var themeColor by remember { mutableStateOf(defaultHeroColor) }
    val heroColor by animateColorAsState(themeColor, spring(), label = "hero")

    val lyricEntries = remember(lyricMap) { lyricMap.entries.toList() }
    val timestamps = remember(lyricMap) { lyricEntries.map { it.key } }
    val karaokeProgress = computeKaraokeProgress(lyricIndex, timestamps, currentPosition, songLength)

    // ── Scroll state ──
    val listState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Auto-centre current line
    LaunchedEffect(lyricIndex, isUserScrolling) {
        if (!isUserScrolling && lyricIndex in lyricEntries.indices && lyricEntries.isNotEmpty()) {
            programmaticScroll = true
            listState.animateScrollToItem(
                index = lyricIndex,
                scrollOffset = listState.layoutInfo.viewportSize.height / 2,
            )
            programmaticScroll = false
        }
    }

    // Detect manual scroll
    val currentFirst = listState.firstVisibleItemIndex
    var lastKnownFirst by remember { mutableStateOf(currentFirst) }
    LaunchedEffect(currentFirst) {
        if (currentFirst != lastKnownFirst && !programmaticScroll && lyricEntries.isNotEmpty()) {
            isUserScrolling = true
            resumeJob?.cancel()
            resumeJob = scope.launch {
                delay(3_000)
                isUserScrolling = false
            }
        }
        lastKnownFirst = currentFirst
    }

    // ── Main container with cursor drawn on top via drawWithContent ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(heroColor, heroColor.copy(alpha = 0.3f), Color(0xFF0A0A0A)),
                ),
            )
            
    ) {
        // ── Album art background ──
        AsyncImage(
            model = metadata?.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                themeColorFromCoilImage(state.result.image)?.let { themeColor = Color(it) }
            },
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
                    AppIcons.ArrowBack,
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
            item { Spacer(Modifier.height(250.dp)) }

            itemsIndexed(lyricEntries, key = { idx, _ -> idx }) { idx, entry ->
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
                    idx < lyricIndex -> 1f
                    else -> 0f
                }

                val wordLine = if (isCurrent) findWordLine(entry.key, wordLyricLines) else null
                val seekToLine: () -> Unit = {
                    entry.key?.let { timestamp ->
                        viewModel.onPositionSeekClick(timestamp)
                        resumeJob?.cancel()
                        isUserScrolling = false
                    }
                }
                if (wordLine != null) {
                    WordLyricKaraokeLine(
                        line = wordLine,
                        positionMs = currentPosition,
                        alpha = alpha,
                        fontSize = fontSize,
                        onClick = seekToLine,
                    )
                } else {
                    LyricKaraokeLine(
                        text = entry.value?.ifBlank { "· · ·" } ?: "· · ·",
                        isCurrent = isCurrent,
                        progress = progress,
                        alpha = alpha,
                        fontSize = fontSize,
                        onClick = seekToLine,
                    )
                }
            }

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

private fun findWordLine(
    timestamp: Long?,
    lines: List<LyricParser.WordLine>,
): LyricParser.WordLine? {
    if (timestamp == null || lines.isEmpty()) return null
    return lines.firstOrNull { it.startTimeMs == timestamp }
        ?: lines
            .minByOrNull { kotlin.math.abs(it.startTimeMs - timestamp) }
            ?.takeIf { kotlin.math.abs(it.startTimeMs - timestamp) <= 150L }
}

@Composable
private fun WordLyricKaraokeLine(
    line: LyricParser.WordLine,
    positionMs: Long,
    alpha: Float,
    fontSize: TextUnit,
    onClick: () -> Unit,
) {
    val dimColor = Color.White.copy(alpha = 0.45f * alpha)
    val brightColor = Color.White.copy(alpha = alpha)
    val annotatedText = buildAnnotatedString {
        line.words.forEach { word ->
            val active = positionMs >= word.startTimeMs
            val current = positionMs in word.startTimeMs until word.endTimeMs.coerceAtLeast(word.startTimeMs + 1L)
            withStyle(
                SpanStyle(
                    color = if (active) brightColor else dimColor,
                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal,
                    shadow = when {
                        current -> Shadow(
                            color = Color(0xFFE9DDFF).copy(alpha = 0.92f * alpha),
                            offset = Offset.Zero,
                            blurRadius = 18f,
                        )
                        active -> Shadow(
                            color = Color(0xFFBFA7FF).copy(alpha = 0.58f * alpha),
                            offset = Offset.Zero,
                            blurRadius = 10f,
                        )
                        else -> null
                    },
                ),
            ) {
                append(word.text)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = annotatedText,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
    }
}

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
            .pointerInput(onClick) { detectTapGestures { onClick() } }
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = dimColor,
            fontSize = fontSize,
            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
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
