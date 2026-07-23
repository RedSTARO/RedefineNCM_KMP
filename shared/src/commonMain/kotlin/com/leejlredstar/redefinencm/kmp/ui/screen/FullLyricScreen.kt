package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.leejlredstar.redefinencm.kmp.ui.component.AutoHideMiniPlayerController
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsButton
import com.leejlredstar.redefinencm.kmp.ui.component.SongWikiDetailsSheet
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.rememberThemeColorExtractor
import com.leejlredstar.redefinencm.kmp.util.LyricParser
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
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
    val lyricUiState by viewModel.lyricUiState.collectAsState()
    val lyricIndex by viewModel.lyricIndex.collectAsState()
    val wordLyricLines by viewModel.wordLyricLines.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()
    val songWikiUiState by viewModel.songWikiUiState.collectAsState()
    var showSongWikiDetails by remember { mutableStateOf(false) }

    val defaultHeroColor = MaterialTheme.colorScheme.primaryContainer
    var themeColor by remember(metadata?.artworkUri, defaultHeroColor) {
        mutableStateOf(defaultHeroColor)
    }
    val extractThemeColor = rememberThemeColorExtractor(metadata?.artworkUri) { themeColor = it }
    val heroColor by animateColorAsState(themeColor, spring(), label = "hero")
    val accentPalette = contentAccentPalette(heroColor)

    val lyricEntries = remember(lyricMap) { lyricMap.entries.toList() }

    // ── Scroll state ──
    val listState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }
    var resumeJob by remember { mutableStateOf<Job?>(null) }
    var programmaticScroll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(metadata?.id) {
        showSongWikiDetails = false
    }

    // Auto-centre current line
    LaunchedEffect(lyricIndex, isUserScrolling) {
        if (!isUserScrolling && lyricIndex in lyricEntries.indices && lyricEntries.isNotEmpty()) {
            programmaticScroll = true
            try {
                listState.animateScrollToItem(
                    // The first LazyColumn item is the leading spacer.
                    index = lyricIndex + 1,
                    // A negative offset places the item below the viewport start.
                    scrollOffset = -(listState.layoutInfo.viewportSize.height / 2).coerceAtLeast(0),
                )
            } finally {
                // A newer lyric line cancels this LaunchedEffect while an animation is active.
                // Always clear the guard or all later user scrolls would be misclassified.
                programmaticScroll = false
            }
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentPalette.pageStart,
                        accentPalette.pageMiddle,
                        accentPalette.pageEnd,
                    ),
                ),
            )
            
    ) {
        val compactHeight = maxHeight < 600.dp
        val lyricTopPadding = if (compactHeight) 72.dp else 100.dp
        val lyricBottomPadding = if (compactHeight) 72.dp else 104.dp
        val lyricEdgeSpacer = (maxHeight * 0.38f).coerceIn(120.dp, 300.dp)
        val featherHeight = if (compactHeight) 48.dp else 80.dp

        // ── Album art background ──
        AsyncImage(
            model = metadata?.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state -> extractThemeColor(state.result.image) },
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

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Surface(
                    shape = CircleShape,
                    color = accentPalette.quietContainer.copy(alpha = 0.70f),
                    contentColor = accentPalette.onQuietContainer,
                ) {
                    Icon(
                        AppIcons.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            SongWikiDetailsButton(
                enabled = metadata != null,
                onClick = {
                    showSongWikiDetails = true
                    viewModel.getSongWikiSummary()
                },
                tint = accentPalette.onQuietContainer,
            )
        }

        // ── Scrollable lyric list ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = lyricTopPadding, bottom = lyricBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(lyricEdgeSpacer)) }

            when {
                metadata == null -> item(key = "no-media") {
                    ExpressiveStatePanel(
                        title = "还没有播放音乐",
                        message = "选择一首歌曲后，歌词会显示在这里。",
                        icon = AppIcons.GraphicEq,
                        accentPalette = accentPalette,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                lyricUiState is LyricUiState.Loading || lyricUiState is LyricUiState.Idle -> item(
                    key = "lyric-loading",
                ) {
                    ExpressiveLoadingState(
                        label = "正在加载歌词…",
                        accentColor = accentPalette.accent,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                lyricUiState is LyricUiState.Error -> item(key = "lyric-error") {
                    ExpressiveStatePanel(
                        title = "歌词加载失败",
                        message = (lyricUiState as LyricUiState.Error).message,
                        icon = AppIcons.Refresh,
                        tone = ExpressiveStateTone.Error,
                        accentPalette = accentPalette,
                        actionLabel = "重试",
                        onAction = viewModel::retryLyrics,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                lyricUiState is LyricUiState.Empty -> item(key = "lyric-empty") {
                    ExpressiveStatePanel(
                        title = "暂无歌词",
                        message = "这首歌曲暂时没有可用歌词。",
                        icon = AppIcons.GraphicEq,
                        accentPalette = accentPalette,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                else -> itemsIndexed(
                    items = lyricEntries,
                    key = { idx, entry -> "${entry.key}:$idx" },
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
                    val wordLine = if (isCurrent) findWordLine(entry.key, wordLyricLines) else null
                    val seekToLine: (() -> Unit)? = entry.key?.let { timestamp ->
                        {
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
                            highlightColor = accentPalette.accent,
                            isCurrent = isCurrent,
                            onClick = seekToLine,
                        )
                    } else {
                        LyricKaraokeLine(
                            text = entry.value?.ifBlank { "· · ·" } ?: "· · ·",
                            alpha = alpha,
                            fontSize = fontSize,
                            isCurrent = isCurrent,
                            onClick = seekToLine,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(lyricEdgeSpacer)) }
        }

        // ── Gradient feather edges ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(featherHeight)
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
                .height(featherHeight)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                    ),
                ),
        )

        AutoHideMiniPlayerController(modifier = Modifier.fillMaxSize())
    }

    SongWikiDetailsSheet(
        visible = showSongWikiDetails,
        songTitle = metadata?.title,
        songArtist = metadata?.artist,
        albumTitle = metadata?.albumTitle,
        artworkUri = metadata?.artworkUri,
        durationMs = metadata?.duration,
        state = songWikiUiState,
        onDismiss = { showSongWikiDetails = false },
        onRetry = viewModel::getSongWikiSummary,
    )
}

// ── Helpers ──

private fun findWordLine(
    timestamp: Long?,
    lines: List<LyricParser.WordLine>,
): LyricParser.WordLine? {
    if (timestamp == null || lines.isEmpty()) return null
    var nearest: LyricParser.WordLine? = null
    var nearestDistance = Long.MAX_VALUE
    lines.forEach { line ->
        val distance = kotlin.math.abs(line.startTimeMs - timestamp)
        if (distance == 0L) return line
        if (distance < nearestDistance) {
            nearest = line
            nearestDistance = distance
        }
    }
    return nearest?.takeIf { nearestDistance <= 150L }
}

@Composable
private fun WordLyricKaraokeLine(
    line: LyricParser.WordLine,
    positionMs: Long,
    alpha: Float,
    fontSize: TextUnit,
    highlightColor: Color,
    isCurrent: Boolean,
    onClick: (() -> Unit)?,
) {
    val dimColor = Color(0xFFC8C8C8)
    val brightColor = lerp(dimColor, Color.White, alpha.coerceIn(0f, 1f))
    val annotatedText = buildAnnotatedString {
        line.words.forEach { word ->
            val current = positionMs in word.startTimeMs until word.endTimeMs.coerceAtLeast(word.startTimeMs + 1L)
            withStyle(
                SpanStyle(
                    color = if (current) brightColor else dimColor,
                    fontWeight = if (current) FontWeight.ExtraBold else FontWeight.Normal,
                    shadow = if (current) {
                        Shadow(
                            color = highlightColor.copy(alpha = 0.92f * alpha),
                            offset = Offset.Zero,
                            blurRadius = 18f,
                        )
                    } else {
                        null
                    },
                ),
            ) {
                append(word.text)
            }
        }
    }

    val seekModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = "跳转到这句歌词",
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .widthIn(max = 840.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(seekModifier)
            .semantics {
                selected = isCurrent
                stateDescription = when {
                    onClick == null -> "歌词间隔"
                    isCurrent -> "当前歌词"
                    else -> "歌词"
                }
            }
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
    alpha: Float,
    fontSize: TextUnit,
    isCurrent: Boolean,
    onClick: (() -> Unit)?,
) {
    val lineColor = if (isCurrent) {
        Color.White
    } else {
        lerp(Color(0xFFC8C8C8), Color.White, alpha.coerceIn(0f, 1f))
    }

    val seekModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = "跳转到这句歌词",
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .widthIn(max = 840.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(seekModifier)
            .semantics {
                selected = isCurrent
                stateDescription = when {
                    onClick == null -> "歌词间隔"
                    isCurrent -> "当前歌词"
                    else -> "歌词"
                }
            }
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = lineColor,
            fontSize = fontSize,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
