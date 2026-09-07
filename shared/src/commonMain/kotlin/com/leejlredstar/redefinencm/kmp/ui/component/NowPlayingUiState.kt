package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusic
import com.leejlredstar.redefinencm.kmp.data.api.dto.CommentMusicComments
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.player.PlayerQueueSnapshot
import com.leejlredstar.redefinencm.kmp.viewmodel.FavoriteUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel

/**
 * Everything a transport surface needs about the current song, read once.
 *
 * The desktop now-playing strip and the auto-hiding mini player each collected the same nine
 * flows by hand and re-derived the same four values from them, which is how their progress
 * maths drifted apart. Deriving them here keeps one definition of "how full is the bar".
 */
@Immutable
internal data class NowPlayingUiState(
    val media: MediaInfo?,
    val isPlaying: Boolean,
    val position: Long,
    val duration: Long,
    val queueSnapshot: PlayerQueueSnapshot,
    val comments: CommentMusic?,
    val commentsLoading: Boolean,
    val commentsLoadError: String?,
    val commentsFromCache: Boolean,
    val favoriteState: FavoriteUiState,
) {
    val playList: List<MediaInfo> get() = queueSnapshot.items
    val currentIndex: Int get() = queueSnapshot.currentIndex
    val shuffleEnabled: Boolean get() = queueSnapshot.shuffleEnabled

    val hasMedia: Boolean get() = media != null

    /**
     * What the comment sheet shows: the hot comments, or the plain ones when there are no hot
     * ones. Each transport surface used to spell this fallback out at its own call site.
     */
    val displayComments: List<CommentMusicComments>
        get() = comments?.hotComments?.ifEmpty { comments?.comments } ?: emptyList()

    /** Whether a comment fetch has ever completed, which the sheet shows differently from empty. */
    val hasLoadedComments: Boolean get() = comments != null

    val isFavorite: Boolean
        get() = favoriteState.mediaId == media?.id && favoriteState.isLiked

    /** A player that has not reported a duration yet still knows one from the queue metadata. */
    val totalDuration: Long
        get() = duration.takeIf { it > 0L }
            ?: media?.duration?.takeIf { it > 0L }
            ?: 0L

    /** Some backends report a negative position between selecting and opening a track. */
    val safePosition: Long get() = position.coerceAtLeast(0L)

    val progress: Float
        get() = if (totalDuration > 0L) {
            (safePosition.toDouble() / totalDuration.toDouble()).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }
}

@Composable
internal fun rememberNowPlayingUiState(
    player: PlatformPlayer,
    viewModel: NowPlayingViewModel,
): NowPlayingUiState {
    val media by player.currentMedia.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    val queueSnapshot by viewModel.queueSnapshot.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val commentsLoading by viewModel.commentsLoading.collectAsState()
    val commentsLoadError by viewModel.commentsLoadError.collectAsState()
    val commentsFromCache by viewModel.commentsFromCache.collectAsState()
    val favoriteState by viewModel.favoriteUiState.collectAsState()
    return remember(
        media,
        isPlaying,
        position,
        duration,
        queueSnapshot,
        comments,
        commentsLoading,
        commentsLoadError,
        commentsFromCache,
        favoriteState,
    ) {
        NowPlayingUiState(
            media = media,
            isPlaying = isPlaying,
            position = position,
            duration = duration,
            queueSnapshot = queueSnapshot,
            comments = comments,
            commentsLoading = commentsLoading,
            commentsLoadError = commentsLoadError,
            commentsFromCache = commentsFromCache,
            favoriteState = favoriteState,
        )
    }
}
