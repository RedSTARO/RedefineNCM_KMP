package com.leejlredstar.redefinencm.kmp.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class LicenseBlocksTest {

    @Test
    fun aHardWrappedParagraphBecomesOneThatTheScreenCanWrap() {
        val text = "This program is free software: you can redistribute it\n" +
            "and/or modify it under the terms of the license.\n" +
            "\n" +
            "Second paragraph."

        assertEquals(
            listOf(
                LicenseBlock("This program is free software: you can redistribute it and/or modify it under the terms of the license."),
                LicenseBlock("Second paragraph."),
            ),
            licenseBlocks(text),
        )
    }

    @Test
    fun indentedLinesAndListItemsKeepTheirOwnLine() {
        val text = "The work must carry:\n" +
            "    a) prominent notices, and\n" +
            "    b) the date.\n" +
            "\n" +
            "It is based on:\n" +
            "- `core` version `0.5.2`\n" +
            "- `lyric` version `1.0.2`"

        assertEquals(
            listOf(
                LicenseBlock("The work must carry:\n    a) prominent notices, and\n    b) the date."),
                LicenseBlock("It is based on:\n- `core` version `0.5.2`\n- `lyric` version `1.0.2`"),
            ),
            licenseBlocks(text),
        )
    }

    @Test
    fun aMarkdownHeadingLosesItsMarkAndIsFlaggedAsOne() {
        assertEquals(
            listOf(LicenseBlock("Third-party notices", heading = true), LicenseBlock("Body.")),
            licenseBlocks("# Third-party notices\r\n\r\nBody.\r\n"),
        )
    }
}
