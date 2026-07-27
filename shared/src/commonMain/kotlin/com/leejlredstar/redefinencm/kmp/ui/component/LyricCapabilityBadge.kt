package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel

internal enum class LyricCapabilityBadgeTone {
    NEUTRAL,
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

internal data class LyricCapabilityBadgeSpec(
    val visibleText: String,
    val contentDescription: String,
    val tone: LyricCapabilityBadgeTone,
)

internal fun lyricCapabilityBadgeSpec(
    level: LyricCapabilityLevel,
): LyricCapabilityBadgeSpec = when (level) {
    LyricCapabilityLevel.UNSYNCED -> LyricCapabilityBadgeSpec(
        visibleText = "词",
        contentDescription = "歌词等级：无时间戳",
        tone = LyricCapabilityBadgeTone.NEUTRAL,
    )
    LyricCapabilityLevel.LINE_SYNCED -> LyricCapabilityBadgeSpec(
        visibleText = "词",
        contentDescription = "歌词等级：普通逐行",
        tone = LyricCapabilityBadgeTone.PRIMARY,
    )
    LyricCapabilityLevel.NCM_YRC -> LyricCapabilityBadgeSpec(
        visibleText = "词",
        contentDescription = "歌词等级：NCM YRC",
        tone = LyricCapabilityBadgeTone.SECONDARY,
    )
    LyricCapabilityLevel.TTML_FULL -> LyricCapabilityBadgeSpec(
        visibleText = "词",
        contentDescription = "歌词等级：TTML 完全支持",
        tone = LyricCapabilityBadgeTone.TERTIARY,
    )
}

@Composable
internal fun LyricCapabilityBadge(
    level: LyricCapabilityLevel,
    modifier: Modifier = Modifier,
) {
    val spec = lyricCapabilityBadgeSpec(level)
    val (containerColor, contentColor) = lyricCapabilityBadgeColors(spec.tone)
    Surface(
        modifier = modifier
            .size(28.dp)
            .clearAndSetSemantics {
                contentDescription = spec.contentDescription
            },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = spec.visibleText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun lyricCapabilityBadgeColors(
    tone: LyricCapabilityBadgeTone,
): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (tone) {
        LyricCapabilityBadgeTone.NEUTRAL -> colors.surfaceVariant to colors.onSurfaceVariant
        LyricCapabilityBadgeTone.PRIMARY -> colors.primaryContainer to colors.onPrimaryContainer
        LyricCapabilityBadgeTone.SECONDARY -> colors.secondaryContainer to colors.onSecondaryContainer
        LyricCapabilityBadgeTone.TERTIARY -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
}
