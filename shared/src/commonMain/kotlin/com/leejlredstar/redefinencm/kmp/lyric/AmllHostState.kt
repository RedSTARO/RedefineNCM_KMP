package com.leejlredstar.redefinencm.kmp.lyric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import com.leejlredstar.redefinencm.kmp.viewmodel.DynamicCoverUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.NowPlayingViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.SongWikiUiState
import kotlinx.serialization.json.Json

/**
 * Everything the three Legacy AMLL hosts feed into `player.html`.
 *
 * Android's System WebView, Desktop's WebView2 child window and iOS's WKWebView embed the page
 * in completely different ways, but they drive it with the same values: each host had copied the
 * same nineteen `collectAsState` calls and then re-derived the same payloads from them. Only the
 * embedding is platform work, so only the embedding stays in the platform files.
 */
@Immutable
internal data class AmllHostState(
    val rawLyric: String,
    val rawWordLyric: String,
    val rawTtmlLyric: String,
    val rawTranslatedLyric: String,
    val rawRomanLyric: String,
    val lyricMap: LinkedHashMap<Long?, String?>,
    val untimedLyricLines: List<String>,
    val lyricUiState: LyricUiState,
    val lyricMediaId: String?,
    val currentPosition: Long,
    val metadata: MediaInfo?,
    val playerStatusRestoreState: PlayerStatusRestoreState,
    val localArtworkActive: Boolean,
    val remoteArtworkUri: String,
    val dynamicCoverUiState: DynamicCoverUiState,
    val songWikiUiState: SongWikiUiState,
    val showTranslatedLyric: Boolean,
    val showRomanLyric: Boolean,
) {
    val dynamicCoverUrl: String? get() = dynamicCoverUiState.urlFor(metadata?.id)

    /** Plain LRC for the page, or empty while the pipeline has no displayable content. */
    val lyricForWeb: String
        get() = if (lyricUiState is LyricUiState.Content) {
            rawLyric.takeIf(String::isNotBlank) ?: lyricMap.toLrcFallbackText()
        } else {
            ""
        }

    val untimedLyricsForWeb: String get() = amllUntimedLyricsJson.encodeToString(untimedLyricLines)

    val isUntimedContent: Boolean
        get() = (lyricUiState as? LyricUiState.Content)?.capabilityLevel ==
            LyricCapabilityLevel.UNSYNCED

    /**
     * Which artwork URI the page should show, given a locally inlined cover the host resolved.
     *
     * The hosts resolve [localAmllArtwork] differently — Android walks MediaStore, Desktop reads
     * the download directory, iOS reads its Documents container — but they agree on how to choose
     * between it, the remote URI and the raw metadata URI.
     */
    fun artworkUriFor(localAmllArtwork: Pair<String, String>?): String =
        metadata?.let { media ->
            if (!localArtworkActive) {
                media.artworkUri
            } else {
                localAmllArtwork
                    ?.takeIf { (mediaId, _) -> mediaId == media.id }
                    ?.second
                    ?: remoteArtworkUri
            }
        }.orEmpty()
}

/** What the page should be told when there is no lyric content to render. */
internal sealed interface AmllHostPlaceholder {
    /** Show a neutral status line. */
    data class Status(val message: String) : AmllHostPlaceholder

    /** Show the failure prominently. */
    data class Error(val message: String) : AmllHostPlaceholder
}

/**
 * The placeholder for a non-[LyricUiState.Content] state, or null when there is content to load.
 *
 * All three hosts inlined this same `when`, down to the wording, which is exactly the kind of
 * thing that goes out of sync one platform at a time.
 */
internal fun AmllHostState.placeholder(): AmllHostPlaceholder? = when (val state = lyricUiState) {
    is LyricUiState.Content -> null

    is LyricUiState.Idle -> AmllHostPlaceholder.Status(
        when {
            metadata != null -> "正在恢复歌词…"
            playerStatusRestoreState is PlayerStatusRestoreState.Loading -> "正在恢复播放…"
            else -> "等待播放…"
        },
    )

    is LyricUiState.Loading -> AmllHostPlaceholder.Status("正在加载歌词…")

    is LyricUiState.Empty -> AmllHostPlaceholder.Status(
        if (state.capabilityLevel == LyricCapabilityLevel.UNSYNCED) {
            "歌词无时间戳"
        } else {
            "暂无歌词"
        },
    )

    is LyricUiState.Error -> AmllHostPlaceholder.Error(state.message)
}

private val amllUntimedLyricsJson = Json

@Composable
internal fun rememberAmllHostState(viewModel: NowPlayingViewModel): AmllHostState {
    val rawLyric by viewModel.rawLyric.collectAsState()
    val rawWordLyric by viewModel.rawWordLyric.collectAsState()
    val rawTtmlLyric by viewModel.rawTtmlLyric.collectAsState()
    val rawTranslatedLyric by viewModel.rawTranslatedLyric.collectAsState()
    val rawRomanLyric by viewModel.rawRomanLyric.collectAsState()
    val lyricMap by viewModel.lyricMap.collectAsState()
    val untimedLyricLines by viewModel.untimedLyricLines.collectAsState()
    val lyricUiState by viewModel.lyricUiState.collectAsState()
    val lyricMediaId by viewModel.lyricMediaId.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val metadata by viewModel.currentMedia.collectAsState()
    val playerStatusRestoreState by viewModel.playerStatusRestoreState.collectAsState()
    val localArtworkActive by viewModel.localArtworkActive.collectAsState()
    val remoteArtworkUri by viewModel.remoteArtworkUri.collectAsState()
    val dynamicCoverUiState by viewModel.dynamicCoverUiState.collectAsState()
    val songWikiUiState by viewModel.songWikiUiState.collectAsState()
    val showTranslatedLyric by viewModel.showTranslatedLyric.collectAsState()
    val showRomanLyric by viewModel.showRomanLyric.collectAsState()
    return remember(
        rawLyric,
        rawWordLyric,
        rawTtmlLyric,
        rawTranslatedLyric,
        rawRomanLyric,
        lyricMap,
        untimedLyricLines,
        lyricUiState,
        lyricMediaId,
        currentPosition,
        metadata,
        playerStatusRestoreState,
        localArtworkActive,
        remoteArtworkUri,
        dynamicCoverUiState,
        songWikiUiState,
        showTranslatedLyric,
        showRomanLyric,
    ) {
        AmllHostState(
            rawLyric = rawLyric,
            rawWordLyric = rawWordLyric,
            rawTtmlLyric = rawTtmlLyric,
            rawTranslatedLyric = rawTranslatedLyric,
            rawRomanLyric = rawRomanLyric,
            lyricMap = lyricMap,
            untimedLyricLines = untimedLyricLines,
            lyricUiState = lyricUiState,
            lyricMediaId = lyricMediaId,
            currentPosition = currentPosition,
            metadata = metadata,
            playerStatusRestoreState = playerStatusRestoreState,
            localArtworkActive = localArtworkActive,
            remoteArtworkUri = remoteArtworkUri,
            dynamicCoverUiState = dynamicCoverUiState,
            songWikiUiState = songWikiUiState,
            showTranslatedLyric = showTranslatedLyric,
            showRomanLyric = showRomanLyric,
        )
    }
}
