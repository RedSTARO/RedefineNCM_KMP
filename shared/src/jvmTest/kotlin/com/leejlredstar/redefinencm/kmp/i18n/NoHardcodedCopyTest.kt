package com.leejlredstar.redefinencm.kmp.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Copy lives in `src/commonMain/i18n/{zh,en,ja}.xml`. A Chinese or Japanese string literal in
 * production Kotlin is copy that skipped those files, so it shows in one language whatever the
 * user picked. The allowlist holds the few literals that are not copy.
 */
class NoHardcodedCopyTest {
    /**
     * Literals that are not copy: names the server sends, which the app matches and never shows as
     * its own words, and the separator an artist line is split on.
     */
    private val notCopy = setOf("音乐百科", "私人雷达", "喜欢的音乐", "、")

    /** The i18n runtime itself: the languages' own names and each language's count units. */
    private val runtimeFile = "i18n/I18n.kt"

    /** Kana, CJK ideographs, and the CJK and full-width punctuation that only Chinese and Japanese use. */
    private val cjk = Regex("[\\u3000-\\u30ff\\u4e00-\\u9fff\\uff00-\\uffef]")

    @Test
    fun productionKotlinKeepsItsCopyInTheResourceFiles() {
        val sourceSets = File("src").listFiles().orEmpty().filter { it.isDirectory && it.name.endsWith("Main") }
        assertTrue(sourceSets.any { it.name == "commonMain" }, "run from the :shared project directory")
        val roots = sourceSets + File("../androidApp/src/main") + File("../desktopApp/src/main")
        val files = roots.filter(File::isDirectory)
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        val offenders = files.flatMap { file ->
            val path = file.invariantSeparatorsPath
            if (path.endsWith("/kmp/$runtimeFile")) return@flatMap emptyList()
            stringLiterals(file.readText())
                .filter { (_, text) -> cjk.containsMatchIn(text) && text !in notCopy }
                .map { (line, text) -> "${path.removePrefix("../")}:$line \"$text\"" }
        }
        assertTrue(files.size > 200, "only ${files.size} Kotlin files found")
        assertTrue(
            offenders.isEmpty(),
            "Copy belongs in src/commonMain/i18n/*.xml, not in Kotlin:\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun theScannerSeesStringsOnlyWhereKotlinHasThem() {
        val source = """
            // "注释"
            /* "块注释" /* "嵌套" */ */
            val a = '"'
            val b = "字符串 ${'$'}{call("模板里")} 尾"
            val c = ""${'"'}原始
            串""${'"'}
            val `名字` = 1
        """.trimIndent()
        assertEquals(
            listOf(4 to "模板里", 4 to "字符串 \${…} 尾", 5 to "原始\n串"),
            stringLiterals(source),
        )
    }
}

/**
 * The string literals of a Kotlin source with the line each opens on, in the order they close.
 * Comments, character literals and backquoted names are skipped. A `${…}` template stands in its
 * string's text as `${…}`, and the strings inside it are literals of their own.
 */
internal fun stringLiterals(source: String): List<Pair<Int, String>> =
    KotlinStringScanner(source).apply { code(insideTemplate = false) }.literals

private class KotlinStringScanner(private val source: String) {
    val literals = mutableListOf<Pair<Int, String>>()
    private var i = 0
    private var line = 1

    /** Reads code up to the end of the source, or to the `}` that closes a template. */
    fun code(insideTemplate: Boolean) {
        var depth = 0
        while (i < source.length) {
            val c = source[i]
            when {
                source.startsWith("//", i) -> while (i < source.length && source[i] != '\n') i++
                source.startsWith("/*", i) -> blockComment()
                c == '"' -> string()
                c == '\'' -> i = source.indexOf('\'', i + if (source[i + 1] == '\\') 3 else 2) + 1
                c == '`' -> i = source.indexOf('`', i + 1) + 1
                c == '{' -> { depth++; i++ }
                c == '}' && insideTemplate && depth == 0 -> { i++; return }
                c == '}' -> { depth--; i++ }
                else -> advance()
            }
        }
    }

    private fun advance() {
        if (source[i] == '\n') line++
        i++
    }

    private fun blockComment() {
        var depth = 0
        do {
            when {
                source.startsWith("/*", i) -> { depth++; i += 2 }
                source.startsWith("*/", i) -> { depth--; i += 2 }
                else -> advance()
            }
        } while (depth > 0 && i < source.length)
    }

    private fun string() {
        val opensOn = line
        val raw = source.startsWith("\"\"\"", i)
        val text = StringBuilder()
        i += if (raw) 3 else 1
        while (i < source.length) {
            when {
                raw && source.startsWith("\"\"\"", i) -> {
                    i += 3
                    while (i < source.length && source[i] == '"') { text.append('"'); i++ }
                    break
                }
                !raw && source[i] == '"' -> { i++; break }
                !raw && source[i] == '\\' -> { text.append(source, i, i + 2); i += 2 }
                source.startsWith("\${", i) -> {
                    i += 2
                    code(insideTemplate = true)
                    text.append("\${…}")
                }
                else -> { text.append(source[i]); advance() }
            }
        }
        literals += opensOn to text.toString()
    }
}
