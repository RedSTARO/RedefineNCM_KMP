package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusicComments
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
            shuffleEnabled = nowPlaying.shuffleEnabled,
            accentPalette = accentPalette,
            actions = rememberQueueActions(viewModel, onSeekClick),
            onDismiss = state::dismiss,
        )
    }
    if (state.showComments) {
        val comments = rememberComments(nowPlaying, viewModel)
        CommentBottomSheet(
            comments = comments.first,
            hasLoadedData = nowPlaying.hasLoadedComments,
            accentPalette = accentPalette,
            paging = comments.second,
            onDismiss = state::dismiss,
            isLoading = nowPlaying.commentsLoading,
            isFromCache = nowPlaying.commentsFromCache,
            errorMessage = nowPlaying.commentsLoadError,
            onRetry = viewModel::getComments,
            totalCount = nowPlaying.comments?.total ?: 0L,
        )
    }
}

/** The queue edits a transport surface offers, going to the player through the view model. */
@Composable
internal fun rememberQueueActions(
    viewModel: NowPlayingViewModel,
    onSeekClick: ((Int) -> Unit)? = null,
): QueueActions = remember(viewModel, onSeekClick) {
    QueueActions(
        onSkipTo = onSeekClick ?: viewModel::onSeekClick,
        onRemove = viewModel::removeFromQueue,
        onMove = viewModel::moveInQueue,
        onClear = viewModel::clearQueue,
    )
}

/**
 * The comments to list — the first page of the ordering picked, then the pages loaded after it —
 * and the paging state that goes with them.
 */
@Composable
internal fun rememberComments(
    nowPlaying: NowPlayingUiState,
    viewModel: NowPlayingViewModel,
): Pair<List<CommentMusicComments>, CommentPaging> {
    val showHot by viewModel.commentsShowHot.collectAsState()
    val more by viewModel.moreComments.collectAsState()
    val moreAvailable by viewModel.moreCommentsAvailable.collectAsState()
    val moreLoading by viewModel.moreCommentsLoading.collectAsState()
    val moreError by viewModel.moreCommentsError.collectAsState()
    val firstPage = nowPlaying.comments
    val comments = remember(firstPage, showHot, more) {
        (if (showHot) firstPage?.hotComments else firstPage?.comments).orEmpty() + more
    }
    val paging = CommentPaging(
        showHot = showHot,
        onShowHot = viewModel::setCommentsShowHot,
        moreAvailable = moreAvailable,
        moreLoading = moreLoading,
        moreError = moreError,
        onLoadMore = viewModel::loadMoreComments,
    )
    return comments to paging
}
