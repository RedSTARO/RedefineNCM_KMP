package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.leejlredstar.amll.compose.rememberReducedMotionEnabled
import com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusicComments
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.BackHandler
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel

/**
 * Which of the two transport sheets is open.
 *
 * Every surface that can reach the queue and the comments keeps one of these and draws
 * [TransportSheets] with it. A surface with its own pair of booleans and its own pair of
 * `if (show…) { …BottomSheet(…) }` blocks is one more copy to keep in step by hand.
 */
@Stable
internal class TransportSheetsState {
    private enum class Sheet { None, Queue, Comments }

    private var active by mutableStateOf(Sheet.None)
    val showQueue: Boolean get() = active == Sheet.Queue
    val showComments: Boolean get() = active == Sheet.Comments
    val anyOpen: Boolean get() = active != Sheet.None
    var commentsRequestedFor: String? = null

    fun openQueue() {
        active = Sheet.Queue
        commentsRequestedFor = null
    }

    fun openComments() {
        active = Sheet.Comments
        commentsRequestedFor = null
    }

    fun dismiss() {
        active = Sheet.None
        commentsRequestedFor = null
    }

    companion object {
        val Saver = listSaver<TransportSheetsState, String>(
            save = { listOf(it.active.name, it.commentsRequestedFor.orEmpty()) },
            restore = { saved ->
                TransportSheetsState().apply {
                    active = Sheet.valueOf(saved[0])
                    commentsRequestedFor = saved[1].takeIf(String::isNotEmpty)
                }
            },
        )
    }
}

@Composable
internal fun rememberTransportSheetsState(): TransportSheetsState =
    if (remember { getPlatform().isAndroid }) {
        rememberSaveable(saver = TransportSheetsState.Saver) { TransportSheetsState() }
    } else {
        remember { TransportSheetsState() }
    }

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
    // Comments load when the sheet opens, and again when the track changes while it is open.
    // This effect runs here for every surface; surfaces do not run their own.
    val android = remember { getPlatform().isAndroid }
    val mediaId = nowPlaying.media?.id
    LaunchedEffect(state.showComments, mediaId) {
        if (!state.showComments || mediaId == null) return@LaunchedEffect
        // getComments refreshes page one and drops later pages. A restored sheet with data
        // for the same track is still the same visit, not a newly opened sheet.
        val hasCurrentResult = nowPlaying.hasLoadedComments || nowPlaying.commentsLoading ||
            nowPlaying.commentsLoadError != null
        if (!android || state.commentsRequestedFor != mediaId || !hasCurrentResult) {
            state.commentsRequestedFor = mediaId
            viewModel.getComments()
        }
    }
    val queueActions = rememberQueueActions(viewModel, onSeekClick)
    // On the desktop the queue and the comments slide in from the side, the way a window with
    // room to spare shows a panel; a drag-handled sheet rising from the bottom is a phone idiom.
    val sidePanels = remember { getPlatform().isDesktop }
    if (sidePanels) {
        val comments = rememberComments(nowPlaying, viewModel)
        // Closing clears both flags in one frame; the panel that was open has to stay drawn
        // while it slides away, or it leaves as an empty sheet.
        val lastPanel = remember { LastSidePanel() }
        if (state.anyOpen) lastPanel.queue = state.showQueue
        val showingQueue = lastPanel.queue
        TransportSidePanel(
            visible = state.anyOpen,
            onDismiss = state::dismiss,
        ) {
            if (showingQueue) {
                QueuePanelContent(
                    playlist = nowPlaying.playList,
                    currentIndex = nowPlaying.currentIndex,
                    shuffleEnabled = nowPlaying.shuffleEnabled,
                    accentPalette = accentPalette,
                    actions = queueActions,
                    onSkipped = {},
                )
            } else {
                CommentPanelContent(
                    comments = comments.first,
                    hasLoadedData = nowPlaying.hasLoadedComments,
                    accentPalette = accentPalette,
                    paging = comments.second,
                    isLoading = nowPlaying.commentsLoading,
                    isFromCache = nowPlaying.commentsFromCache,
                    errorMessage = nowPlaying.commentsLoadError,
                    onRetry = viewModel::getComments,
                    totalCount = nowPlaying.comments?.total ?: 0L,
                    unsupported = nowPlaying.hasMedia && !nowPlaying.canComment,
                )
            }
        }
        return
    }
    if (android && state.anyOpen && !nowPlaying.hasMedia) {
        val restoreState by viewModel.playerStatusRestoreState.collectAsState()
        // Don't give the restored list an empty dataset long enough to clamp its saved offset.
        if (restoreState is PlayerStatusRestoreState.Loading) return
    }
    if (state.showQueue) {
        QueueBottomSheet(
            playlist = nowPlaying.playList,
            currentIndex = nowPlaying.currentIndex,
            shuffleEnabled = nowPlaying.shuffleEnabled,
            accentPalette = accentPalette,
            actions = queueActions,
            onDismiss = state::dismiss,
        )
    }
    if (state.showComments) {
        val comments = rememberComments(nowPlaying, viewModel)
        key(if (android) mediaId else Unit) {
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
                unsupported = nowPlaying.hasMedia && !nowPlaying.canComment,
            )
        }
    }
}

/**
 * A panel sliding in from the end edge over a scrim; tapping the scrim or going back closes it.
 * The desktop's home for the queue and the comments.
 */
@Composable
private fun TransportSidePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val reducedMotion = rememberReducedMotionEnabled()
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = minOf(maxWidth, SidePanelWidth)
        AnimatedVisibility(
            visible = visible,
            enter = if (reducedMotion) EnterTransition.None else fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = if (reducedMotion) EnterTransition.None else slideInHorizontally { it * direction },
            exit = if (reducedMotion) ExitTransition.None else slideOutHorizontally { it * direction },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                color = colors.surfaceContainerLow,
                contentColor = colors.onSurface,
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(panelWidth)
                    .semantics {
                        dismiss(label = strings.close) {
                            onDismiss()
                            true
                        }
                    },
            ) {
                Column(Modifier.fillMaxSize().padding(top = 16.dp), content = content)
            }
        }
    }
}

private val SidePanelWidth = 440.dp

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
 * The comments to list (the first page of the ordering picked, then the pages loaded after it)
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
        moreError = moreError?.text,
        onLoadMore = viewModel::loadMoreComments,
    )
    return comments to paging
}

/** Which side panel was open last; a plain holder written while composing, not state. */
private class LastSidePanel {
    var queue: Boolean = true
}
