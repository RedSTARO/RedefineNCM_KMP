package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLoadingState
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStatePanel
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveStateTone
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.ui.theme.DarkColors

@Composable
internal fun BoxScope.LyricStateOverlay(
    state: LyricUiState,
    hasMedia: Boolean,
    isPlayerRestoring: Boolean,
    onRetry: () -> Unit,
) {
    // The lyric page is dark whatever the app theme is; its panels follow the page.
    MaterialTheme(colorScheme = DarkColors) {
        LyricStateOverlayPanels(state, hasMedia, isPlayerRestoring, onRetry)
    }
}

@Composable
private fun BoxScope.LyricStateOverlayPanels(
    state: LyricUiState,
    hasMedia: Boolean,
    isPlayerRestoring: Boolean,
    onRetry: () -> Unit,
) {
    val statePalette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)
    val stateModifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 32.dp)
    when (state) {
        is LyricUiState.Idle -> if (hasMedia || isPlayerRestoring) {
            ExpressiveLoadingState(
                label = if (hasMedia) strings.restoringLyrics else strings.restoringPlayback,
                accentColor = statePalette.accent,
                modifier = stateModifier,
            )
        } else {
            ExpressiveStatePanel(
                title = strings.nothingPlayingYet,
                message = strings.lyricsAppearHere,
                icon = AppIcons.MusicNote,
                accentPalette = statePalette,
                modifier = stateModifier,
            )
        }
        is LyricUiState.Loading -> ExpressiveLoadingState(
            label = strings.loadingLyrics,
            accentColor = statePalette.accent,
            modifier = stateModifier,
        )
        is LyricUiState.Empty -> ExpressiveStatePanel(
            title = if (state.capabilityLevel == LyricCapabilityLevel.UNSYNCED) {
                strings.lyricsUntimedTitle
            } else {
                strings.noLyrics
            },
            message = if (state.capabilityLevel == LyricCapabilityLevel.UNSYNCED) {
                strings.lyricsUntimedMessage
            } else {
                strings.songHasNoLyrics
            },
            icon = AppIcons.FormatQuote,
            accentPalette = statePalette,
            modifier = stateModifier,
        )
        is LyricUiState.Error -> ExpressiveStatePanel(
            title = strings.lyricsLoadFailed,
            message = state.message.text,
            icon = AppIcons.Refresh,
            tone = ExpressiveStateTone.Error,
            accentPalette = statePalette,
            actionLabel = strings.retry,
            onAction = onRetry,
            modifier = stateModifier,
        )
        // Not an error: nothing failed, so there is nothing to retry.
        is LyricUiState.Unsupported -> ExpressiveStatePanel(
            title = strings.lyricsUnsupported,
            message = state.message.text,
            icon = AppIcons.FormatQuote,
            accentPalette = statePalette,
            modifier = stateModifier,
        )
        is LyricUiState.Content -> Unit
    }
}
