package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderSearchResults
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Each provider's search results continue from where that provider left off. */
class SearchCursorTest {
    private fun page(size: Int, failed: Boolean = false) = ProviderSearchResults(
        provider = MusicProviderId.QQ,
        tracks = List(size) { ProviderTrack(ProviderItemId.qq("m$it"), "t") },
        failed = failed,
    )

    @Test
    fun aFullPageMovesOnAndAShortPageEndsTheProvider() {
        val full = SearchCursor.after(requestedOffset = 0, page(30), pageSize = 30)
        assertEquals(30, full.nextOffset)
        assertTrue(full.hasMore)

        val short = SearchCursor.after(requestedOffset = 30, page(12), pageSize = 30)
        assertFalse(short.hasMore)
    }

    @Test
    fun aFailedPageIsAskedForAgainRatherThanSkipped() {
        val failed = SearchCursor.after(requestedOffset = 0, page(0, failed = true), pageSize = 30)
        assertEquals(0, failed.nextOffset)
        assertTrue(failed.failed)
        assertTrue(failed.hasMore)
    }
}
