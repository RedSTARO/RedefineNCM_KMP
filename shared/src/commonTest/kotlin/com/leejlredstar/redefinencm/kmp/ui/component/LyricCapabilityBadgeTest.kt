package com.leejlredstar.redefinencm.kmp.ui.component

import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.lyricCapabilityLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricCapabilityBadgeTest {
    @Test
    fun fourLevelsKeepOneGlyphAndDistinctColorRoles() {
        val specs = LyricCapabilityLevel.entries.map(::lyricCapabilityBadgeSpec)

        assertEquals(setOf("词"), specs.map { it.visibleText }.toSet())
        assertEquals(4, specs.map { it.tone }.toSet().size)
        assertEquals(
            listOf(
                "歌词等级：无时间戳",
                "歌词等级：普通逐行",
                "歌词等级：NCM YRC",
                "歌词等级：TTML 完全支持",
            ),
            specs.map { it.contentDescription },
        )
    }

    @Test
    fun onlyClassifiedLyricStatesExposeABadgeLevel() {
        assertEquals(
            LyricCapabilityLevel.LINE_SYNCED,
            LyricUiState.Content(
                lineCount = 2,
                capabilityLevel = LyricCapabilityLevel.LINE_SYNCED,
            ).lyricCapabilityLevel,
        )
        assertEquals(
            LyricCapabilityLevel.UNSYNCED,
            LyricUiState.Empty(LyricCapabilityLevel.UNSYNCED).lyricCapabilityLevel,
        )
        assertNull(LyricUiState.Empty().lyricCapabilityLevel)
        assertNull(LyricUiState.Idle.lyricCapabilityLevel)
        assertNull(LyricUiState.Loading.lyricCapabilityLevel)
        assertNull(LyricUiState.Error("failed").lyricCapabilityLevel)
    }
}
