package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState

@Composable
internal fun BoxScope.LyricStateOverlay(
    state: LyricUiState,
    onRetry: () -> Unit,
) {
    val statePalette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)
    val stateModifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 32.dp)
    when (state) {
        is LyricUiState.Idle -> ExpressiveStatePanel(
            title = "还没有播放音乐",
            message = "选择一首歌曲后，歌词会显示在这里。",
            icon = AppIcons.GraphicEq,
            accentPalette = statePalette,
            modifier = stateModifier,
        )
        is LyricUiState.Loading -> ExpressiveLoadingState(
            label = "正在加载歌词…",
            accentColor = statePalette.accent,
            modifier = stateModifier,
        )
        is LyricUiState.Empty -> ExpressiveStatePanel(
            title = "暂无歌词",
            message = "这首歌曲暂时没有可用歌词。",
            icon = AppIcons.GraphicEq,
            accentPalette = statePalette,
            modifier = stateModifier,
        )
        is LyricUiState.Error -> ExpressiveStatePanel(
            title = "歌词加载失败",
            message = state.message,
            icon = AppIcons.Refresh,
            tone = ExpressiveStateTone.Error,
            accentPalette = statePalette,
            actionLabel = "重试",
            onAction = onRetry,
            modifier = stateModifier,
        )
        is LyricUiState.Content -> Unit
    }
}
