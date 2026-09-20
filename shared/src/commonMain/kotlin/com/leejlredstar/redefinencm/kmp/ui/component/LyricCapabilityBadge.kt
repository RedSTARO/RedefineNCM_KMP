package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.lyric.LyricSource

internal enum class LyricCapabilityBadgeTone {
    NEUTRAL,
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

internal data class LyricCapabilityBadgeSpec(
    val visibleText: String,
    val levelLabel: String,
    val contentDescription: String,
    val tone: LyricCapabilityBadgeTone,
    /**
     * Resting silhouette for this level.
     *
     * The shape is the point of the badge: capability rises from a plain square through a circle
     * to increasingly elaborate scalloped shapes, so a glance at the outline says how good the
     * lyrics are before the colour or the dropdown does. Shape is decoration in most of this app;
     * here it carries the meaning, which is why the tone/level/shape triple moves together.
     *
     * Never the sole signal — [contentDescription] and the dropdown still state the level in
     * words for screen readers and for anyone who cannot distinguish the outlines.
     */
    val shape: () -> RoundedPolygon,
)

internal fun lyricCapabilityBadgeSpec(
    level: LyricCapabilityLevel,
): LyricCapabilityBadgeSpec = when (level) {
    LyricCapabilityLevel.UNSYNCED -> LyricCapabilityBadgeSpec(
        visibleText = "词",
        levelLabel = "无时间戳",
        contentDescription = "歌词等级：无时间戳",
        tone = LyricCapabilityBadgeTone.NEUTRAL,
        shape = { MaterialShapes.Square },
    )
    LyricCapabilityLevel.LINE_SYNCED -> LyricCapabilityBadgeSpec(
        visibleText = "词",
        levelLabel = "普通逐行",
        contentDescription = "歌词等级：普通逐行",
        tone = LyricCapabilityBadgeTone.PRIMARY,
        shape = { MaterialShapes.Circle },
    )
    LyricCapabilityLevel.NCM_YRC -> LyricCapabilityBadgeSpec(
        visibleText = "词",
        levelLabel = "NCM YRC 逐字",
        contentDescription = "歌词等级：NCM YRC",
        tone = LyricCapabilityBadgeTone.SECONDARY,
        shape = { MaterialShapes.Cookie6Sided },
    )
    LyricCapabilityLevel.TTML_FULL -> LyricCapabilityBadgeSpec(
        visibleText = "词",
        levelLabel = "TTML 完全支持",
        contentDescription = "歌词等级：TTML 完全支持",
        tone = LyricCapabilityBadgeTone.TERTIARY,
        shape = { MaterialShapes.Clover4Leaf },
    )
}

internal fun lyricSourceDisplayName(
    source: LyricSource?,
    endpoint: String,
): String {
    val provider = when (source) {
        LyricSource.AMLL_TTML -> "AMLL TTML"
        LyricSource.NCM_BACKEND -> "网易云歌词后端"
        null -> "未知"
    }
    return if (endpoint == "local-sidecar") "$provider · 本地歌词文件" else provider
}

@Composable
internal fun LyricCapabilityBadge(
    level: LyricCapabilityLevel,
    source: LyricSource?,
    endpoint: String,
    detailsExpanded: Boolean,
    onDetailsExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = lyricCapabilityBadgeSpec(level)
    val sourceLabel = lyricSourceDisplayName(source, endpoint)
    val (containerColor, contentColor) = lyricCapabilityBadgeColors(spec.tone)
    val interactionSource = remember { MutableInteractionSource() }
    val morphProgress by rememberPressMorphProgress(interactionSource)
    // Morphs toward a single open shape from whichever silhouette this level rests at, so the
    // press feedback is uniform while the resting outline still identifies the level.
    val badgeShape = rememberMorphShape(
        start = spec.shape(),
        end = MaterialShapes.Cookie12Sided,
        progress = morphProgress,
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "查看歌词信息",
                interactionSource = interactionSource,
                indication = null,
                onClick = { onDetailsExpandedChange(!detailsExpanded) },
            )
            .semantics {
                contentDescription =
                    "${spec.contentDescription}，来源：$sourceLabel"
            },
        contentAlignment = Alignment.Center,
    ) {
        // Square rather than the old 40x32 pill: MaterialShapes are normalised into a unit box,
        // so a non-square badge would stretch every silhouette out of recognisable proportion.
        Surface(
            modifier = Modifier.size(36.dp),
            shape = badgeShape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = spec.visibleText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        DropdownMenu(
            expanded = detailsExpanded,
            onDismissRequest = { onDetailsExpandedChange(false) },
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 220.dp, max = 300.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "歌词信息",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LyricDetailRow(label = "等级", value = spec.levelLabel)
                LyricDetailRow(label = "来源", value = sourceLabel)
            }
        }
    }
}

@Composable
private fun LyricDetailRow(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
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
