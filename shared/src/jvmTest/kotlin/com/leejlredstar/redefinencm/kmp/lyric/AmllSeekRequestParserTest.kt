package com.leejlredstar.redefinencm.kmp.lyric

import kotlin.test.Test
import kotlin.test.assertEquals

class AmllSeekRequestParserTest {
    @Test
    fun parsesWebviewBindArray() {
        assertEquals(12_345L to "987654", parseAmllSeekRequest("""[12345,"987654"]"""))
    }

    @Test
    fun parsesStringArrayWithoutUsingTimeAsMediaId() {
        assertEquals(12_345L to "987654", parseAmllSeekRequest("""["12345","987654"]"""))
    }

    @Test
    fun parsesObjectRequest() {
        assertEquals(12_345L to "987654", parseAmllSeekRequest("""{"timeMs":12345,"mediaId":"987654"}"""))
    }

    @Test
    fun parsesLegacySeekRequest() {
        assertEquals(12_345L to "987654", parseAmllSeekRequest("seekTo:12345:987654"))
    }
}
