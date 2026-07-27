package com.leejlredstar.redefinencm.kmp.ui.amll

import com.leejlredstar.redefinencm.kmp.util.LyricParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmllLyricModelTest {

    @Test
    fun losslessLrcBuildRetainsDuplicatesBackgroundAndExactGroups() {
        val lrcLines = LyricParser.parseLrcLines(
            """
            [00:01.000]main
            [00:01.000](echo)
            [00:04.000]next
            """.trimIndent(),
        )

        val document = buildAmllLyricDocument(
            lyricMap = emptyMap(),
            wordLines = emptyList(),
            lrcLines = lrcLines,
        )

        assertEquals(3, document.lines.size)
        assertEquals(
            listOf("line:1000:0", "line:1000:1", "line:4000:0"),
            document.lines.map { it.id },
        )
        assertEquals(listOf(false, true, false), document.lines.map { it.isBackground })
        assertEquals(2, document.groups.size)
        assertEquals("main", document.groups[0].mainLine.mainText)
        assertEquals("echo", document.groups[0].backgroundLine?.mainText)
        assertFalse(document.groups[0].isBackgroundFirst)
        assertEquals(400.0, document.groups[0].exactStartTimeMs)
        assertTrue(document.interludes.isEmpty())
        assertTrue(document.lines.all { it.interludeBefore == null })
    }

    @Test
    fun yrcIsPrimaryAndKeepsBridgeWordTimings() {
        val wordLines = LyricParser.parseYrc(
            """
            [1000,1200](1000,400,0)one (1400,800,0)line
            [6500,1000](6500,500,0)next(7000,500,0) line
            """.trimIndent(),
        )

        val document = buildAmllLyricDocument(
            lyricMap = linkedMapOf(500L to "map must not win"),
            wordLines = wordLines,
            lrcLines = LyricParser.parseLrcLines("[00:00.750]lrc must not win"),
        )
        val first = document.lines.first()

        assertTrue(document.hasWordTiming)
        assertEquals("one line", first.mainText)
        assertEquals(2, first.words.size)
        assertEquals(1_000L, first.words[0].startTimeMs)
        assertEquals(1_400L, first.words[1].startTimeMs)
        assertEquals("line:1000:0:word:1000:0", first.words[0].id)
        assertEquals(2_200L, first.endTimeMs)
        assertEquals(400.0, first.exactStartTimeMs)
        assertEquals(1_000L, first.sourceStartTimeMs)
    }

    @Test
    fun legacyMapRemainsACompatibleFallback() {
        val document = buildAmllLyricDocument(
            lyricMap = linkedMapOf(
                1_000L to "first",
                3_000L to "second",
            ),
            wordLines = emptyList(),
        )

        assertEquals(listOf("first", "second"), document.lines.map { it.mainText })
        assertFalse(document.hasWordTiming)
        assertEquals(1, document.lines[0].words.size)
    }

    @Test
    fun supplementsUseOriginalTimestampAndExact850MillisecondRule() {
        val lrcLines = LyricParser.parseLrcLines(
            """
            [00:01.000]first
            [00:03.000]second
            [00:05.000]third
            """.trimIndent(),
        )
        val translated = """
            [00:00.150]first boundary
            [00:01.300]first nearer
            [00:03.200]second near
        """.trimIndent()
        val roman = """
            [00:01.100]roman first
            [00:05.851]outside
        """.trimIndent()

        val shown = buildAmllLyricDocument(
            lyricMap = emptyMap(),
            wordLines = emptyList(),
            lrcLines = lrcLines,
            translatedLrc = translated,
            romanLrc = roman,
            showTranslated = true,
            showRoman = true,
        )
        val hidden = buildAmllLyricDocument(
            lyricMap = emptyMap(),
            wordLines = emptyList(),
            lrcLines = lrcLines,
            translatedLrc = translated,
            romanLrc = roman,
        )

        assertEquals("first nearer", shown.lines[0].translatedText)
        assertEquals("second near", shown.lines[1].translatedText)
        assertEquals("roman first", shown.lines[0].romanText)
        assertNull(shown.lines[2].romanText)
        assertNull(hidden.lines[0].translatedText)
        assertNull(hidden.lines[0].romanText)
    }

    @Test
    fun hiddenSupplementPreferencesAlsoSuppressEmbeddedLineAndWordMetadata() {
        val source = LyricParser.WordLine(
            startTimeMs = 1_000L,
            endTimeMs = 2_000L,
            words = listOf(
                LyricParser.Word(
                    startTimeMs = 1_000L,
                    endTimeMs = 2_000L,
                    text = "主词",
                    romanWord = "romaji",
                ),
            ),
            translatedText = "embedded translation",
            romanText = "embedded romanization",
        )

        val hidden = buildAmllLyricDocument(
            lyricMap = emptyMap(),
            wordLines = listOf(source),
        ).lines.single()
        val shown = buildAmllLyricDocument(
            lyricMap = emptyMap(),
            wordLines = listOf(source),
            showTranslated = true,
            showRoman = true,
        ).lines.single()

        assertNull(hidden.translatedText)
        assertNull(hidden.romanText)
        assertNull(hidden.words.single().romanWord)
        assertEquals("embedded translation", shown.translatedText)
        assertEquals("embedded romanization", shown.romanText)
        assertEquals("romaji", shown.words.single().romanWord)
    }

    @Test
    fun supplementTieKeepsTheFirstSortedCandidateLikePlayerHtml() {
        val document = buildAmllLyricDocument(
            lyricMap = emptyMap(),
            wordLines = emptyList(),
            lrcLines = LyricParser.parseLrcLines("[00:02.000]line"),
            translatedLrc = """
            [00:01.700]earlier
            [00:02.300]later
            """.trimIndent(),
            showTranslated = true,
        )

        assertEquals("earlier", document.lines.single().translatedText)
    }

    @Test
    fun wordProgressIsClampedAndHandlesZeroDuration() {
        val word = AmllLyricWord(
            id = "word",
            text = "hello",
            startTimeMs = 1_000L,
            endTimeMs = 2_000L,
        )

        assertEquals(0f, calculateAmllWordProgress(word, 999L))
        assertEquals(0f, calculateAmllWordProgress(word, 1_000L))
        assertEquals(0.5f, calculateAmllWordProgress(word, 1_500L))
        assertEquals(1f, calculateAmllWordProgress(word, 2_000L))
        assertEquals(1f, calculateAmllWordProgress(word, 9_000L))
        assertEquals(
            0f,
            calculateAmllWordProgress(positionMs = 999L, startTimeMs = 1_000L, endTimeMs = 1_000L),
        )
        assertEquals(
            1f,
            calculateAmllWordProgress(positionMs = 1_000L, startTimeMs = 1_000L, endTimeMs = 1_000L),
        )
    }

    @Test
    fun activeIndexesUseTheOptimizedTimelineAndLeaveLeadInInactive() {
        val document = buildAmllLyricDocument(
            lyricMap = emptyMap(),
            wordLines = emptyList(),
            lrcLines = LyricParser.parseLrcLines(
                """
                [00:01.000]first
                [00:03.000]second
                """.trimIndent(),
            ),
        )

        assertEquals(-1, document.activeLineIndexAt(399L))
        assertEquals(0, document.activeLineIndexAt(400L))
        assertEquals(0, document.activeGroupIndexAt(2_999L))
        assertEquals(1, document.activeGroupIndexAt(3_000L))
        assertEquals(-1, AmllLyricDocument(emptyList()).activeLineIndexAt(3_000L))
    }

    @Test
    fun responsiveParametersFollowAmllBreakpoints() {
        val phone = calculateAmllLyricVisualParameters(
            viewportWidthDp = 360f,
            viewportHeightDp = 800f,
            reducedMotion = false,
        )
        val desktop = calculateAmllLyricVisualParameters(
            viewportWidthDp = 1_200f,
            viewportHeightDp = 800f,
            reducedMotion = false,
        )

        assertEquals(AmllLyricViewportClass.NARROW, phone.viewportClass)
        assertEquals(28.8f, phone.baseFontSizeSp, absoluteTolerance = 0.001f)
        assertEquals(1f, phone.lineWidthFraction)
        assertEquals(20f, phone.horizontalPaddingDp)
        assertEquals(0.8f, phone.blurDistanceScale)

        assertEquals(AmllLyricViewportClass.WIDE, desktop.viewportClass)
        assertEquals(40f, desktop.baseFontSizeSp)
        // In core 0.5.2 the CSS aspect variable is declared, but the DOM assignment that
        // consumes it is commented out; the effective line width therefore stays 100%.
        assertEquals(1f, desktop.lineWidthFraction)
        assertEquals(40f, desktop.horizontalPaddingDp)
        assertEquals(0.88f, desktop.backgroundLineOpacity)
        assertEquals(1f, desktop.blurDistanceScale)
    }

    @Test
    fun reducedMotionDisablesSpringScrollAndTransitionsOnly() {
        val normal = calculateAmllLyricVisualParameters(900f, 700f, reducedMotion = false)
        val reduced = calculateAmllLyricVisualParameters(900f, 700f, reducedMotion = true)

        assertTrue(normal.springEnabled)
        assertTrue(normal.animatedScrollEnabled)
        assertEquals(400, normal.transitionDurationMs)
        assertFalse(reduced.springEnabled)
        assertFalse(reduced.animatedScrollEnabled)
        assertEquals(0, reduced.transitionDurationMs)
        assertEquals(normal.baseFontSizeSp, reduced.baseFontSizeSp)
        assertEquals(normal.lineWidthFraction, reduced.lineWidthFraction)
    }

    @Test
    fun backgroundSpecsIncludePlayerHtmlOverscanAndPlatformFilters() {
        val desktop = amllBackgroundVisualSpec(androidPresentation = false)
        val android = amllBackgroundVisualSpec(androidPresentation = true)

        assertEquals(48f, desktop.blurRadiusDp)
        assertEquals(1.32f, desktop.combinedScale, absoluteTolerance = 0.0001f)
        assertEquals(1.30f, desktop.saturation)
        assertEquals(0.55f, desktop.brightness)

        assertEquals(24f, android.blurRadiusDp)
        assertEquals(1.272f, android.combinedScale, absoluteTolerance = 0.0001f)
        assertEquals(1.15f, android.saturation)
        assertEquals(0.46f, android.brightness)
        assertEquals(0.62f, android.scrimStartAlpha)
        assertEquals(0.56f, android.scrimEndAlpha)
    }
}
