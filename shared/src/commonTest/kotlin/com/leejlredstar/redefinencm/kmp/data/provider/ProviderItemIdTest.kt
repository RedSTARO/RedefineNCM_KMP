package com.leejlredstar.redefinencm.kmp.data.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProviderItemIdTest {
    @Test
    fun roundTripsBothProviders() {
        assertEquals("ncm:123456", ProviderItemId.netease(123456).toString())
        assertEquals("qq:0039MnYb0qxYhV", ProviderItemId.qq("0039MnYb0qxYhV").toString())

        assertEquals(
            ProviderItemId(MusicProviderId.NETEASE, "123456"),
            ProviderItemId.parseOrNull("ncm:123456"),
        )
        assertEquals(
            ProviderItemId(MusicProviderId.QQ, "0039MnYb0qxYhV"),
            ProviderItemId.parseOrNull("qq:0039MnYb0qxYhV"),
        )
    }

    @Test
    fun bareIdsWrittenBeforeProvidersExistedStillResolve() {
        // The player queue, the download queue and a year of cached rows hold bare numeric ids.
        assertEquals(ProviderItemId.netease(123456), ProviderItemId.parseOrNull("123456"))
        assertEquals(MusicProviderId.NETEASE, "123456".providerIdOrLegacy())
        assertEquals(MusicProviderId.NETEASE, "".providerIdOrLegacy())
    }

    @Test
    fun unknownProviderPrefixIsRejectedRatherThanReadAsNetease() {
        // Coercing an unrecognised prefix to NetEase would send a future provider's ids to the
        // NetEase client, which then fails somewhere far from the cause.
        assertNull(ProviderItemId.parseOrNull("spotify:1"))
        assertNull(ProviderItemId.parseOrNull("qqq:1"))
    }

    @Test
    fun idsCarryingAColonWithoutAProviderPrefixFallBackToLegacy() {
        assertEquals(
            ProviderItemId(MusicProviderId.NETEASE, "12:34"),
            ProviderItemId.parseOrNull("12:34"),
        )
    }

    @Test
    fun malformedInputIsNull() {
        assertNull(ProviderItemId.parseOrNull(""))
        assertNull(ProviderItemId.parseOrNull("   "))
        assertNull(ProviderItemId.parseOrNull("ncm:"))
        assertNull(ProviderItemId.parseOrNull(":123"))
    }

    @Test
    fun neteaseIdIsOnlyNumericForNetease() {
        assertEquals(123456L, ProviderItemId.netease(123456).neteaseIdOrNull)
        assertNull(ProviderItemId.qq("0039MnYb0qxYhV").neteaseIdOrNull)
        assertNull(ProviderItemId(MusicProviderId.NETEASE, "not-a-number").neteaseIdOrNull)
    }

    @Test
    fun providerKeysAreStableBecauseTheyArePersisted() {
        assertEquals("ncm", MusicProviderId.NETEASE.key)
        assertEquals("qq", MusicProviderId.QQ.key)
        assertEquals(MusicProviderId.NETEASE, MusicProviderId.fromKey("ncm"))
        assertNull(MusicProviderId.fromKey("NCM"))
    }
}
