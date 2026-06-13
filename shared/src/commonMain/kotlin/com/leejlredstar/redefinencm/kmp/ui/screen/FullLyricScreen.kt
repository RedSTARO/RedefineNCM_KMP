package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.koin.compose.koinInject

/**
 * Full-screen Apple Music-style karaoke lyric display.
 *
 * Opens when the user taps the lyric section on the NowPlayingScreen.
 * Features:
 * - Karaoke word-by-word highlight sweeping left-to-right on the current line
 * - Past / future lines fade in opacity the farther they are from the current line
 * - Current line is center-aligned vertically
 * - Gradient feather (scrim) at top and bottom edges
 * - Album art as background with dark overlay
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

    val lyrics = lyricMap.values.toList()
    val timestamps = lyricMap.keys.toList()
    val lineTimestamps = timestamps.mapNotNull { it }

    // Karaoke progress for the current line (0..1)
    val karaokeProgress = if (lyricIndex in lineTimestamps.indices) {
        val currentTime = lineTimestamps[lyricIndex]
        val nextTime = lineTimestamps.getOrNull(lyricIndex + 1) ?: songLength.coerceAtLeast(currentTime)
        if (nextTime > currentTime) {
            ((currentPosition - currentTime).toFloat() / (nextTime - currentTime)).coerceIn(0f, 1f)
        } else 1f
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(heroColor, heroColor.copy(alpha = 0.3f), Color(0xFF0A0A0A)),
                ),
            ),
    ) {
        // Album art background with dark scrim
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

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        // Lyrics — fixed 11-slot window centered vertically
        val windowSize = 5

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            (lyricIndex - windowSize..lyricIndex + windowSize).forEach { idx ->
                if (idx in lyrics.indices) {
                    val dist = kotlin.math.abs(idx - lyricIndex)
                    val alpha = when {
                        dist == 0 -> 1f
                        dist <= 2 -> 1f - dist * 0.18f
                        else -> 0.35f
                    }
                    val fontSize = when {
                        dist == 0 -> 20.sp
                        dist == 1 -> 17.sp
                        else -> 15.sp
                    }
                    val isCurrent = idx == lyricIndex

                    LyricKaraokeLine(
                        text = lyrics[idx]?.ifBlank { "· · ·" } ?: "· · ·",
                        isCurrent = isCurrent,
                        progress = if (isCurrent) karaokeProgress else if (idx < lyricIndex) 1f else 0f,
                        alpha = alpha,
                        fontSize = fontSize,
                    )
                } else {
                    // Empty slot to keep centering
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // Gradient feather edges
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent,
                        ),
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
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f),
                        ),
                    ),
                ),
        )

        // Song info at bottom
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

/**
 * A single line of lyric text with karaoke progress highlight.
 *
 * The current line renders twice — a dimmed full-width layer and a bright overlay
 * clipped to [progress] width, creating a left-to-right sweep effect.
 */
@Composable
private fun LyricKaraokeLine(
    text: String,
    isCurrent: Boolean,
    progress: Float,
    alpha: Float,
    fontSize: TextUnit,
) {
    val dimColor = Color.White.copy(alpha = 0.45f * alpha)
    val brightColor = Color.White.copy(alpha = alpha)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
        // Karaoke overlay layer: the Box width is constrained to [progress] fraction,
        // so the child Text naturally renders only within that span — no explicit clip needed.
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
