package com.leejlredstar.redefinencm.kmp.lyric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmllMediaIdRequestParserTest {
    @Test
    fun parsesWebviewBindArray() {
        assertEquals("987654", parseAmllMediaIdRequest("""["987654"]"""))
    }

    @Test
    fun parsesNamedObject() {
        assertEquals("987654", parseAmllMediaIdRequest("""{"mediaId":"987654"}"""))
    }

    @Test
    fun rejectsMissingMediaId() {
        assertNull(parseAmllMediaIdRequest("[]"))
        assertNull(parseAmllMediaIdRequest(null))
    }
}
