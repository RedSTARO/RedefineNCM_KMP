package com.leejlredstar.redefinencm.kmp.ui.amll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmllLyricOptimizerTest {

    @Test
    fun normalizeSpacesAndResetLineTimestampsMatchFirstTwoPasses() {
        val singleUntimedWord = line(
            id = "single",
            start = 1_000,
            end = 2_000,
            words = listOf(word("a\u00A0\u3000\tb", 0, 0)),
        )
        val timedWords = line(
            id = "timed",
            start = 500,
            end = 900,
            words = listOf(
                word("first", 1_000, 1_100),
                word("second", 1_200, 1_300),
            ),
        )

        val result = optimizeAmllLyricLines(
            listOf(singleUntimedWord, timedWords),
            only(normalizeSpaces = true, resetLineTimestamps = true),
        )

        assertEquals("a b", result[0].mainText)
        assertEquals(1_000L, result[0].words.single().startTimeMs)
        assertEquals(2_000L, result[0].words.single().endTimeMs)
        assertEquals(1_000L, result[1].startTimeMs)
        assertEquals(1_300L, result[1].endTimeMs)
    }

    @Test
    fun consecutiveBackgroundLinesBecomeOneBackgroundThenMain() {
        val result = optimizeAmllLyricLines(
            listOf(
                line("main", 1_000, 2_000),
                line("bg-1", 1_100, 1_900, isBackground = true),
                line("bg-2", 1_200, 1_800, isBackground = true),
            ),
            only(convertExcessiveBackgroundLines = true),
        )

        assertEquals(listOf(false, true, false), result.map { it.isBackground })
        val groups = groupAmllLyricLines(result)
        assertEquals(2, groups.size)
        assertEquals("bg-1", groups[0].backgroundLine?.id)
        assertEquals("bg-2", groups[1].mainLine.id)
    }

    @Test
    fun mainAndAttachedBackgroundUseEarliestStartAndLatestEnd() {
        val result = optimizeAmllLyricLines(
            listOf(
                line(
                    id = "main",
                    start = 1_000,
                    end = 2_000,
                    words = listOf(word("main", 1_200, 1_800)),
                ),
                line(
                    id = "bg",
                    start = 1_100,
                    end = 2_100,
                    isBackground = true,
                    words = listOf(word("echo", 900, 2_200)),
                ),
            ),
            only(syncMainAndBackgroundLines = true),
        )

        assertEquals(900.0, result[0].exactStartTimeMs)
        assertEquals(2_200.0, result[0].exactEndTimeMs)
        assertEquals(900.0, result[1].exactStartTimeMs)
        assertEquals(2_200.0, result[1].exactEndTimeMs)
        assertTrue(groupAmllLyricLines(result).single().isBackgroundFirst)
    }

    @Test
    fun smallOverlapIsCleanedOnMainAndAttachedBackground() {
        val result = optimizeAmllLyricLines(
            listOf(
                line("main", 1_000, 2_100),
                line("bg", 1_100, 2_100, isBackground = true),
                line("next", 2_000, 3_000),
            ),
            only(cleanUnintentionalOverlaps = true),
        )

        assertEquals(2_000L, result[0].endTimeMs)
        assertEquals(2_000L, result[1].endTimeMs)
    }

    @Test
    fun largeOverlapOverBothThresholdsIsPreserved() {
        val result = optimizeAmllLyricLines(
            listOf(
                line("main", 1_000, 2_201),
                line("next", 2_000, 3_000),
            ),
            only(cleanUnintentionalOverlaps = true),
        )

        assertEquals(2_201L, result[0].endTimeMs)
    }

    @Test
    fun startAdvanceUsesGapAndOverlapBoundariesAndCopiesToBackground() {
        val result = optimizeAmllLyricLines(
            listOf(
                line("first", 1_001, 2_002),
                line("second", 1_500, 2_500),
                line("second-bg", 1_550, 2_400, isBackground = true),
            ),
            only(tryAdvanceStartTime = true),
        )

        assertEquals(401.0, result[0].exactStartTimeMs)
        assertEquals(1_301.3, result[1].exactStartTimeMs, absoluteTolerance = 0.000_001)
        assertEquals(result[1].exactStartTimeMs, result[2].exactStartTimeMs)
        assertEquals(1_301L, result[1].startTimeMs)
    }

    @Test
    fun startAdvanceWithGapCannotCrossPreviousMainGroupEnd() {
        val result = optimizeAmllLyricLines(
            listOf(
                line("first", 1_000, 2_000),
                line("second", 3_000, 4_000),
            ),
            only(tryAdvanceStartTime = true),
        )

        assertEquals(400L, result[0].startTimeMs)
        assertEquals(2_400L, result[1].startTimeMs)
        assertFalse(result[0].isBackground)
    }

    @Test
    fun optimizerRoundTripPreservesFractionalWordMetadataAndRuby() {
        val ruby = listOf(
            AmllLyricRubySegment(
                text = "かな",
                startTimeMs = 1_000,
                endTimeMs = 1_900,
                exactStartTimeMs = 1_000.25,
                exactEndTimeMs = 1_900.75,
            ),
        )
        val sourceWord = AmllLyricWord(
            id = "word",
            text = "仮名",
            startTimeMs = 1_000,
            endTimeMs = 2_000,
            romanWord = "kana",
            obscene = true,
            ruby = ruby,
            exactStartTimeMs = 1_000.125,
            exactEndTimeMs = 2_000.875,
        )

        val result = optimizeAmllLyricLines(
            lines = listOf(line("line", 1_000, 2_001, words = listOf(sourceWord))),
            options = only(),
        ).single().words.single()

        assertEquals("kana", result.romanWord)
        assertTrue(result.obscene)
        assertEquals(ruby, result.ruby)
        assertEquals(1_000.125, result.exactStartTimeMs)
        assertEquals(2_000.875, result.exactEndTimeMs)
        // Compatibility timestamps remain rounded without becoming the renderer's source of truth.
        assertEquals(1_000L, result.startTimeMs)
        assertEquals(2_001L, result.endTimeMs)
    }

    private fun line(
        id: String,
        start: Long,
        end: Long,
        isBackground: Boolean = false,
        words: List<AmllLyricWord> = listOf(word(id, start, end)),
    ): AmllLyricLine = AmllLyricLine(
        id = id,
        startTimeMs = start,
        endTimeMs = end,
        mainText = words.joinToString(separator = "") { it.text },
        words = words,
        isBackground = isBackground,
    )

    private companion object {
        fun word(text: String, start: Long, end: Long): AmllLyricWord = AmllLyricWord(
            id = "$text:$start:$end",
            text = text,
            startTimeMs = start,
            endTimeMs = end,
        )

        fun only(
            normalizeSpaces: Boolean = false,
            resetLineTimestamps: Boolean = false,
            convertExcessiveBackgroundLines: Boolean = false,
            syncMainAndBackgroundLines: Boolean = false,
            cleanUnintentionalOverlaps: Boolean = false,
            tryAdvanceStartTime: Boolean = false,
        ): AmllLyricOptimizeOptions = AmllLyricOptimizeOptions(
            normalizeSpaces = normalizeSpaces,
            resetLineTimestamps = resetLineTimestamps,
            convertExcessiveBackgroundLines = convertExcessiveBackgroundLines,
            syncMainAndBackgroundLines = syncMainAndBackgroundLines,
            cleanUnintentionalOverlaps = cleanUnintentionalOverlaps,
            tryAdvanceStartTime = tryAdvanceStartTime,
        )
    }
}
