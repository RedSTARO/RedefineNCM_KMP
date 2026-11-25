package com.leejlredstar.redefinencm.kmp.ui.screen

import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.data.provider.TrackTag
import com.leejlredstar.redefinencm.kmp.data.provider.toProviderTrack
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.LanguageSetting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongFeeBadgeTest {
    @BeforeTest
    fun startInChinese() = I18n.apply(LanguageSetting.ZH)

    @AfterTest
    fun followTheSystemAgain() = I18n.apply(LanguageSetting.SYSTEM)

    @Test
    fun neteaseSearchAndPlaylistRowsUseTheSameFeeTags() {
        assertEquals(setOf(TrackTag.VIP), SongDetailSongs(id = 1, fee = 1).toProviderTrack().tags)
        assertEquals(setOf(TrackTag.PAID), SongDetailSongs(id = 1, fee = 4).toProviderTrack().tags)
        assertEquals("VIP", songFeeBadge(1))
        assertEquals("付费", songFeeBadge(4))
        I18n.apply(LanguageSetting.EN)
        assertEquals("Paid", songFeeBadge(4))
        I18n.apply(LanguageSetting.JA)
        assertEquals("有料", songFeeBadge(4))

        for (fee in listOf(0, 8, 99)) {
            assertEquals(emptySet(), SongDetailSongs(id = 1, fee = fee).toProviderTrack().tags)
            assertNull(songFeeBadge(fee))
        }
    }
}
