package com.leejlredstar.redefinencm.kmp.lyric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TtmlLyricParserTest {
    @Test
    fun parsesWordTimingTranslationRomanizationAndDuet() {
        val lines = TtmlLyricParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="1.000" end="3s" itunes:key="L1" ttm:agent="v2">
                    <span begin="1.000" end="1.500">A &amp; </span>
                    <span begin="1.500" end="3.000">B</span>
                    <span ttm:role="x-translation">甲和乙</span>
                    <span ttm:role="x-translation">A and B</span>
                    <span ttm:role="x-roman">a and b</span>
                    <span ttm:role="x-roman">second romanization</span>
                  </p>
                  <p begin="1:00.250" end="00:01:02.000">next</p>
                </div>
              </body>
            </tt>
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals(1_000L, lines[0].startTimeMs)
        assertEquals(3_000L, lines[0].endTimeMs)
        assertEquals("A & B", lines[0].text)
        assertEquals("甲和乙", lines[0].translatedLyric)
        assertEquals("a and b", lines[0].romanLyric)
        assertTrue(lines[0].isDuet)
        assertEquals(60_250L, lines[1].startTimeMs)
        assertEquals("next", lines[1].text)
    }

    @Test
    fun supportsRelativeWordTimesInsideTimedLine() {
        val line = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div><p begin="10s" end="12s">
                <span begin="0s" end="1s">relative</span>
              </p></div></body>
            </tt>
            """.trimIndent(),
        ).single()

        assertEquals(10_000L, line.words.single().startTimeMs)
        assertEquals(11_000L, line.words.single().endTimeMs)
    }

    @Test
    fun preservesSpacesBetweenTimedSpans() {
        val line = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div><p begin="0s" end="2s"><span begin="0s" end="1s">A</span> <span begin="1s" end="2s">B</span></p></div></body>
            </tt>
            """.trimIndent(),
        ).single()

        assertEquals("A B", line.text)
    }

    @Test
    fun preservesNestedBackgroundVocalAndBoundsNestedTextWork() {
        val lines = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="10s" end="13s" itunes:key="L1">
                <span begin="10s" end="11s">main</span>
                <span ttm:role="x-bg">
                  <span begin="11s" end="11.5s">back</span> <span begin="11.5s" end="12s">vocal</span>
                  <span ttm:role="x-translation">和声</span>
                </span>
              </p></div></body>
            </tt>
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals("main", lines[0].text)
        assertEquals("back vocal", lines[1].text)
        assertTrue(lines[1].isBackground)
        assertEquals("和声", lines[1].translatedLyric)
    }

    @Test
    fun rejectsExcessiveSpanDepth() {
        val nested = buildString {
            append("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="0s">""")
            repeat(33) { append("<span>") }
            append("line")
            repeat(33) { append("</span>") }
            append("</p></div></body></tt>")
        }

        assertFailsWith<IllegalArgumentException> {
            TtmlLyricParser.parse(nested)
        }
    }

    @Test
    fun backgroundOnlyDocumentHasNoPrimaryLine() {
        val lines = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="0s" end="1s">
                <span ttm:role="x-bg"><span begin="0s" end="1s">back</span></span>
              </p></div></body>
            </tt>
            """.trimIndent(),
        )

        assertEquals(1, lines.size)
        assertTrue(lines.single().isBackground)
        assertFalse(lines.hasPrimaryTimedLine())
    }

    @Test
    fun rejectsDoctypeBeforeOpeningXmlReader() {
        assertFailsWith<IllegalArgumentException> {
            TtmlLyricParser.parse(
                """<!DOCTYPE tt [<!ENTITY x SYSTEM "file:///etc/passwd">]><tt>&x;</tt>""",
            )
        }
    }
}
