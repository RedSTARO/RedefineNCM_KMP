package com.leejlredstar.redefinencm.kmp.ui.component

import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.lyric.LyricSource
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.lyricCapabilityLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LyricCapabilityBadgeTest {
    @Test
    fun fourLevelsNameThemselvesAndKeepDistinctColorRoles() {
        val specs = LyricCapabilityLevel.entries.map(::lyricCapabilityBadgeSpec)

        assertEquals(listOf("文本", "逐行", "逐字", "TTML"), specs.map { it.visibleText })
        assertEquals(4, specs.map { it.tone }.toSet().size)
        assertEquals(
            listOf(
                "歌词等级：纯文本",
                "歌词等级：逐行同步",
                "歌词等级：逐字同步",
                "歌词等级：逐字同步，含和声与对唱",
            ),
            specs.map { it.contentDescription },
        )
        assertEquals(
            listOf(
                "纯文本（没有时间轴）",
                "逐行同步",
                "逐字同步（网易云逐字歌词）",
                "逐字同步，含和声与对唱（AMLL TTML）",
            ),
            specs.map { it.levelLabel },
        )
    }

    @Test
    fun sourceLabelDistinguishesProviderAndLocalSidecar() {
        assertEquals(
            "AMLL 歌词库",
            lyricSourceDisplayName(LyricSource.AMLL_TTML, "stevexmh-exact"),
        )
        assertEquals(
            "网易云 · 本地歌词文件",
            lyricSourceDisplayName(LyricSource.NCM_BACKEND, "local-sidecar"),
        )
        assertEquals("未知", lyricSourceDisplayName(null, ""))
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
            LyricUiState.Content(
                lineCount = 2,
                capabilityLevel = LyricCapabilityLevel.UNSYNCED,
            ).lyricCapabilityLevel,
        )
        assertNull(LyricUiState.Empty().lyricCapabilityLevel)
        assertNull(LyricUiState.Idle.lyricCapabilityLevel)
        assertNull(LyricUiState.Loading.lyricCapabilityLevel)
        assertNull(LyricUiState.Error("failed").lyricCapabilityLevel)
    }
}
