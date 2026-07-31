/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import com.leejlredstar.redefinencm.kmp.util.LyricParser
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmllWordRendererTest {

    @Test
    fun inactiveAtomSnapshotResetsAndCompletesAcrossSeeks() {
        assertClose(
            1_000.0,
            resolveAmllInactiveAtomPositionMs(
                playbackPositionMs = 500.0,
                lineStartTimeMs = 1_000.0,
                atomEndTimeMs = 2_000.0,
            ),
        )
        assertClose(
            1_500.0,
            resolveAmllInactiveAtomPositionMs(
                playbackPositionMs = 1_500.0,
                lineStartTimeMs = 1_000.0,
                atomEndTimeMs = 2_000.0,
            ),
        )
        assertClose(
            2_000.0,
            resolveAmllInactiveAtomPositionMs(
                playbackPositionMs = 2_500.0,
                lineStartTimeMs = 1_000.0,
                atomEndTimeMs = 2_000.0,
            ),
        )
    }
    @Test
    fun chunkAndSplitPreservesEveryWhitespaceSeparatorAndSourceOrder() {
        val words = listOf(
            word("0", "Life", 0, 400),
            word("1", " ", 400, 400),
            word("2", "is", 400, 600),
            word("3", " a", 600, 800),
            word("4", " su", 800, 1_000),
            word("5", "gar so", 1_000, 1_400),
            word("6", "sweet", 1_400, 1_800),
        )

        val chunks = chunkAndSplitAmllLyricWords(words)

        assertEquals(words.joinToString("") { it.text }, chunks.joinToString("") { it.text })
        // This list follows the implementation, not the stale illustrative comment in upstream:
        // whitespace flushes the mergeable group and remains its own text node.
        assertEquals(
            listOf("Life", " ", "is", " ", "a", " ", "sugar", " ", "sosweet"),
            chunks.map(AmllWordChunk::text),
        )
        assertEquals(listOf("su", "gar"), chunks[6].words.map(AmllSplitWord::text))
        assertEquals(listOf("so", "sweet"), chunks[8].words.map(AmllSplitWord::text))
    }

    @Test
    fun ecmaWhitespaceRunsRemainLosslessAndHaveZeroDuration() {
        val source = "A\u00A0\u3000B"
        val chunks = chunkAndSplitAmllLyricWords(listOf(word("w", source, 100, 300)))

        assertEquals(source, chunks.joinToString("") { it.text })
        assertEquals(listOf("A", "\u00A0\u3000", "B"), chunks.map(AmllWordChunk::text))
        val space = chunks[1].words.single()
        assertClose(space.startTimeMs, space.endTimeMs)
    }

    @Test
    fun supplementaryCjkFollowsUpstreamUtf16CodeUnitSplitAndTiming() {
        val first = "\uD840\uDC00"
        val second = "\uD840\uDC01"
        val utf16Units = (first + second).map { it.toString() }

        val chunks = chunkAndSplitAmllLyricWords(
            listOf(word("cjk", first + second, 0, 1_000)),
        )

        assertEquals(first + second, chunks.joinToString("") { it.text })
        // Upstream calls split(""), so isolated surrogate units are then mergeable non-CJK atoms.
        assertEquals(1, chunks.size)
        assertEquals(utf16Units, chunks.single().words.map(AmllSplitWord::text))
        assertEquals(
            listOf(0.0, 250.0, 500.0, 750.0),
            chunks.single().words.map(AmllSplitWord::startTimeMs),
        )
        assertEquals(
            listOf(250.0, 500.0, 750.0, 1_000.0),
            chunks.single().words.map(AmllSplitWord::endTimeMs),
        )
        assertEquals(utf16Units, splitAmllUtf16CodeUnits(first + second))
    }

    @Test
    fun rubyIsAtomicWhileRomanWordSuppressesCjkSplitAndMetadataSurvives() {
        val ruby = listOf(
            AmllLyricRubySegment(
                text = "かん",
                startTimeMs = 100,
                endTimeMs = 450,
                exactStartTimeMs = 100.25,
                exactEndTimeMs = 449.75,
            ),
        )
        val rubyWord = AmllLyricWord(
            id = "ruby",
            text = "漢 字",
            startTimeMs = 100,
            endTimeMs = 900,
            romanWord = "kan ji",
            obscene = true,
            ruby = ruby,
            exactStartTimeMs = 100.125,
            exactEndTimeMs = 900.875,
        )
        val romanOnly = AmllLyricWord(
            id = "roman",
            text = "世界",
            startTimeMs = 1_000,
            endTimeMs = 1_800,
            romanWord = "sekai",
            obscene = true,
            exactStartTimeMs = 1_000.5,
            exactEndTimeMs = 1_800.5,
        )

        val rubyChunk = chunkAndSplitAmllLyricWords(listOf(rubyWord)).single()
        val rubyAtom = rubyChunk.words.single()
        assertEquals("漢 字", rubyAtom.text)
        assertEquals("kan ji", rubyAtom.romanWord)
        assertTrue(rubyAtom.obscene)
        assertEquals(ruby, rubyAtom.ruby)
        assertClose(100.125, rubyAtom.startTimeMs)
        assertClose(900.875, rubyAtom.endTimeMs)

        val romanChunk = chunkAndSplitAmllLyricWords(listOf(romanOnly)).single()
        val romanAtom = romanChunk.words.single()
        assertEquals("世界", romanAtom.text)
        assertEquals("sekai", romanAtom.romanWord)
        assertTrue(romanAtom.obscene)
        assertClose(1_000.5, romanAtom.startTimeMs)
        assertClose(1_800.5, romanAtom.endTimeMs)
    }

    @Test
    fun lyricParserWordTransportReachesRendererWithoutLosingFractionalMetadata() {
        val sourceRuby = LyricParser.RubySegment(
            startTimeMs = 100,
            endTimeMs = 400,
            text = "\u304B",
            exactStartTimeMs = 100.25,
            exactEndTimeMs = 399.75,
        )
        val sourceLine = LyricParser.WordLine(
            startTimeMs = 100,
            endTimeMs = 901,
            words = listOf(
                LyricParser.Word(
                    startTimeMs = 100,
                    endTimeMs = 901,
                    text = "\u6F22",
                    romanWord = "kan",
                    obscene = true,
                    ruby = listOf(sourceRuby),
                    exactStartTimeMs = 100.125,
                    exactEndTimeMs = 900.875,
                ),
            ),
            exactStartTimeMs = 100.125,
            exactEndTimeMs = 900.875,
        )
        val document = buildAmllLyricDocument(
            lyricMap = emptyMap(),
            wordLines = listOf(sourceLine),
            showRoman = true,
            optimizeOptions = AmllLyricOptimizeOptions(
                normalizeSpaces = false,
                resetLineTimestamps = false,
                convertExcessiveBackgroundLines = false,
                syncMainAndBackgroundLines = false,
                cleanUnintentionalOverlaps = false,
                tryAdvanceStartTime = false,
            ),
        )

        val line = document.lines.single()
        val word = line.words.single()
        assertClose(100.125, line.exactStartTimeMs)
        assertClose(900.875, line.exactEndTimeMs)
        assertClose(100.125, word.exactStartTimeMs)
        assertClose(900.875, word.exactEndTimeMs)
        assertEquals("kan", word.romanWord)
        assertTrue(word.obscene)
        assertEquals(
            AmllLyricRubySegment(
                text = "\u304B",
                startTimeMs = 100,
                endTimeMs = 400,
                exactStartTimeMs = 100.25,
                exactEndTimeMs = 399.75,
            ),
            word.ruby.single(),
        )

        val atom = buildAmllRenderAtoms(
            line = line,
            isNonDynamic = false,
            staticSegments = emptyList(),
            maskObsceneWordsMode = AmllMaskObsceneWordsMode.FULL_MASK,
        ).single()
        assertEquals("*", atom.text)
        assertEquals("kan", atom.romanWord)
        assertTrue(atom.obscene)
        assertEquals(word.ruby, atom.ruby)
        assertClose(100.125, atom.startTimeMs)
        assertClose(900.875, atom.endTimeMs)
    }

    @Test
    fun obsceneWordModesMatchCoreUtf16MaskingRules() {
        val source = "  abc de  "
        assertEquals(
            source,
            processAmllObsceneWord(
                text = source,
                obscene = true,
                mode = AmllMaskObsceneWordsMode.DISABLED,
            ),
        )
        assertEquals(
            "  ### ##  ",
            processAmllObsceneWord(
                text = source,
                obscene = true,
                mode = AmllMaskObsceneWordsMode.FULL_MASK,
                maskCharacter = "#ignored-after-first",
            ),
        )
        assertEquals(
            "  a** *e  ",
            processAmllObsceneWord(
                text = source,
                obscene = true,
                mode = AmllMaskObsceneWordsMode.PARTIAL_MASK,
            ),
        )
        assertEquals(
            "**",
            processAmllObsceneWord(
                text = "ab",
                obscene = true,
                mode = AmllMaskObsceneWordsMode.PARTIAL_MASK,
            ),
        )
        assertEquals(
            source,
            processAmllObsceneWord(
                text = source,
                obscene = false,
                mode = AmllMaskObsceneWordsMode.FULL_MASK,
            ),
        )

        val rendered = buildAmllRenderAtoms(
            line = AmllLyricLine(
                id = "obscene",
                startTimeMs = 0,
                endTimeMs = 1_000,
                mainText = "abc def",
                words = listOf(
                    AmllLyricWord(
                        id = "word",
                        text = "abc def",
                        startTimeMs = 0,
                        endTimeMs = 1_000,
                        obscene = true,
                    ),
                ),
            ),
            isNonDynamic = false,
            staticSegments = emptyList(),
            maskObsceneWordsMode = AmllMaskObsceneWordsMode.PARTIAL_MASK,
        )
        assertEquals("a*c d*f", rendered.joinToString(separator = "") { it.text })
    }

    @Test
    fun graphemeFallbackMatchesUpstreamArrayFromCodePointIteration() {
        val family = "👨‍👩‍👧‍👦"
        val skinTone = "👍🏽"
        val flag = "🇨🇳"
        val combining = "e\u0301"
        val input = family + skinTone + flag + combining

        assertEquals(splitAmllCodePoints(input), splitAmllGraphemesFallback(input))
        assertEquals(13, splitAmllGraphemesFallback(input).size)
        assertEquals(7, splitAmllCodePoints(family).size)
        assertEquals(2, splitAmllCodePoints(skinTone).size)
    }

    @Test
    fun cjkPredicateTracksUnifiedIdeographSupplementaryBoundaries() {
        assertTrue(isAmllCjk(codePointText(0x2EBF0)))
        assertTrue(isAmllCjk(codePointText(0x2EE5D)))
        assertTrue(isAmllCjk(codePointText(0x33479)))

        assertTrue(!isAmllCjk(codePointText(0x2B81E)))
        assertTrue(!isAmllCjk(codePointText(0x2CEAE)))
        assertTrue(!isAmllCjk(codePointText(0x2EBE1)))
        assertTrue(!isAmllCjk(codePointText(0x2EE5E)))
        assertTrue(!isAmllCjk(codePointText(0x3134B)))
        assertTrue(!isAmllCjk(codePointText(0x3347A)))
    }

    @Test
    fun unavailableWordSegmenterCreatesNoSyntheticSegmentsOrLineBalancer() {
        val input = "Hello  世界👨‍👩‍👧‍👦!"
        val unavailable = resolveAmllWordSegmentation(
            source = input,
            platformSegments = null,
        )
        val invalid = resolveAmllWordSegmentation(
            source = input,
            platformSegments = listOf(AmllWordSegment("not-the-source", true)),
        )
        val platform = resolveAmllWordSegmentation(
            source = input,
            platformSegments = listOf(AmllWordSegment(input, true)),
        )
        val unavailableEmpty = resolveAmllWordSegmentation(
            source = "",
            platformSegments = null,
        )
        val platformEmpty = resolveAmllWordSegmentation(
            source = "",
            platformSegments = emptyList(),
        )

        assertEquals(AmllSegmentationBackend.FALLBACK, unavailable.backend)
        assertTrue(unavailable.segments.isEmpty())
        assertEquals(AmllLineBreakStrategy.NATIVE_FLOW, amllLineBreakStrategy(unavailable))
        assertEquals(AmllSegmentationBackend.FALLBACK, invalid.backend)
        assertTrue(invalid.segments.isEmpty())
        assertEquals(AmllSegmentationBackend.PLATFORM, platform.backend)
        assertEquals(listOf(AmllWordSegment(input, true)), platform.segments)
        assertEquals(AmllLineBreakStrategy.BALANCED, amllLineBreakStrategy(platform))
        assertEquals(AmllSegmentationBackend.FALLBACK, unavailableEmpty.backend)
        assertEquals(AmllSegmentationBackend.PLATFORM, platformEmpty.backend)
    }

    @Test
    fun platformSegmentersAreLosslessForWordsAndExtendedGraphemes() {
        val family = "👨‍👩‍👧‍👦"
        val skinTone = "👍🏻"
        val flag = "🇨🇦"
        val combining = "e\u0301"
        val input = "Hello 世界 $family$skinTone$flag$combining"

        val words = segmentAmllWords(input)
        val graphemes = segmentAmllGraphemes(family + skinTone + flag + combining)

        assertEquals(AmllSegmentationBackend.PLATFORM, words.backend)
        assertEquals(input, words.segments.joinToString("") { it.text })
        assertEquals(AmllSegmentationBackend.PLATFORM, graphemes.backend)
        assertEquals(listOf(family, skinTone, flag, combining), graphemes.segments)
    }

    @Test
    fun maskAlphaTargetsAndAttackReleaseMatchUpstreamExponentialStep() {
        assertEquals(
            AmllMaskAlphaState(brightAlpha = 0.2, darkAlpha = 0.2),
            targetAmllMaskAlpha(scale = 0.97, renderMode = AmllLineRenderMode.SOLID),
        )
        assertEquals(
            AmllMaskAlphaState(brightAlpha = 1.0, darkAlpha = 0.4),
            targetAmllMaskAlpha(scale = 1.0, renderMode = AmllLineRenderMode.GRADIENT),
        )
        assertEquals(
            AmllMaskAlphaState(brightAlpha = 0.4, darkAlpha = 0.4),
            targetAmllMaskAlpha(scale = 1.0, renderMode = AmllLineRenderMode.SOLID),
        )

        val attackFactor = 1.0 - exp(-50.0 * 0.016)
        val attacked = advanceAmllMaskAlpha(
            current = AmllMaskAlphaState(0.2, 0.2),
            target = AmllMaskAlphaState(1.0, 0.4),
            deltaSeconds = 0.016,
        )
        assertClose(0.2 + (1.0 - 0.2) * attackFactor, attacked.brightAlpha)
        assertClose(0.2 + (0.4 - 0.2) * attackFactor, attacked.darkAlpha)

        val releaseFactor = 1.0 - exp(-7.0 * 0.016)
        val released = advanceAmllMaskAlpha(
            current = AmllMaskAlphaState(1.0, 0.4),
            target = AmllMaskAlphaState(0.2, 0.2),
            deltaSeconds = 0.016,
        )
        assertClose(1.0 + (0.2 - 1.0) * releaseFactor, released.brightAlpha)
        assertClose(0.4 + (0.2 - 0.4) * releaseFactor, released.darkAlpha)
        assertEquals(
            AmllMaskAlphaState(0.2, 0.2),
            advanceAmllMaskAlpha(
                current = AmllMaskAlphaState(0.2005, 0.1995),
                target = AmllMaskAlphaState(0.2, 0.2),
                deltaSeconds = 0.016,
            ),
        )
        assertEquals(
            AmllMaskAlphaState(1.0, 0.14),
            visibleAmllMaskAlpha(
                animated = AmllMaskAlphaState(0.4567, 0.2345),
                active = true,
                androidPresentation = false,
            ),
        )
        assertEquals(
            AmllMaskAlphaState(1.0, 0.12),
            visibleAmllMaskAlpha(
                animated = AmllMaskAlphaState(0.4567, 0.2345),
                active = true,
                androidPresentation = true,
            ),
        )
        assertEquals(
            AmllMaskAlphaState(0.457, 0.235),
            visibleAmllMaskAlpha(
                animated = AmllMaskAlphaState(0.4567, 0.2346),
                active = false,
                androidPresentation = false,
            ),
        )
    }

    @Test
    fun reverseFloatSamplesTheSameEaseOutTimelineBackToZero() {
        assertClose(0.0, computeAmllNormalFloatProgress(100.0, 200.0, 1_200.0))
        assertClose(0.5, computeAmllNormalFloatProgress(700.0, 200.0, 1_200.0))
        assertClose(1.0, computeAmllNormalFloatProgress(1_500.0, 200.0, 1_200.0))
        assertClose(
            computeAmllNormalFloatOffsetFromProgressEm(0.5, false) * 2.0,
            computeAmllNormalFloatOffsetFromProgressEm(0.5, true),
        )
        assertClose(0.0, computeAmllNormalFloatOffsetFromProgressEm(0.0, false))
    }

    @Test
    fun webMaskUsesOneWholeLineTimelineAndFadeEdgeCorrections() {
        val words = listOf(
            AmllMaskWordMetrics(
                widthPx = 100.0,
                heightPx = 20.0,
                paddingPx = 0.0,
                startTimeMs = 0.0,
                endTimeMs = 1_000.0,
            ),
            AmllMaskWordMetrics(
                widthPx = 50.0,
                heightPx = 20.0,
                paddingPx = 0.0,
                startTimeMs = 1_000.0,
                endTimeMs = 2_000.0,
            ),
        )
        val firstTimeline = buildAmllWebMaskTimeline(words, 0, 0.0, 2_000.0)
        val secondTimeline = buildAmllWebMaskTimeline(words, 1, 0.0, 2_000.0)

        val firstAtStart = sampleAmllWebMaskTimeline(firstTimeline, 0.0, 0.0)
        val firstAfterOwnWord = sampleAmllWebMaskTimeline(firstTimeline, 1_000.0, 0.0)
        val firstAtEnd = sampleAmllWebMaskTimeline(firstTimeline, 2_000.0, 0.0)
        val secondAtStart = sampleAmllWebMaskTimeline(secondTimeline, 0.0, 0.0)
        val secondAfterFirst = sampleAmllWebMaskTimeline(secondTimeline, 1_000.0, 0.0)
        val secondAtEnd = sampleAmllWebMaskTimeline(secondTimeline, 2_000.0, 0.0)

        assertClose(-110.0, firstAtStart.maskPositionPx)
        // width 100 + first-edge fade 15 advances the shared cursor from -120 to -5.
        assertClose(-5.0, firstAfterOwnWord.maskPositionPx)
        // The last-edge fade 5 is applied only while the second word is traversed.
        assertClose(0.0, firstAtEnd.maskPositionPx)
        assertClose(-60.0, secondAtStart.maskPositionPx)
        assertClose(-55.0, secondAfterFirst.maskPositionPx)
        assertClose(0.0, secondAtEnd.maskPositionPx)
        assertClose(-10.0, firstAtStart.brightBoundaryPx)
        assertClose(50.0, secondAtEnd.brightBoundaryPx)

        val paddedTimeline = buildAmllWebMaskTimeline(
            words = listOf(AmllMaskWordMetrics(100.0, 20.0, 20.0, 0.0, 1_000.0)),
            targetWordIndex = 0,
            lineStartTimeMs = 0.0,
            lineEndTimeMs = 1_000.0,
        )
        val paddedStart = sampleAmllWebMaskTimeline(paddedTimeline, 0.0, 0.0)
        val paddedEnd = sampleAmllWebMaskTimeline(paddedTimeline, 1_000.0, 0.0)
        assertClose(-150.0, paddedTimeline.minPositionPx)
        // CSS masks the padded element; Canvas coordinates start at its content-box origin.
        assertClose(-20.0, paddedStart.brightBoundaryPx)
        assertClose(100.0, paddedEnd.brightBoundaryPx)
    }

    @Test
    fun maskTimelineRetainsStaticPausesBetweenWords() {
        val words = listOf(
            AmllMaskWordMetrics(40.0, 20.0, 0.0, 0.0, 500.0),
            AmllMaskWordMetrics(40.0, 20.0, 0.0, 1_000.0, 1_500.0),
        )
        val timeline = buildAmllWebMaskTimeline(words, 1, 0.0, 1_500.0)

        val pauseStart = sampleAmllWebMaskTimeline(timeline, 500.0, 0.0)
        val pauseMiddle = sampleAmllWebMaskTimeline(timeline, 750.0, 0.0)
        val pauseEnd = sampleAmllWebMaskTimeline(timeline, 1_000.0, 0.0)

        assertClose(pauseStart.maskPositionPx, pauseMiddle.maskPositionPx)
        assertClose(pauseStart.maskPositionPx, pauseEnd.maskPositionPx)
    }

    @Test
    fun rubyMaskTimelineUsesSegmentGapsAndUtf16CharacterTiming() {
        val timeline = buildAmllWebMaskTimeline(
            words = listOf(
                AmllMaskWordMetrics(
                    widthPx = 100.0,
                    heightPx = 20.0,
                    paddingPx = 0.0,
                    startTimeMs = 0.0,
                    endTimeMs = 1_000.0,
                    rubySegments = listOf(
                        AmllMaskRubySegment("a", 100.0, 300.0),
                        AmllMaskRubySegment("bc", 500.0, 900.0),
                    ),
                ),
            ),
            targetWordIndex = 0,
            lineStartTimeMs = 0.0,
            lineEndTimeMs = 1_000.0,
        )

        assertClose(-110.0, sampleAmllWebMaskTimeline(timeline, 100.0, 0.0).maskPositionPx)
        assertClose(
            -71.66666666666667,
            sampleAmllWebMaskTimeline(timeline, 300.0, 0.0).maskPositionPx,
        )
        // The 200 ms gap between ruby segments is a true static keyframe interval.
        assertClose(
            -71.66666666666667,
            sampleAmllWebMaskTimeline(timeline, 500.0, 0.0).maskPositionPx,
        )
        assertClose(
            -38.333333333333336,
            sampleAmllWebMaskTimeline(timeline, 700.0, 0.0).maskPositionPx,
        )
        assertClose(0.0, sampleAmllWebMaskTimeline(timeline, 900.0, 0.0).maskPositionPx)
        assertClose(0.0, sampleAmllWebMaskTimeline(timeline, 1_000.0, 0.0).maskPositionPx)
    }

    @Test
    fun emphasisInterpolatesAuthoredThirtyTwoFramesInsteadOfContinuousEasing() {
        val neutral = computeAmllEmphasisFrame(
            positionMs = 0.0,
            mergedStartTimeMs = 0.0,
            mergedEndTimeMs = 1_000.0,
            characterIndex = 0,
            characterCount = 2,
            isLastWordChunk = false,
            isBackground = false,
        )
        val firstAuthored = computeAmllEmphasisFrame(
            positionMs = 1_000.0 / 32.0,
            mergedStartTimeMs = 0.0,
            mergedEndTimeMs = 1_000.0,
            characterIndex = 0,
            characterCount = 2,
            isLastWordChunk = false,
            isBackground = false,
        )
        val halfway = computeAmllEmphasisFrame(
            positionMs = 1_000.0 / 64.0,
            mergedStartTimeMs = 0.0,
            mergedEndTimeMs = 1_000.0,
            characterIndex = 0,
            characterCount = 2,
            isLastWordChunk = false,
            isBackground = false,
        )

        assertClose((neutral.scale + firstAuthored.scale) / 2.0, halfway.scale)
        assertClose(
            (neutral.offsetXEm + firstAuthored.offsetXEm) / 2.0,
            halfway.offsetXEm,
        )
        assertClose((neutral.glowAlpha + firstAuthored.glowAlpha) / 2.0, halfway.glowAlpha)
    }

    @Test
    fun rubyCharacterCountIsTheEmphasisDelayAnchor() {
        val baseCharacterAnchor = computeAmllEmphasisFrame(
            positionMs = 150.0,
            mergedStartTimeMs = 0.0,
            mergedEndTimeMs = 1_000.0,
            characterIndex = 1,
            characterCount = 2,
            anchorCharacterCount = 2,
            isLastWordChunk = false,
            isBackground = false,
        )
        val rubyCharacterAnchor = computeAmllEmphasisFrame(
            positionMs = 150.0,
            mergedStartTimeMs = 0.0,
            mergedEndTimeMs = 1_000.0,
            characterIndex = 1,
            characterCount = 2,
            anchorCharacterCount = 4,
            isLastWordChunk = false,
            isBackground = false,
        )

        // Base anchor delay is 200 ms, ruby anchor delay is 100 ms.
        assertClose(1.0, baseCharacterAnchor.scale)
        assertTrue(rubyCharacterAnchor.scale > 1.0)
    }

    @Test
    fun renderAtomsCarryLineWideRubyRomanStructureAndUnfilteredRubyAnchorCount() {
        val line = AmllLyricLine(
            id = "line",
            startTimeMs = 0,
            endTimeMs = 2_000,
            mainText = "漢字",
            words = listOf(
                AmllLyricWord(
                    id = "first",
                    text = "漢",
                    startTimeMs = 0,
                    endTimeMs = 1_200,
                    ruby = listOf(
                        AmllLyricRubySegment("か", 0, 600),
                        // `getRubyCharCount()` counts this even though mask/layout filtering drops it.
                        AmllLyricRubySegment(" ", 600, 1_200),
                    ),
                ),
                AmllLyricWord(
                    id = "second",
                    text = "字",
                    startTimeMs = 1_200,
                    endTimeMs = 2_000,
                    romanWord = "ji",
                ),
            ),
        )

        val atoms = buildAmllRenderAtoms(
            line = line,
            isNonDynamic = false,
            staticSegments = emptyList(),
        )

        assertTrue(atoms.all(AmllRenderAtom::hasRubyLine))
        assertTrue(atoms.all(AmllRenderAtom::hasRomanLine))
        assertEquals(listOf("か", " "), atoms.first().ruby.map(AmllLyricRubySegment::text))
        assertEquals(2, atoms.first().emphasisAnchorCount)
        assertEquals("ji", atoms.last().romanWord)
        assertEquals(
            "\u00A0",
            amllRomanWordDisplayText(
                romanWord = atoms.first().romanWord,
                hasRomanLine = atoms.first().hasRomanLine,
            ),
        )
        assertEquals(
            "ji",
            amllRomanWordDisplayText(
                romanWord = atoms.last().romanWord,
                hasRomanLine = atoms.last().hasRomanLine,
            ),
        )
        assertEquals(null, amllRomanWordDisplayText("unused", hasRomanLine = false))
    }

    @Test
    fun wordBodyGlowConstantsMatchTheFixedHostSelectors() {
        val desktop = amllWordBodyGlowSpec(androidPresentation = false)
        assertClose(0.98, desktop.foregroundAlpha)
        assertClose(0.55, desktop.strokeWidthPx)
        assertClose(0.58, desktop.strokeAlpha)
        assertEquals(listOf(3.0, 14.0, 30.0), desktop.textShadows.map { it.blurRadiusPx })
        assertClose(10.0, desktop.dropShadow.blurRadiusPx)
        assertClose(0.52, desktop.dropShadow.alpha)

        val android = amllWordBodyGlowSpec(androidPresentation = true)
        assertClose(0.96, android.foregroundAlpha)
        assertClose(0.45, android.strokeWidthPx)
        assertClose(0.52, android.strokeAlpha)
        assertEquals(listOf(3.0, 10.0, 22.0), android.textShadows.map { it.blurRadiusPx })
        assertClose(7.0, android.dropShadow.blurRadiusPx)
        assertClose(0.42, android.dropShadow.alpha)
    }

    @Test
    fun backgroundRuleDoublesOnlyTheEmphasisFloatKeyframes() {
        val foreground = emphasisAt(isBackground = false)
        val background = emphasisAt(isBackground = true)

        assertClose(foreground.scale, background.scale)
        assertClose(foreground.offsetXEm, background.offsetXEm)
        assertClose(foreground.offsetYEm, background.offsetYEm)
        assertClose(foreground.glowAlpha, background.glowAlpha)
        assertClose(foreground.floatOffsetYEm * 2.0, background.floatOffsetYEm)
    }

    @Test
    fun emphasisTransformScalesBothChildTranslationsButNotParentWordFloat() {
        val frame = AmllEmphasisFrame(
            scale = 2.0,
            offsetXEm = 3.0,
            offsetYEm = 4.0,
            floatOffsetYEm = 5.0,
            glowAlpha = 0.0,
            glowRadiusEm = 0.0,
            underlyingStyleWeight = 0.0,
            firstAuthoredGlowAlpha = 0.0,
        )

        val transformedOrigin = transformAmllEmphasisPoint(
            point = AmllTransformPoint(10.0, 20.0),
            origin = AmllTransformPoint(10.0, 20.0),
            parentFloatYEm = 7.0,
            frame = frame,
        )
        val transformedOffsetPoint = transformAmllEmphasisPoint(
            point = AmllTransformPoint(11.0, 22.0),
            origin = AmllTransformPoint(10.0, 20.0),
            parentFloatYEm = 7.0,
            frame = frame,
        )

        // CSS matrix(scale) translate(glow) followed by additive translate(float):
        // child translations are inside scale; the parent word translation remains outside.
        assertEquals(AmllTransformPoint(16.0, 45.0), transformedOrigin)
        assertEquals(AmllTransformPoint(18.0, 49.0), transformedOffsetPoint)
    }

    @Test
    fun emphasisNeutralKeyframeInterpolatesInheritedHostShadowStack() {
        fun frameAt(positionMs: Double) = computeAmllEmphasisFrame(
            positionMs = positionMs,
            mergedStartTimeMs = 0.0,
            mergedEndTimeMs = 1_000.0,
            characterIndex = 0,
            characterCount = 1,
            isLastWordChunk = false,
            isBackground = false,
            lineStartTimeMs = 0.0,
        )

        val neutral = frameAt(0.0)
        val halfway = frameAt(1_000.0 / 64.0)
        val firstAuthored = frameAt(1_000.0 / 32.0)
        val neutralHost = computeAmllEmphasisShadows(
            frame = neutral,
            active = true,
            androidPresentation = false,
            fontSizePx = 40.0,
        )
        val halfwayHost = computeAmllEmphasisShadows(
            frame = halfway,
            active = true,
            androidPresentation = false,
            fontSizePx = 40.0,
        )
        val firstAuthoredHost = computeAmllEmphasisShadows(
            frame = firstAuthored,
            active = true,
            androidPresentation = false,
            fontSizePx = 40.0,
        )

        assertClose(1.0, neutral.underlyingStyleWeight)
        assertEquals(2, neutralHost.size)
        assertClose(0.35, neutralHost[0].alpha)
        assertClose(10.0, neutralHost[0].blurRadiusPx)
        assertClose(0.35, neutralHost[1].alpha)
        assertClose(4.0, neutralHost[1].offsetYpx)
        assertClose(24.0, neutralHost[1].blurRadiusPx)

        assertClose(0.5, halfway.underlyingStyleWeight)
        assertEquals(2, halfwayHost.size)
        assertClose(0.175, halfwayHost[1].alpha)
        assertClose(2.0, halfwayHost[1].offsetYpx)
        assertClose(12.0, halfwayHost[1].blurRadiusPx)

        assertClose(0.0, firstAuthored.underlyingStyleWeight)
        assertEquals(1, firstAuthoredHost.size)
        assertClose(firstAuthored.glowAlpha, firstAuthoredHost.single().alpha)
        assertClose(
            firstAuthored.glowRadiusEm * 40.0,
            firstAuthoredHost.single().blurRadiusPx,
        )

        val halfwayAndroidInactive = computeAmllEmphasisShadows(
            frame = halfway,
            active = false,
            androidPresentation = true,
            fontSizePx = 40.0,
        ).single()
        val expectedAlpha = (0.55 + halfway.firstAuthoredGlowAlpha) / 2.0
        val expectedPremultipliedChannel =
            (halfway.firstAuthoredGlowAlpha / 2.0) / expectedAlpha
        assertClose(expectedAlpha, halfwayAndroidInactive.alpha)
        assertClose(expectedPremultipliedChannel, halfwayAndroidInactive.red)
        assertClose(expectedPremultipliedChannel, halfwayAndroidInactive.green)
        assertClose(expectedPremultipliedChannel, halfwayAndroidInactive.blue)
    }

    @Test
    fun balancedWrappingUsesTheSameDynamicProgramForStaticSegments() {
        val nodes = listOf(
            AmllBalanceNode(40.0, "A", false),
            AmllBalanceNode(10.0, " ", true),
            AmllBalanceNode(40.0, "B", false),
            AmllBalanceNode(10.0, " ", true),
            AmllBalanceNode(40.0, "C", false),
        )

        assertEquals(setOf(2, 4), calculateAmllBalancedBreaks(nodes, 70.0))
        assertTrue(calculateAmllBalancedBreaks(nodes, 140.0).isEmpty())
    }

    @Test
    fun nativeFlowWrapsGreedilyWithoutRunningTheBalanceProgram() {
        val nodes = listOf(
            AmllBalanceNode(45.0, "A", false),
            AmllBalanceNode(15.0, " ", true),
            AmllBalanceNode(35.0, "B", false),
            AmllBalanceNode(25.0, "C", false),
        )

        assertEquals(setOf(2), calculateAmllNativeFlowBreaks(nodes, 60.0))
        assertTrue(calculateAmllNativeFlowBreaks(nodes, 120.0).isEmpty())
        assertEquals(
            setOf(2),
            calculateAmllNativeFlowBreaks(
                nodes = listOf(
                    AmllBalanceNode(60.0, "full", false),
                    AmllBalanceNode(10.0, " ", true),
                    AmllBalanceNode(60.0, "next", false),
                ),
                containerWidthPx = 60.0,
            ),
        )
    }

    @Test
    fun duetRowsUsePhysicalRightOriginIndependentOfLayoutDirection() {
        assertEquals(0, amllPhysicalRowStartX(300, 120, isDuet = false))
        assertEquals(180, amllPhysicalRowStartX(300, 120, isDuet = true))
    }

    private fun emphasisAt(isBackground: Boolean): AmllEmphasisFrame =
        computeAmllEmphasisFrame(
            positionMs = 600.0,
            mergedStartTimeMs = 200.0,
            mergedEndTimeMs = 1_400.0,
            characterIndex = 1,
            characterCount = 4,
            isLastWordChunk = false,
            isBackground = isBackground,
            lineStartTimeMs = 100.0,
        )

    private fun word(
        id: String,
        text: String,
        startTimeMs: Long,
        endTimeMs: Long,
    ): AmllLyricWord = AmllLyricWord(
        id = id,
        text = text,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
    )

    private fun codePointText(codePoint: Int): String {
        require(codePoint in 0x10000..0x10FFFF)
        val value = codePoint - 0x10000
        return buildString(2) {
            append(((value ushr 10) + 0xD800).toChar())
            append(((value and 0x3FF) + 0xDC00).toChar())
        }
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-7,
    ) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "Expected $expected, actual $actual",
        )
    }
}
