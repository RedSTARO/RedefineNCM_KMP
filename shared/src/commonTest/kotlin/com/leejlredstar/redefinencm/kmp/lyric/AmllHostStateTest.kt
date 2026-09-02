package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.player.PlayerStatusRestoreState
import com.leejlredstar.redefinencm.kmp.viewmodel.DynamicCoverUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.SongWikiUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The payloads and placeholder wording the Android, Desktop and iOS AMLL hosts all send.
 *
 * The payload cases moved here from the Desktop host's own test when the derivation stopped
 * being desktop-specific; the placeholder cases are new, because while each host inlined its own
 * copy of that `when` there was nowhere to assert the wording once.
 */
class AmllHostStateTest {

    private fun state(
        rawLyric: String = "",
        lyricMap: LinkedHashMap<Long?, String?> = linkedMapOf(),
        lyricUiState: LyricUiState = LyricUiState.Idle,
        metadata: MediaInfo? = null,
        playerStatusRestoreState: PlayerStatusRestoreState =
            PlayerStatusRestoreState.Restored(null),
        localArtworkActive: Boolean = false,
        remoteArtworkUri: String = "",
    ) = AmllHostState(
        rawLyric = rawLyric,
        rawWordLyric = "",
        rawTtmlLyric = "",
        rawTranslatedLyric = "",
        rawRomanLyric = "",
        lyricMap = lyricMap,
        untimedLyricLines = emptyList(),
        lyricUiState = lyricUiState,
        lyricMediaId = metadata?.id,
        currentPosition = 0L,
        metadata = metadata,
        playerStatusRestoreState = playerStatusRestoreState,
        localArtworkActive = localArtworkActive,
        remoteArtworkUri = remoteArtworkUri,
        dynamicCoverUiState = DynamicCoverUiState(),
        songWikiUiState = SongWikiUiState.Idle,
        showTranslatedLyric = false,
        showRomanLyric = false,
    )

    private fun media(id: String = "1", artworkUri: String = "https://cdn/cover.jpg") =
        MediaInfo(id = id, title = "t", artist = "a", artworkUri = artworkUri)

    private val content = LyricUiState.Content(
        lineCount = 2,
        capabilityLevel = LyricCapabilityLevel.LINE_SYNCED,
    )

    // ── payload ──

    @Test
    fun fallsBackToParsedLyricsWhenRawPayloadIsBlank() {
        val payload = state(
            rawLyric = " \n ",
            lyricMap = linkedMapOf(1_500L to "第一句", 3_000L to "第二句"),
            lyricUiState = content,
        ).lyricForWeb

        assertTrue(payload.contains("第一句"))
        assertTrue(payload.contains("第二句"))
        assertTrue(payload.contains("["))
    }

    @Test
    fun doesNotExposeStaleLyricsOutsideContentState() {
        val payload = state(
            rawLyric = "[00:01.00]旧歌词",
            lyricMap = linkedMapOf(1_000L to "旧歌词"),
            lyricUiState = LyricUiState.Loading,
        ).lyricForWeb

        assertEquals("", payload)
    }

    @Test
    fun prefersTheRawPayloadWhenItHasContent() {
        val payload = state(
            rawLyric = "[00:01.00]原始",
            lyricMap = linkedMapOf(1_000L to "回退"),
            lyricUiState = content,
        ).lyricForWeb

        assertEquals("[00:01.00]原始", payload)
    }

    @Test
    fun marksOnlyUnsyncedContentAsUntimed() {
        assertTrue(
            state(
                lyricUiState = LyricUiState.Content(
                    lineCount = 1,
                    capabilityLevel = LyricCapabilityLevel.UNSYNCED,
                ),
            ).isUntimedContent,
        )
        assertTrue(!state(lyricUiState = content).isUntimedContent)
        assertTrue(!state(lyricUiState = LyricUiState.Loading).isUntimedContent)
    }

    // ── placeholder ──

    @Test
    fun contentHasNoPlaceholder() {
        assertNull(state(lyricUiState = content).placeholder())
    }

    @Test
    fun idleDistinguishesRestoringLyricsFromRestoringPlayback() {
        assertEquals(
            AmllHostPlaceholder.Status("正在恢复歌词…"),
            state(lyricUiState = LyricUiState.Idle, metadata = media()).placeholder(),
        )
        assertEquals(
            AmllHostPlaceholder.Status("正在恢复播放…"),
            state(
                lyricUiState = LyricUiState.Idle,
                playerStatusRestoreState = PlayerStatusRestoreState.Loading,
            ).placeholder(),
        )
        assertEquals(
            AmllHostPlaceholder.Status("等待播放…"),
            state(lyricUiState = LyricUiState.Idle).placeholder(),
        )
    }

    @Test
    fun emptyDistinguishesNoLyricsFromUntimedLyrics() {
        assertEquals(
            AmllHostPlaceholder.Status("歌词无时间戳"),
            state(
                lyricUiState = LyricUiState.Empty(LyricCapabilityLevel.UNSYNCED),
            ).placeholder(),
        )
        assertEquals(
            AmllHostPlaceholder.Status("暂无歌词"),
            state(lyricUiState = LyricUiState.Empty()).placeholder(),
        )
    }

    @Test
    fun failureIsReportedAsAnErrorRatherThanAStatus() {
        assertEquals(
            AmllHostPlaceholder.Error("网络错误"),
            state(lyricUiState = LyricUiState.Error("网络错误")).placeholder(),
        )
    }

    @Test
    fun loadingSaysSo() {
        assertEquals(
            AmllHostPlaceholder.Status("正在加载歌词…"),
            state(lyricUiState = LyricUiState.Loading).placeholder(),
        )
    }

    // ── artwork ──

    @Test
    fun usesTheMetadataArtworkWhenNoLocalCoverIsActive() {
        val s = state(metadata = media(), localArtworkActive = false)
        assertEquals("https://cdn/cover.jpg", s.artworkUriFor("1" to "data:image/png;base64,AA"))
    }

    @Test
    fun usesTheInlinedLocalCoverOnlyWhenItBelongsToTheCurrentSong() {
        val s = state(
            metadata = media(id = "1"),
            localArtworkActive = true,
            remoteArtworkUri = "https://cdn/remote.jpg",
        )
        assertEquals("data:image/png;base64,AA", s.artworkUriFor("1" to "data:image/png;base64,AA"))
        // A cover resolved for the previous song must not be shown for this one.
        assertEquals("https://cdn/remote.jpg", s.artworkUriFor("2" to "data:image/png;base64,AA"))
        assertEquals("https://cdn/remote.jpg", s.artworkUriFor(null))
    }

    @Test
    fun hasNoArtworkWithoutASelection() {
        assertEquals("", state(metadata = null).artworkUriFor(null))
    }
}
