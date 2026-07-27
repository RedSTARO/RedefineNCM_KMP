package com.leejlredstar.redefinencm.kmp.lyric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TtmlLyricParserTest {
    @Test
    fun transportsOfficialRubyObsceneEmptyBeatBackgroundAndInlineFieldsEndToEnd() {
        val lines = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:amll="http://www.example.com/ns/amll"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:tts="http://www.w3.org/ns/ttml#styling">
              <head>
                <metadata>
                  <ttm:agent xml:id="v1" type="person"><ttm:name>Main</ttm:name></ttm:agent>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="1.0004s" end="4s" itunes:key="L1" ttm:agent="v1">
                    <span begin="1.0004s" end="1.5s"
                          amll:obscene="true" amll:empty-beat="5">Hello </span>
                    <span tts:ruby="container" amll:obscene="true">
                      <span tts:ruby="base">世界</span>
                      <span tts:ruby="textContainer">
                        <span tts:ruby="text" begin="1.5s" end="2.0005s">せかい</span>
                      </span>
                    </span>
                    <span ttm:role="x-translation" xml:lang="zh-Hans">内联翻译</span>
                    <span ttm:role="x-roman" xml:lang="ja-Latn">inline roman</span>
                    <span ttm:role="x-bg" begin="2.5s" end="3.5s">（
                      <span begin="2.5s" end="3.4s">和声</span>
                      <span ttm:role="x-translation" xml:lang="zh-Hans">背景翻译</span>
                    ）</span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        val main = lines[0]
        val background = lines[1]

        assertEquals("Hello 世界", main.text)
        assertEquals(1_000L, main.startTimeMs)
        assertEquals(4_000L, main.endTimeMs)
        assertEquals(1_000.0, main.exactStartTimeMs)
        assertEquals(4_000.0, main.exactEndTimeMs)
        assertEquals("内联翻译", main.translatedLyric)
        assertEquals("inline roman", main.romanLyric)
        assertFalse(main.isBackground)
        assertFalse(main.isDuet)

        assertEquals("Hello ", main.words[0].text)
        assertTrue(main.words[0].obscene)
        assertEquals(5, main.words[0].emptyBeat)
        assertEquals(1_000.0, main.words[0].exactStartTimeMs)
        assertEquals("世界", main.words[1].text)
        assertTrue(main.words[1].obscene)
        assertEquals(1_500.0, main.words[1].exactStartTimeMs)
        assertEquals(2_001.0, main.words[1].exactEndTimeMs)
        assertEquals(1, main.words[1].ruby.size)
        assertEquals("せかい", main.words[1].ruby.single().text)
        assertEquals(1_500.0, main.words[1].ruby.single().exactStartTimeMs)
        assertEquals(2_001.0, main.words[1].ruby.single().exactEndTimeMs)

        assertTrue(background.isBackground)
        assertEquals("和声", background.text)
        assertEquals(2_500L, background.startTimeMs)
        assertEquals(3_500L, background.endTimeMs)
        assertEquals("背景翻译", background.translatedLyric)
        assertEquals(main.isDuet, background.isDuet)
    }

    @Test
    fun restoresEcmaScriptWhitespaceBetweenTimedSyllables() {
        val line = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <body><div><p begin="0s" end="2s" itunes:key="L1"><span begin="0s" end="1s">A</span> <span begin="1s" end="2s">B</span></p></div></body>
            </tt>
            """.trimIndent(),
        ).single()

        assertEquals(listOf("A ", "B"), line.words.map { it.text })
        assertEquals("A B", line.text)
    }

    @Test
    fun mergesItunesSidecarsAndAlignsTimedRomanizationByFastTrackThenIou() {
        val line = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head>
                <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                  <translations>
                    <translation xml:lang="zh-Hans">
                      <text for="L2">侧载翻译</text>
                    </translation>
                  </translations>
                  <transliterations>
                    <transliteration xml:lang="ja-Latn">
                      <text for="L2">
                        <span begin="10.002s" end="10.390s" xmlns="http://www.w3.org/ns/ttml">ko</span>
                        <span begin="10.450s" end="10.550s" xmlns="http://www.w3.org/ns/ttml">u</span>
                      </text>
                    </transliteration>
                  </transliterations>
                </iTunesMetadata>
              </head>
              <body><div>
                <p begin="10s" end="11s" itunes:key="L2" ttm:agent="v1">
                  <span begin="10s" end="10.400s">処</span>
                  <span begin="10.400s" end="10.405s">、</span>
                  <span begin="10.405s" end="11s">浮</span>
                </p>
              </div></body>
            </tt>
            """.trimIndent(),
        ).single()

        assertEquals("侧载翻译", line.translatedLyric)
        assertEquals("", line.romanLyric)
        assertEquals(listOf("ko", "", "u"), line.words.map { it.romanWord })
        // `ko` takes the <=2 ms fast path; punctuation has neither overlap nor a close start;
        // `u` reaches 100/595 IoU and therefore clears the official 0.1 threshold.
        assertEquals(listOf("処", "、", "浮"), line.words.map { it.text })
    }

    @Test
    fun followsOfficialPersonOtherAndGroupDuetAlternation() {
        val lines = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head><metadata>
                <ttm:agent xml:id="v1" type="person"/>
                <ttm:agent xml:id="v2" type="other"/>
                <ttm:agent xml:id="v3" type="person"/>
                <ttm:agent xml:id="v1000" type="group"/>
              </metadata></head>
              <body><div>
                <p begin="0s" end="1s" itunes:key="L1" ttm:agent="v2">other first</p>
                <p begin="1s" end="2s" itunes:key="L2" ttm:agent="v1000">group</p>
                <p begin="2s" end="3s" itunes:key="L3" ttm:agent="v2">other again</p>
                <p begin="3s" end="4s" itunes:key="L4" ttm:agent="v1">person one</p>
                <p begin="4s" end="5s" itunes:key="L5" ttm:agent="v3">person two</p>
              </div></body>
            </tt>
            """.trimIndent(),
        )

        assertEquals(
            listOf(true, false, true, false, true),
            lines.map { it.isDuet },
        )
    }

    @Test
    fun usesOfficialMathRoundAndKeepsTheRoundedJavascriptNumberInExactFields() {
        assertEquals(101.0, TtmlLyricParser.parseTtmlTimeExact("0.1005s"))
        assertEquals(100.0, TtmlLyricParser.parseTtmlTimeExact("0.1004s"))
        assertEquals(62_000.0, TtmlLyricParser.parseTtmlTimeExact("00:01:02.000"))
        assertEquals(0.0, TtmlLyricParser.parseTtmlTimeExact("500ms"))
        val negativeZero = TtmlLyricParser.parseTtmlTimeExact("-0.0005s")
        assertEquals((-0.0).toBits(), negativeZero.toBits())
        assertEquals(0L, TtmlLyricParser.parseTtmlTime("-0.0005s"))

        val line = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <body><p begin="0.1005s" end="0.2005s" itunes:key="L1">rounded</p></body>
            </tt>
            """.trimIndent(),
        ).single()

        assertEquals(101L, line.startTimeMs)
        assertEquals(201L, line.endTimeMs)
        assertEquals(101.0, line.exactStartTimeMs)
        assertEquals(201.0, line.exactEndTimeMs)
        assertEquals(101.0, line.words.single().exactStartTimeMs)
        assertEquals(201.0, line.words.single().exactEndTimeMs)
    }

    @Test
    fun normalizesJavascriptNanToZeroWhilePreservingInfinity() {
        assertEquals(0.0, TtmlLyricParser.parseTtmlTimeExact("NaNs"))
        assertEquals(
            Double.POSITIVE_INFINITY,
            TtmlLyricParser.parseTtmlTimeExact("Infinitys"),
        )

        val nanLine = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <body><p begin="NaNs" end="2s" itunes:key="L1">not a number</p></body>
            </tt>
            """.trimIndent(),
        ).single()
        assertEquals(0L, nanLine.startTimeMs)
        assertEquals(0.0, nanLine.exactStartTimeMs)
        assertEquals(0L, nanLine.words.single().startTimeMs)
        assertEquals(0.0, nanLine.words.single().exactStartTimeMs)

        val infiniteLine = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <body><p begin="Infinitys" end="Infinitys" itunes:key="L2">infinite</p></body>
            </tt>
            """.trimIndent(),
        ).single()
        assertEquals(Long.MAX_VALUE, infiniteLine.startTimeMs)
        assertEquals(Double.POSITIVE_INFINITY, infiniteLine.exactStartTimeMs)
        assertEquals(Long.MAX_VALUE, infiniteLine.words.single().startTimeMs)
        assertEquals(Double.POSITIVE_INFINITY, infiniteLine.words.single().exactStartTimeMs)
    }

    @Test
    fun skipsParagraphsWithoutTheRequiredItunesKey() {
        val lines = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><p begin="0s" end="1s">unkeyed</p></body>
            </tt>
            """.trimIndent(),
        )

        assertTrue(lines.isEmpty())
    }

    @Test
    fun backgroundOnlyOfficialConversionKeepsAnEmptyPrimaryButIsNotAHit() {
        val lines = TtmlLyricParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><p begin="0s" end="1s" itunes:key="L1">
                <span ttm:role="x-bg"><span begin="0s" end="1s">back</span></span>
              </p></body>
            </tt>
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
        assertFalse(lines[0].isBackground)
        assertEquals("back", lines[1].text)
        assertTrue(lines[1].isBackground)
        assertFalse(lines.hasPrimaryTimedLine())
    }

    @Test
    fun rejectsExcessiveSpanDepth() {
        val nested = buildString {
            append(
                """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal"><body><p begin="0s" itunes:key="L1">""",
            )
            repeat(33) { append("<span>") }
            append("line")
            repeat(33) { append("</span>") }
            append("</p></body></tt>")
        }

        assertFailsWith<IllegalArgumentException> {
            TtmlLyricParser.parse(nested)
        }
    }

    @Test
    fun rejectsOversizedDocumentsAndDoctypeBeforeOpeningXmlReader() {
        assertFailsWith<IllegalArgumentException> {
            TtmlLyricParser.parse(" ".repeat(2 * 1024 * 1024 + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            TtmlLyricParser.parse(
                """<!DOCTYPE tt [<!ENTITY x SYSTEM "file:///etc/passwd">]><tt>&x;</tt>""",
            )
        }
    }
}
