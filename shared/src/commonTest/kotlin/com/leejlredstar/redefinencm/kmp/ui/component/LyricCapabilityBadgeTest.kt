package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.ui.graphics.Color
import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.lyric.LyricSource
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.DarkColors
import com.leejlredstar.redefinencm.kmp.ui.theme.LightColors
import com.leejlredstar.redefinencm.kmp.ui.theme.buildContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contrastRatio
import com.leejlredstar.redefinencm.kmp.viewmodel.LyricUiState
import com.leejlredstar.redefinencm.kmp.viewmodel.lyricCapabilityLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /**
     * The badge sits on the expanded playback card, which is drawn in
     * [ContentAccentPalette.container]. Every level has to be both readable in itself and
     * distinguishable from that card — a badge the colour of its own card is a label floating
     * on nothing, which is what the brand-coloured containers were replaced to avoid.
     */
    @Test
    fun everyLevelCarriesItsOwnLabelAndStandsOffTheCard() {
        listOf(0xFFFFE94A, 0xFF006B5B, 0xFFB03060, 0xFF3355FF, 0xFF9E9E9E)
            .forEach { source ->
                listOf("light" to LightColors, "dark" to DarkColors).forEach { (name, scheme) ->
                    val palette = buildContentAccentPalette(Color(source), scheme)
                    LyricCapabilityBadgeTone.entries.forEach { tone ->
                        val (container, content) =
                            lyricCapabilityBadgeColors(tone, palette, scheme.surface)
                        val legibility = contrastRatio(content, container, scheme.surface)
                        assertTrue(
                            legibility >= 4.5f,
                            "$tone label in $name for ${source.toString(16)} is $legibility",
                        )
                        val offTheCard =
                            contrastRatio(container, palette.container, scheme.surface)
                        assertTrue(
                            offTheCard >= 1.15f,
                            "$tone is the card's own colour in $name for " +
                                "${source.toString(16)}: $offTheCard",
                        )
                    }
                }
            }
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
