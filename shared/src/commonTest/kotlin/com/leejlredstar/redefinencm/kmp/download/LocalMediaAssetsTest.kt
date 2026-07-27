package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.lyric.LyricDocument
import com.leejlredstar.redefinencm.kmp.lyric.LyricCapabilityLevel
import com.leejlredstar.redefinencm.kmp.lyric.LyricSource
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalMediaAssetsTest {
    @Test
    fun ttmlIsSavedVerbatimWithoutDerivedLrcFiles() {
        val raw = """<?xml version="1.0"?><tt><body><p begin="0s">原文</p></body></tt>"""
        val files = LyricDocument(
            source = LyricSource.AMLL_TTML,
            capabilityLevel = LyricCapabilityLevel.TTML_FULL,
            lines = emptyList(),
            rawTtml = raw,
            rawLineLyric = "[00:00.00]derived",
            rawTranslatedLyric = "[00:00.00]derived translation",
        ).toOriginalLyricSidecars(42)

        assertEquals(listOf("42.lyric.ttml"), files.map { it.fileName })
        assertEquals(raw, files.single().content)
    }

    @Test
    fun backendYrcKeepsEveryOriginalRepresentationVerbatim() {
        val yrc = "[0,1000](0,500,0)逐(500,500,0)字"
        val lrc = "[00:00.00]逐字"
        val translation = "[00:00.00]word by word"
        val romanization = "[00:00.00]zhu zi"

        val files = LyricDocument(
            source = LyricSource.NCM_BACKEND,
            capabilityLevel = LyricCapabilityLevel.NCM_YRC,
            lines = emptyList(),
            rawWordLyric = yrc,
            rawLineLyric = lrc,
            rawTranslatedLyric = translation,
            rawRomanLyric = romanization,
        ).toOriginalLyricSidecars(7)

        assertEquals(
            mapOf(
                "7.lyric.yrc" to yrc,
                "7.lyric.line.lrc" to lrc,
                "7.lyric.translation.lrc" to translation,
                "7.lyric.romanization.lrc" to romanization,
            ),
            files.associate { it.fileName to it.content },
        )
    }

    @Test
    fun backendLineLyricsUseLrcAsThePrimarySidecar() {
        val lrc = "[00:00.00]line only\r\n[00:01.00]第二行"

        val files = LyricDocument(
            source = LyricSource.NCM_BACKEND,
            capabilityLevel = LyricCapabilityLevel.LINE_SYNCED,
            lines = emptyList(),
            rawLineLyric = lrc,
        ).toOriginalLyricSidecars(9)

        assertEquals(listOf("9.lyric.lrc"), files.map { it.fileName })
        assertEquals(lrc, files.single().content)
    }
}
