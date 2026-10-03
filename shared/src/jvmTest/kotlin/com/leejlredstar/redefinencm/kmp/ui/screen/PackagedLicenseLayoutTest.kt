package com.leejlredstar.redefinencm.kmp.ui.screen

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackagedLicenseLayoutTest {
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
