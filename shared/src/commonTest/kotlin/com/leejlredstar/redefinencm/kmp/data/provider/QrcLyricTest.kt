package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.amll.compose.lyric.LyricParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * QQ's QRC, in the shape the gateway answered with on 2026-09-23, turned into the YRC and LRC the
 * lyric pipeline parses.
 */
class QrcLyricTest {
    private val document = """
        <?xml version="1.0" encoding="utf-8"?>
        <QrcInfos>
        <QrcHeadInfo SaveTime="162" Version="100"/>
        <LyricInfo LyricCount="1">
        <Lyric_1 LyricType="1" LyricContent="[ti:你的]
        [ar:歌手]
        [offset:0]
        [14273,3225]你(14273,184)的(14457,272) (14729,0)名&quot;字&quot;(14729,392)
        [17498,3183]Hello(17498,191)(Live)(17689,256)
        "/>
        </LyricInfo>
        </QrcInfos>
    """.trimIndent()

    @Test
    fun plainLrcIsNotQrc() {
        assertNull(QrcLyric.contentOrNull("[00:01.00]一句歌词"))
    }

    @Test
    fun wordTimingSurvivesTheTripIntoYrc() {
        val content = requireNotNull(QrcLyric.contentOrNull(document))
        val yrc = QrcLyric.toYrc(content)

        val lines = LyricParser.parseYrc(yrc)
        assertEquals(2, lines.size)
        val first = lines.first()
        assertEquals(14273L, first.startTimeMs)
        assertEquals(listOf("你", "的", " ", "名\"字\""), first.words.map { it.text })
        assertEquals(14457L, first.words[1].startTimeMs)
        assertEquals(14457L + 272L, first.words[1].endTimeMs)
        // A bracketed word keeps its text rather than being cut at the bracket.
        assertEquals("Hello（Live）", lines[1].text)
    }

    @Test
    fun theSameLinesMakeALineLyric() {
        val content = requireNotNull(QrcLyric.contentOrNull(document))
        assertEquals(
            "[00:14.27]你的 名\"字\"\n[00:17.49]Hello(Live)",
            QrcLyric.toLrc(content),
        )
    }
}
