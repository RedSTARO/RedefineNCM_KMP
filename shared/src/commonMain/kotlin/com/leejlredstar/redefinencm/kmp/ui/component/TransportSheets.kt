package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel

/**
 * Which of the two transport sheets is open.
 *
 * Every surface that can reach the queue and the comments held its own pair of booleans and its
 * own pair of `if (show…) { …BottomSheet(…) }` blocks. Four surfaces meant four copies that had
 * to be kept in step by hand, and the newest one was written by copying the previous.
 */
@Stable
internal class TransportSheetsState {
    var showQueue by mutableStateOf(false)
        private set
    var showComments by mutableStateOf(false)
        private set

    /** Whether either sheet is up, which surfaces use to suspend auto-hide. */
    val anyOpen: Boolean get() = showQueue || showComments

    /** Opening one closes the other: the two sheets occupy the same space. */
    fun openQueue() {
        showComments = false
        showQueue = true
    }

    fun openComments() {
        showQueue = false
        showComments = true
    }

    fun dismiss() {
        showQueue = false
        showComments = false
    }
}

@Composable
internal fun rememberTransportSheetsState(): TransportSheetsState =
    remember { TransportSheetsState() }

/**
 * The queue and comment sheets for a transport surface.
 *
 * Place at the end of the surface's root container so the sheets draw over it.
 *
 * @param onSeekClick runs in addition to the queue jump, for surfaces that also have to bring
 *   themselves back into view.
 */
@Composable
internal fun TransportSheets(
    state: TransportSheetsState,
    nowPlaying: NowPlayingUiState,
    accentPalette: ContentAccentPalette,
    viewModel: NowPlayingViewModel,
    onSeekClick: ((Int) -> Unit)? = null,
) {
    // Comments load when the sheet opens, and again when the sheet is open across a track
    // change. Each surface used to run this effect itself.
    LaunchedEffect(state.showComments, nowPlaying.media?.id) {
        if (state.showComments) viewModel.getComments()
    }
    if (state.showQueue) {
        QueueBottomSheet(
            playlist = nowPlaying.playList,
            currentIndex = nowPlaying.currentIndex,
            accentPalette = accentPalette,
            onDismiss = state::dismiss,
            onSeekClick = onSeekClick ?: viewModel::onSeekClick,
        )
    }
    if (state.showComments) {
        CommentBottomSheet(
            comments = nowPlaying.displayComments,
            hasLoadedData = nowPlaying.hasLoadedComments,
            accentPalette = accentPalette,
            onDismiss = state::dismiss,
            isLoading = nowPlaying.commentsLoading,
            isFromCache = nowPlaying.commentsFromCache,
            errorMessage = nowPlaying.commentsLoadError,
            onRetry = viewModel::getComments,
        )
    }
}
