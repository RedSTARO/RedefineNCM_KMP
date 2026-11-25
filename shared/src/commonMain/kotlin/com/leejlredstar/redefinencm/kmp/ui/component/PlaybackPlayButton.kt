package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons

/** One play/pause silhouette across the hero, the lyric island, and the persistent players. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaybackPlayButton(
    isPlaying: Boolean,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(ExpressiveLayout.MinimumTouchTarget),
    iconSize: Dp = IconButtonDefaults.smallIconSize,
    shapes: IconToggleButtonShapes = IconButtonDefaults.toggleableShapes(),
) {
    FilledIconToggleButton(
        checked = isPlaying,
        onCheckedChange = { onClick() },
        enabled = enabled,
        modifier = modifier,
        shapes = shapes,
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            checkedContainerColor = containerColor,
            checkedContentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.12f),
            disabledContentColor = contentColor.copy(alpha = 0.38f),
        ),
    ) {
        Icon(
            imageVector = if (isPlaying) AppIcons.Pause else AppIcons.PlayArrow,
            contentDescription = if (isPlaying) strings.pause else strings.play,
            modifier = Modifier.size(iconSize),
        )
    }
}
