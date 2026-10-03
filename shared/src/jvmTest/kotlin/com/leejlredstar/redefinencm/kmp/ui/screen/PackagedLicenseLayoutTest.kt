package com.leejlredstar.redefinencm.kmp.ui.screen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class PackagedLicenseLayoutTest {
    @Test
    fun packagedNoticesAndLicensesMatchTheCanonicalFiles() {
        val root = listOf(File("."), File("..")).first { it.resolve("LICENSES/OFL-1.1.txt").isFile }
        val packaged = root.resolve("shared/src/commonMain/composeResources/files/licenses")
        for (name in listOf("Apache-2.0.txt", "AGPL-3.0-only.txt", "OFL-1.1.txt")) {
            assertEquals(root.resolve("LICENSES/$name").readText(), packaged.resolve(name).readText(), name)
        }
        assertEquals(root.resolve("THIRD_PARTY_NOTICES.md").readText(), packaged.resolve("THIRD_PARTY_NOTICES.md").readText())
        assertTrue("Noto Sans SC" in packaged.resolve("THIRD_PARTY_NOTICES.md").readText())
    }

    private fun license(name: String): String {
        val directory = listOf(File("LICENSES"), File("../LICENSES"))
            .first { it.resolve(name).isFile }
        return directory.resolve(name).readText()
    }

    @Test
    fun apacheIndentedDefinitionsReflowAsWholeParagraphs() {
        val blocks = licenseBlocks(license("Apache-2.0.txt"))
        val definition = blocks.first { "\"Legal Entity\" shall mean" in it.text }
        assertFalse('\n' in definition.text)
        assertTrue("beneficial ownership" in definition.text)
    }

    @Test
    fun agplSubclausesKeepTheirLabelsButJoinContinuationLines() {
        val blocks = licenseBlocks(license("AGPL-3.0-only.txt"))
        val clause = blocks.first { "d) If the work has interactive user interfaces" in it.text }
        assertFalse('\n' in clause.text)
        assertTrue("work need not make them do so." in clause.text)
    }
}
