/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * This parser is a Kotlin translation of @applemusic-like-lyrics/ttml 1.0.1
 * (parser.ts and utils/amll-converter.ts), fixed at upstream commit
 * 36e57035a735596479abe943f78846b8d1e78afc.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.lyric

import com.leejlredstar.redefinencm.kmp.util.LyricParser
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Bounded common Kotlin translation of AMLL TTML 1.0.1's `parseTTML`.
 *
 * The observable lyric result follows upstream `TTMLParser.parse()` followed by
 * `toAmllLyrics()` without language-selection options. XML is first materialized into a small
 * bounded tree because upstream's DOM traversal needs Head sidecars before Body conversion.
 * The pre-existing 2 MiB, declaration, depth, element, line and word limits remain enforced.
 */
object TtmlLyricParser {
    fun parse(ttml: String): List<LyricParser.WordLine> {
        require(ttml.length <= MAX_TTML_CHARS) { "TTML document is too large" }
        require(!ttml.contains("<!DOCTYPE", ignoreCase = true)) {
            "TTML document declarations are not supported"
        }

        val root = readBoundedTree(ttml)
        require(root.name == ELEMENT_TT) { "Not a TTML document" }

        val (agents, sidecar) = parseHead(root)
        val parsedLines = parseBody(root, sidecar)
        return convertToAmllLines(parsedLines, agents).also(::enforceOutputLimits)
    }

    /**
     * Upstream 1.0.1 applies JavaScript `Math.round(seconds * 1000)`. The returned Double is
     * therefore an exact transport of that already-rounded JavaScript number, not the unrounded
     * fractional millisecond from the source spelling.
     */
    internal fun parseTtmlTimeExact(value: String?): Double {
        if (value == null) return 0.0
        val clean = value.trim()
        if (clean.isEmpty()) return 0.0

        if (clean.endsWith("s")) {
            val seconds = clean.dropLast(1).toDoubleOrNull() ?: return 0.0
            if (seconds.isNaN()) return 0.0
            return jsMathRound(seconds * 1_000.0)
        }

        val match = TIME_REGEX.matchEntire(clean) ?: return 0.0
        val seconds = match.groupValues[3].toDoubleOrNull() ?: return 0.0
        val minutes = match.groupValues[2].takeIf(String::isNotEmpty)
            ?.toDoubleOrNull()
            ?: 0.0
        val hours = match.groupValues[1].takeIf(String::isNotEmpty)
            ?.toDoubleOrNull()
            ?: 0.0
        return jsMathRound((hours * 3_600.0 + minutes * 60.0 + seconds) * 1_000.0)
    }

    internal fun parseTtmlTime(value: String): Long =
        parseTtmlTimeExact(value).toCompatibilityLong()

    private fun readBoundedTree(ttml: String): XmlElement {
        val reader = xmlStreaming.newReader(ttml)
        val stack = mutableListOf<XmlElement>()
        var root: XmlElement? = null
        var elementCount = 0

        try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    EventType.START_ELEMENT -> {
                        elementCount += 1
                        require(elementCount <= MAX_ELEMENT_COUNT) {
                            "TTML has too many elements"
                        }
                        require(stack.size + 1 <= MAX_ELEMENT_DEPTH) {
                            "TTML nesting is too deep"
                        }
                        if (reader.localName == ELEMENT_SPAN) {
                            val spanDepth = stack.count { it.name == ELEMENT_SPAN } + 1
                            require(spanDepth <= MAX_SPAN_DEPTH) {
                                "TTML span nesting is too deep"
                            }
                        }

                        val attributes = linkedMapOf<String, String>()
                        for (index in 0 until reader.attributeCount) {
                            // Upstream getAttr() falls back to matching an attribute's local name,
                            // so a local-name map preserves its effective parser behavior.
                            attributes[reader.getAttributeLocalName(index)] =
                                reader.getAttributeValue(index)
                        }
                        val element = XmlElement(
                            name = reader.localName,
                            attributes = attributes,
                        )
                        if (stack.isEmpty()) {
                            require(root == null) { "TTML has more than one root element" }
                            root = element
                        } else {
                            stack.last().children += element
                        }
                        stack += element
                    }

                    EventType.TEXT,
                    EventType.CDSECT,
                    EventType.ENTITY_REF,
                    EventType.IGNORABLE_WHITESPACE,
                    -> stack.lastOrNull()?.children?.add(XmlText(reader.text))

                    EventType.END_ELEMENT -> {
                        require(stack.isNotEmpty()) { "Malformed TTML element stack" }
                        stack.removeAt(stack.lastIndex)
                    }

                    else -> Unit
                }
            }
        } finally {
            reader.close()
        }

        require(stack.isEmpty()) { "Unclosed TTML element" }
        return requireNotNull(root) { "Not a TTML document" }
    }

    private fun parseHead(root: XmlElement): Pair<Map<String, Agent>, Map<String, Sidecar>> {
        val head = root.descendants(ELEMENT_HEAD).firstOrNull()
            ?: return emptyMap<String, Agent>() to emptyMap()
        val agents = linkedMapOf<String, Agent>()

        head.descendants(ELEMENT_AGENT).forEach { element ->
            val id = element.attribute(ATTRIBUTE_ID)
            if (!id.isNullOrEmpty()) {
                agents[id] = Agent(
                    type = element.attribute(ATTRIBUTE_TYPE),
                )
            }
        }

        val sidecar = linkedMapOf<String, Sidecar>()
        head.descendants(ELEMENT_ITUNES_METADATA).forEach { metadata ->
            parseSidecarContainer(
                metadata = metadata,
                containerName = ELEMENT_TRANSLATIONS,
                itemName = ELEMENT_TRANSLATION,
                isRomanization = false,
                sidecar = sidecar,
            )
            parseSidecarContainer(
                metadata = metadata,
                containerName = ELEMENT_TRANSLITERATIONS,
                itemName = ELEMENT_TRANSLITERATION,
                isRomanization = true,
                sidecar = sidecar,
            )
        }
        return agents to sidecar
    }

    private fun parseSidecarContainer(
        metadata: XmlElement,
        containerName: String,
        itemName: String,
        isRomanization: Boolean,
        sidecar: MutableMap<String, Sidecar>,
    ) {
        val container = metadata.descendants(containerName).firstOrNull() ?: return
        container.descendants(itemName).forEach { item ->
            val language = item.attribute(ATTRIBUTE_LANG)
            item.descendants(ELEMENT_TEXT).forEach textLoop@{ textElement ->
                val lineId = textElement.attribute(ATTRIBUTE_FOR)
                if (lineId.isNullOrEmpty()) return@textLoop

                val content = parseCommonContent(textElement)
                val extracted = extractSubContent(
                    base = content,
                    language = language,
                    ignoreWords = false,
                )
                val entry = sidecar.getOrPut(lineId, ::Sidecar)
                if (isRomanization) {
                    extracted.main?.let(entry.romanizations::add)
                    extracted.background?.let(entry.backgroundRomanizations::add)
                } else {
                    extracted.main?.let(entry.translations::add)
                    extracted.background?.let(entry.backgroundTranslations::add)
                }
            }
        }
    }

    private fun parseBody(
        root: XmlElement,
        sidecar: Map<String, Sidecar>,
    ): List<ParsedLine> {
        val body = root.descendants(ELEMENT_BODY).firstOrNull() ?: return emptyList()
        val lines = mutableListOf<ParsedLine>()
        var blockIndex = 0

        body.children.filterIsInstance<XmlElement>().forEach { element ->
            when (element.name) {
                ELEMENT_DIV -> {
                    blockIndex += 1
                    element.descendants(ELEMENT_P).forEach { paragraph ->
                        processLineElement(paragraph, blockIndex, sidecar)?.let(lines::add)
                        require(lines.size <= MAX_LINE_COUNT) {
                            "TTML has too many lyric lines"
                        }
                    }
                }

                ELEMENT_P -> {
                    blockIndex += 1
                    processLineElement(element, blockIndex, sidecar)?.let(lines::add)
                    require(lines.size <= MAX_LINE_COUNT) {
                        "TTML has too many lyric lines"
                    }
                }
            }
        }
        return lines
    }

    private fun processLineElement(
        paragraph: XmlElement,
        blockIndex: Int,
        sidecar: Map<String, Sidecar>,
    ): ParsedLine? {
        val id = paragraph.attribute(ATTRIBUTE_KEY)
        if (id.isNullOrEmpty()) return null

        val base = parseCommonContent(paragraph)
        sidecar[id]?.let { external ->
            base.translations += external.translations
            base.romanizations += external.romanizations
            base.backgroundVocal?.let { background ->
                background.translations += external.backgroundTranslations
                background.romanizations += external.backgroundRomanizations
            }
        }
        return ParsedLine(
            base = base,
            agentId = paragraph.attribute(ATTRIBUTE_AGENT),
            blockIndex = blockIndex,
        )
    }

    private fun parseCommonContent(element: XmlElement): LyricBase {
        val beginAttribute = element.attribute(ATTRIBUTE_BEGIN)
        val endAttribute = element.attribute(ATTRIBUTE_END)
        val originalStartTime = parseTtmlTimeExact(beginAttribute)
        val originalEndTime = parseTtmlTimeExact(endAttribute)
        val state = extractNodeState(element)

        state.backgroundVocal?.let { background ->
            background.translations += state.backgroundTranslations
            background.romanizations += state.backgroundRomanizations
        }

        finalizeWords(state.words)
        val (startTime, endTime) = calculateTimeRange(
            originalStart = originalStartTime,
            originalEnd = originalEndTime,
            words = state.words,
            backgroundVocal = state.backgroundVocal,
        )
        val cleanFullText = state.fullText.toString().normalizeText()
        val hasTimeAttributes = beginAttribute != null || endAttribute != null
        applyFallbackWord(
            words = state.words,
            cleanText = cleanFullText,
            hasTimeAttributes = hasTimeAttributes,
            originalStart = originalStartTime,
            originalEnd = originalEndTime,
            calculatedStart = startTime,
            calculatedEnd = endTime,
        )

        return LyricBase(
            text = cleanFullText,
            startTime = startTime,
            endTime = endTime,
            words = state.words,
            translations = state.translations,
            romanizations = state.romanizations,
            backgroundVocal = state.backgroundVocal,
        )
    }

    private fun extractNodeState(element: XmlElement): ParsedState {
        val state = ParsedState()
        element.children.forEach { node ->
            when (node) {
                is XmlText -> processTextNode(state, node)
                is XmlElement -> processElementNode(state, node)
            }
        }
        return state
    }

    private fun processTextNode(state: ParsedState, node: XmlText) {
        val rawText = node.text
        val isFormatting = '\n' in rawText
        if (isFormatting && rawText.trimEcmaScriptWhitespace().isEmpty()) return

        val normalizedText = rawText.normalizeText(trim = false)
        state.fullText.append(normalizedText)
        if (
            !isFormatting &&
            normalizedText.isNotEmpty() &&
            normalizedText.trimEcmaScriptWhitespace().isEmpty()
        ) {
            state.words.lastOrNull()?.endsWithSpace = true
        }
    }

    private fun processElementNode(state: ParsedState, element: XmlElement) {
        if (element.attribute(ATTRIBUTE_RUBY) == RUBY_CONTAINER) {
            processRubyElement(state, element)
            return
        }

        when (element.attribute(ATTRIBUTE_ROLE)) {
            ROLE_BACKGROUND -> {
                state.backgroundVocalCount += 1
                require(state.backgroundVocalCount <= MAX_BACKGROUND_LINES_PER_LINE) {
                    "TTML line has too many background vocals"
                }
                // Upstream uses one slot: a later x-bg replaces an earlier x-bg.
                state.backgroundVocal = parseBackgroundVocal(element)
            }

            ROLE_TRANSLATION -> parseInlineSubContent(element)?.let { content ->
                content.main?.let(state.translations::add)
                content.background?.let(state.backgroundTranslations::add)
            }

            ROLE_ROMAN -> parseInlineSubContent(element)?.let { content ->
                content.main?.let(state.romanizations::add)
                content.background?.let(state.backgroundRomanizations::add)
            }

            else -> processWordElement(state, element)
        }
    }

    private fun processRubyElement(state: ParsedState, container: XmlElement) {
        val isObscene = container.attribute(ATTRIBUTE_OBSCENE) == TRUE_VALUE
        val emptyBeat = container.attribute(ATTRIBUTE_EMPTY_BEAT)?.parseJavaScriptInteger()
        var baseText = ""
        val rubyTags = mutableListOf<RubyTag>()

        container.children.filterIsInstance<XmlElement>().forEach { child ->
            when (child.attribute(ATTRIBUTE_RUBY)) {
                RUBY_BASE -> baseText = child.textContent().normalizeText(trim = false)
                RUBY_TEXT_CONTAINER -> {
                    child.children.filterIsInstance<XmlElement>().forEach { rubyElement ->
                        if (rubyElement.attribute(ATTRIBUTE_RUBY) == RUBY_TEXT) {
                            val begin = rubyElement.attribute(ATTRIBUTE_BEGIN)
                            val end = rubyElement.attribute(ATTRIBUTE_END)
                            val text = rubyElement.textContent()
                                .normalizeText(trim = false)
                                .trimEcmaScriptWhitespace()
                            if (text.isNotEmpty() && !begin.isNullOrEmpty() && !end.isNullOrEmpty()) {
                                rubyTags += RubyTag(
                                    text = text,
                                    startTime = parseTtmlTimeExact(begin),
                                    endTime = parseTtmlTimeExact(end),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (baseText.isEmpty()) return

        state.fullText.append(baseText)
        val cleanBaseText = baseText.trimEcmaScriptWhitespace()
        if (cleanBaseText.isEmpty()) return

        if (baseText.first().isEcmaScriptWhitespace() && state.words.isNotEmpty()) {
            state.words.last().endsWithSpace = true
        }
        state.words += Syllable(
            text = cleanBaseText,
            startTime = rubyTags.minOfOrNull(RubyTag::startTime) ?: 0.0,
            endTime = rubyTags.maxOfOrNull(RubyTag::endTime) ?: 0.0,
            endsWithSpace = baseText.last().isEcmaScriptWhitespace(),
            ruby = rubyTags,
            obscene = isObscene,
            emptyBeat = emptyBeat,
        )
        enforceWordsPerContent(state.words)
    }

    private fun processWordElement(state: ParsedState, element: XmlElement) {
        val begin = element.attribute(ATTRIBUTE_BEGIN)
        val end = element.attribute(ATTRIBUTE_END)
        val rawText = element.textContent()
        val normalizedText = rawText.normalizeText(trim = false)
        state.fullText.append(normalizedText)

        if (begin.isNullOrEmpty() || end.isNullOrEmpty()) return
        val isFormatting = '\n' in rawText
        val startsWithSpace = !isFormatting &&
            normalizedText.firstOrNull()?.isEcmaScriptWhitespace() == true
        val endsWithSpace = !isFormatting &&
            normalizedText.lastOrNull()?.isEcmaScriptWhitespace() == true
        val cleanText = normalizedText.trimEcmaScriptWhitespace()

        if (startsWithSpace && state.words.isNotEmpty()) {
            state.words.last().endsWithSpace = true
        }
        if (cleanText.isNotEmpty()) {
            state.words += Syllable(
                text = cleanText,
                startTime = parseTtmlTimeExact(begin),
                endTime = parseTtmlTimeExact(end),
                endsWithSpace = endsWithSpace,
                obscene = element.attribute(ATTRIBUTE_OBSCENE) == TRUE_VALUE,
                emptyBeat = element.attribute(ATTRIBUTE_EMPTY_BEAT)?.parseJavaScriptInteger(),
            )
            enforceWordsPerContent(state.words)
        }
    }

    private fun parseBackgroundVocal(element: XmlElement): LyricBase {
        val parsed = parseCommonContent(element)
        parsed.backgroundVocal = null
        parsed.text = parsed.text
            .replace(LEADING_BACKGROUND_BRACKETS, "")
            .replace(TRAILING_BACKGROUND_BRACKETS, "")
        parsed.words.firstOrNull()?.let { first ->
            first.text = first.text
                .replace(LEADING_BACKGROUND_BRACKETS, "")
                .trimStartEcmaScriptWhitespace()
        }
        parsed.words.lastOrNull()?.let { last ->
            last.text = last.text
                .replace(TRAILING_BACKGROUND_BRACKETS, "")
                .trimEndEcmaScriptWhitespace()
        }
        return parsed
    }

    private fun parseInlineSubContent(element: XmlElement): ExtractedSubContent? {
        val content = extractSubContent(
            base = parseCommonContent(element),
            language = element.attribute(ATTRIBUTE_LANG),
            ignoreWords = true,
        )
        return content.takeIf { it.main != null || it.background != null }
    }

    private fun extractSubContent(
        base: LyricBase,
        language: String?,
        ignoreWords: Boolean,
    ): ExtractedSubContent {
        val mainText = base.text.normalizeText()
        val hasMainWords = !ignoreWords && base.words.isNotEmpty()
        val main = if (mainText.isNotEmpty() || hasMainWords) {
            SubContent(
                text = mainText,
                language = language,
                words = base.words.takeIf {
                    hasMainWords && !it.isZeroTimeFallback()
                }.orEmpty(),
            )
        } else {
            null
        }

        val background = base.backgroundVocal?.let { bg ->
            val backgroundText = bg.text.normalizeText()
            val hasBackgroundWords = !ignoreWords && bg.words.isNotEmpty()
            if (backgroundText.isNotEmpty() || hasBackgroundWords) {
                SubContent(
                    text = backgroundText,
                    language = language,
                    words = bg.words.takeIf {
                        hasBackgroundWords && !it.isZeroTimeFallback()
                    }.orEmpty(),
                )
            } else {
                null
            }
        }
        return ExtractedSubContent(main = main, background = background)
    }

    private fun calculateTimeRange(
        originalStart: Double,
        originalEnd: Double,
        words: List<Syllable>,
        backgroundVocal: LyricBase?,
    ): Pair<Double, Double> {
        var startTime = originalStart
        var endTime = originalEnd
        val timedStarts = words.map(Syllable::startTime) +
            listOfNotNull(backgroundVocal?.startTime)
        val timedEnds = words.map(Syllable::endTime) +
            listOfNotNull(backgroundVocal?.endTime)

        if (timedStarts.isNotEmpty()) {
            val minimumChildStart = timedStarts.minOrNull() ?: Double.POSITIVE_INFINITY
            val maximumChildEnd = timedEnds.maxOrNull() ?: 0.0
            if (
                startTime == 0.0 ||
                (minimumChildStart > 0.0 && minimumChildStart < startTime)
            ) {
                startTime = minimumChildStart
            }
            if (endTime == 0.0 || maximumChildEnd > endTime) {
                endTime = maximumChildEnd
            }
        }
        return startTime to endTime
    }

    private fun applyFallbackWord(
        words: MutableList<Syllable>,
        cleanText: String,
        hasTimeAttributes: Boolean,
        originalStart: Double,
        originalEnd: Double,
        calculatedStart: Double,
        calculatedEnd: Double,
    ) {
        if (words.isEmpty() && cleanText.isNotEmpty() && hasTimeAttributes) {
            words += Syllable(
                text = cleanText,
                startTime = originalStart.takeIf { it > 0.0 } ?: calculatedStart,
                endTime = originalEnd.takeIf { it > 0.0 } ?: calculatedEnd,
                endsWithSpace = false,
            )
            enforceWordsPerContent(words)
        }
    }

    private fun finalizeWords(words: MutableList<Syllable>) {
        words.firstOrNull()?.let { it.text = it.text.trimStartEcmaScriptWhitespace() }
        words.lastOrNull()?.let {
            it.text = it.text.trimEndEcmaScriptWhitespace()
            it.endsWithSpace = false
        }
    }

    private fun convertToAmllLines(
        lines: List<ParsedLine>,
        agents: Map<String, Agent>,
    ): List<LyricParser.WordLine> {
        val result = mutableListOf<LyricParser.WordLine>()
        var lastPersonAgentId: String? = null
        var lastPersonIsDuet = false

        lines.forEach { parsed ->
            val agentId = parsed.agentId ?: DEFAULT_AGENT_ID
            val agent = agents[agentId]
            val isGroup = agent?.type == AGENT_GROUP
            val isOther = agent?.type == AGENT_OTHER
            val isDuet = if (isGroup) {
                false
            } else {
                when {
                    lastPersonAgentId == null -> {
                        isOther.also {
                            lastPersonAgentId = agentId
                            lastPersonIsDuet = it
                        }
                    }

                    lastPersonAgentId == agentId -> lastPersonIsDuet
                    else -> {
                        (!lastPersonIsDuet).also {
                            lastPersonAgentId = agentId
                            lastPersonIsDuet = it
                        }
                    }
                }
            }

            result += parsed.base.toWordLine(isBackground = false, isDuet = isDuet)
            parsed.base.backgroundVocal?.let { background ->
                result += background.toWordLine(isBackground = true, isDuet = isDuet)
            }
        }
        return result
    }

    private fun LyricBase.toWordLine(
        isBackground: Boolean,
        isDuet: Boolean,
    ): LyricParser.WordLine {
        val convertedWords = if (words.isNotEmpty()) {
            words.map { syllable ->
                LyricParser.Word(
                    startTimeMs = syllable.startTime.toCompatibilityLong(),
                    endTimeMs = syllable.endTime.toCompatibilityLong(),
                    text = syllable.text + if (syllable.endsWithSpace) " " else "",
                    obscene = syllable.obscene,
                    emptyBeat = syllable.emptyBeat,
                    ruby = syllable.ruby.map { ruby ->
                        LyricParser.RubySegment(
                            startTimeMs = ruby.startTime.toCompatibilityLong(),
                            endTimeMs = ruby.endTime.toCompatibilityLong(),
                            text = ruby.text,
                            exactStartTimeMs = ruby.startTime,
                            exactEndTimeMs = ruby.endTime,
                        )
                    },
                    exactStartTimeMs = syllable.startTime,
                    exactEndTimeMs = syllable.endTime,
                )
            }.toMutableList()
        } else {
            mutableListOf(
                LyricParser.Word(
                    startTimeMs = startTime.toCompatibilityLong(),
                    endTimeMs = endTime.toCompatibilityLong(),
                    text = text,
                    exactStartTimeMs = startTime,
                    exactEndTimeMs = endTime,
                ),
            )
        }

        val translation = translations.firstOrNull()?.text.orEmpty()
        val firstRomanization = romanizations.firstOrNull()
        var romanLyric = ""
        if (firstRomanization != null) {
            if (firstRomanization.words.isNotEmpty()) {
                alignRomanization(convertedWords, firstRomanization.words)
            } else {
                romanLyric = firstRomanization.text
            }
        }

        return LyricParser.WordLine(
            startTimeMs = startTime.toCompatibilityLong(),
            endTimeMs = endTime.toCompatibilityLong(),
            words = convertedWords,
            translatedLyric = translation,
            romanLyric = romanLyric,
            isBackground = isBackground,
            isDuet = isDuet,
            exactStartTimeMs = startTime,
            exactEndTimeMs = endTime,
        )
    }

    /**
     * Literal port of 1.0.1's fast-track (2 ms) then maximum-IoU (at least 0.1) matching.
     */
    private fun alignRomanization(
        mainWords: MutableList<LyricParser.Word>,
        romanWords: List<Syllable>,
    ) {
        var romanSearchStartIndex = 0
        mainWords.indices.forEach { mainIndex ->
            val main = mainWords[mainIndex]
            var maxIou = 0.0
            var bestMatchIndex = -1
            var fastTrackMatched = false
            var romanIndex = romanSearchStartIndex

            while (romanIndex < romanWords.size) {
                val roman = romanWords[romanIndex]
                if (abs(main.exactStartTimeMs - roman.startTime) <= FAST_TRACK_TOLERANCE_MS) {
                    mainWords[mainIndex] = main.copy(romanWord = roman.text)
                    romanSearchStartIndex = romanIndex + 1
                    fastTrackMatched = true
                    break
                }

                val overlapStart = max(main.exactStartTimeMs, roman.startTime)
                val overlapEnd = min(main.exactEndTimeMs, roman.endTime)
                val intersection = max(0.0, overlapEnd - overlapStart)
                if (intersection > 0.0) {
                    val unionStart = min(main.exactStartTimeMs, roman.startTime)
                    val unionEnd = max(main.exactEndTimeMs, roman.endTime)
                    val unionDuration = max(1.0, unionEnd - unionStart)
                    val iou = intersection / unionDuration
                    if (iou > maxIou) {
                        maxIou = iou
                        bestMatchIndex = romanIndex
                    }
                }
                if (roman.startTime >= main.exactEndTimeMs) break
                romanIndex += 1
            }

            if (
                !fastTrackMatched &&
                bestMatchIndex != -1 &&
                maxIou >= MIN_IOU_THRESHOLD
            ) {
                mainWords[mainIndex] =
                    main.copy(romanWord = romanWords[bestMatchIndex].text)
                romanSearchStartIndex = bestMatchIndex + 1
            }
        }
    }

    private fun enforceOutputLimits(lines: List<LyricParser.WordLine>) {
        require(lines.size <= MAX_LINE_COUNT) { "TTML has too many lyric lines" }
        require(lines.sumOf { it.words.size } <= MAX_TOTAL_WORD_COUNT) {
            "TTML has too many timed words"
        }
    }

    private fun enforceWordsPerContent(words: List<Syllable>) {
        require(words.size <= MAX_WORDS_PER_LINE) { "TTML line has too many timed words" }
    }

    private fun List<Syllable>.isZeroTimeFallback(): Boolean =
        size == 1 && first().startTime == 0.0 && first().endTime == 0.0

    private fun String.normalizeText(trim: Boolean = true): String {
        if (isEmpty()) return ""
        val normalized = buildString(length) {
            var inWhitespace = false
            this@normalizeText.forEach { character ->
                if (character.isEcmaScriptWhitespace()) {
                    if (!inWhitespace) append(' ')
                    inWhitespace = true
                } else {
                    append(character)
                    inWhitespace = false
                }
            }
        }
        return if (trim) normalized.trimEcmaScriptWhitespace() else normalized
    }

    private fun String.trimEcmaScriptWhitespace(): String =
        trim { it.isEcmaScriptWhitespace() }

    private fun String.trimStartEcmaScriptWhitespace(): String =
        trimStart { it.isEcmaScriptWhitespace() }

    private fun String.trimEndEcmaScriptWhitespace(): String =
        trimEnd { it.isEcmaScriptWhitespace() }

    private fun Char.isEcmaScriptWhitespace(): Boolean = when (code) {
        in 0x0009..0x000D,
        0x0020,
        0x00A0,
        0x1680,
        in 0x2000..0x200A,
        0x2028,
        0x2029,
        0x202F,
        0x205F,
        0x3000,
        0xFEFF,
        -> true

        else -> false
    }

    private fun jsMathRound(value: Double): Double {
        if (!value.isFinite() || value == 0.0) return value
        val rounded = floor(value + 0.5)
        return if (rounded == 0.0 && value < 0.0) -0.0 else rounded
    }

    /**
     * Decimal-prefix behavior of JavaScript `parseInt(value, 10)` for the small authoring hint
     * carried by AMLL. Values outside Kotlin Int transport are left absent.
     */
    private fun String.parseJavaScriptInteger(): Int? {
        val value = trimStartEcmaScriptWhitespace()
        val match = JAVASCRIPT_INTEGER_PREFIX.find(value) ?: return null
        return match.value.toIntOrNull()
    }

    private fun Double.toCompatibilityLong(): Long = when {
        isNaN() -> 0L
        this >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
        this <= Long.MIN_VALUE.toDouble() -> Long.MIN_VALUE
        else -> toLong()
    }

    private fun XmlElement.attribute(localName: String): String? =
        attributes[localName]

    private fun XmlElement.descendants(name: String): List<XmlElement> {
        val result = mutableListOf<XmlElement>()
        children.filterIsInstance<XmlElement>().forEach { child ->
            if (child.name == name) result += child
            result += child.descendants(name)
        }
        return result
    }

    private fun XmlElement.textContent(): String = buildString {
        children.forEach { node ->
            when (node) {
                is XmlText -> append(node.text)
                is XmlElement -> append(node.textContent())
            }
        }
    }

    private sealed interface XmlNode

    private data class XmlText(val text: String) : XmlNode

    private data class XmlElement(
        val name: String,
        val attributes: Map<String, String>,
        val children: MutableList<XmlNode> = mutableListOf(),
    ) : XmlNode

    private data class Agent(val type: String?)

    private data class ParsedLine(
        val base: LyricBase,
        val agentId: String?,
        @Suppress("unused") val blockIndex: Int,
    )

    private data class Sidecar(
        val translations: MutableList<SubContent> = mutableListOf(),
        val romanizations: MutableList<SubContent> = mutableListOf(),
        val backgroundTranslations: MutableList<SubContent> = mutableListOf(),
        val backgroundRomanizations: MutableList<SubContent> = mutableListOf(),
    )

    private data class ExtractedSubContent(
        val main: SubContent?,
        val background: SubContent?,
    )

    private data class SubContent(
        val text: String,
        @Suppress("unused") val language: String?,
        val words: List<Syllable>,
    )

    private data class RubyTag(
        val text: String,
        val startTime: Double,
        val endTime: Double,
    )

    private data class Syllable(
        var text: String,
        val startTime: Double,
        val endTime: Double,
        var endsWithSpace: Boolean = false,
        val ruby: List<RubyTag> = emptyList(),
        val obscene: Boolean = false,
        val emptyBeat: Int? = null,
    )

    private data class LyricBase(
        var text: String,
        val startTime: Double,
        val endTime: Double,
        val words: MutableList<Syllable>,
        val translations: MutableList<SubContent>,
        val romanizations: MutableList<SubContent>,
        var backgroundVocal: LyricBase?,
    )

    private data class ParsedState(
        val fullText: StringBuilder = StringBuilder(),
        val words: MutableList<Syllable> = mutableListOf(),
        val translations: MutableList<SubContent> = mutableListOf(),
        val romanizations: MutableList<SubContent> = mutableListOf(),
        val backgroundTranslations: MutableList<SubContent> = mutableListOf(),
        val backgroundRomanizations: MutableList<SubContent> = mutableListOf(),
        var backgroundVocal: LyricBase? = null,
        var backgroundVocalCount: Int = 0,
    )

    private val TIME_REGEX =
        Regex("""^(?:(?:(\d+):)?(\d+):)?(\d+(?:\.\d+)?)$""")
    private val JAVASCRIPT_INTEGER_PREFIX = Regex("""^[+-]?\d+""")
    private val LEADING_BACKGROUND_BRACKETS = Regex("""^[(（]+""")
    private val TRAILING_BACKGROUND_BRACKETS = Regex("""[)）]+$""")

    private const val ELEMENT_TT = "tt"
    private const val ELEMENT_HEAD = "head"
    private const val ELEMENT_BODY = "body"
    private const val ELEMENT_DIV = "div"
    private const val ELEMENT_P = "p"
    private const val ELEMENT_SPAN = "span"
    private const val ELEMENT_AGENT = "agent"
    private const val ELEMENT_ITUNES_METADATA = "iTunesMetadata"
    private const val ELEMENT_TRANSLATIONS = "translations"
    private const val ELEMENT_TRANSLATION = "translation"
    private const val ELEMENT_TRANSLITERATIONS = "transliterations"
    private const val ELEMENT_TRANSLITERATION = "transliteration"
    private const val ELEMENT_TEXT = "text"

    private const val ATTRIBUTE_ID = "id"
    private const val ATTRIBUTE_TYPE = "type"
    private const val ATTRIBUTE_LANG = "lang"
    private const val ATTRIBUTE_FOR = "for"
    private const val ATTRIBUTE_KEY = "key"
    private const val ATTRIBUTE_AGENT = "agent"
    private const val ATTRIBUTE_BEGIN = "begin"
    private const val ATTRIBUTE_END = "end"
    private const val ATTRIBUTE_ROLE = "role"
    private const val ATTRIBUTE_RUBY = "ruby"
    private const val ATTRIBUTE_OBSCENE = "obscene"
    private const val ATTRIBUTE_EMPTY_BEAT = "empty-beat"

    private const val ROLE_TRANSLATION = "x-translation"
    private const val ROLE_ROMAN = "x-roman"
    private const val ROLE_BACKGROUND = "x-bg"
    private const val RUBY_CONTAINER = "container"
    private const val RUBY_BASE = "base"
    private const val RUBY_TEXT_CONTAINER = "textContainer"
    private const val RUBY_TEXT = "text"
    private const val TRUE_VALUE = "true"
    private const val DEFAULT_AGENT_ID = "v1"
    private const val AGENT_GROUP = "group"
    private const val AGENT_OTHER = "other"

    private const val MIN_IOU_THRESHOLD = 0.1
    private const val FAST_TRACK_TOLERANCE_MS = 2.0
    private const val MAX_TTML_CHARS = 2 * 1024 * 1024
    private const val MAX_ELEMENT_DEPTH = 64
    private const val MAX_SPAN_DEPTH = 32
    private const val MAX_ELEMENT_COUNT = 50_000
    private const val MAX_LINE_COUNT = 10_000
    private const val MAX_WORDS_PER_LINE = 4_096
    private const val MAX_BACKGROUND_LINES_PER_LINE = 64
    private const val MAX_TOTAL_WORD_COUNT = 100_000
}
