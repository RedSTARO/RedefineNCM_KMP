package com.leejlredstar.redefinencm.kmp.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

/** A queue item's artist line is joined differently by each list; the first name is the same. */
class FirstArtistTest {
    @Test
    fun theFirstNameOfEveryJoiningIsFound() {
        assertEquals("周杰伦", firstArtistOf("周杰伦, 费玉清"))
        assertEquals("周杰伦", firstArtistOf("周杰伦 / 费玉清"))
        assertEquals("周杰伦", firstArtistOf("周杰伦、费玉清"))
        assertEquals("Beyond", firstArtistOf("Beyond"))
    }
}
