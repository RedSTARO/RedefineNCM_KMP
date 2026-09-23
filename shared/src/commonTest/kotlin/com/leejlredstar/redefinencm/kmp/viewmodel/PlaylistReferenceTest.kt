package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.data.provider.ProviderItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A pasted playlist link or id names one provider's playlist, matched on the link's host. */
class PlaylistReferenceTest {
    @Test
    fun qqLinksAndPrefixedIdsAreQq() {
        val qq = ProviderItemId(MusicProviderId.QQ, "8052190267")
        assertEquals(qq, parsePlaylistReference("https://y.qq.com/n/ryqq/playlist/8052190267"))
        assertEquals(qq, parsePlaylistReference("https://i.y.qq.com/n2/m/share/details/taoge.html?id=8052190267&hosteuin=x"))
        assertEquals(qq, parsePlaylistReference("qq:8052190267"))
    }

    @Test
    fun neteaseLinksAndBareNumbersAreNetEase() {
        val netease = ProviderItemId.netease(24381616)
        assertEquals(netease, parsePlaylistReference("https://music.163.com/playlist?id=24381616&userid=1"))
        assertEquals(netease, parsePlaylistReference("https://music.163.com/#/playlist?id=24381616"))
        assertEquals(netease, parsePlaylistReference("  24381616 "))
    }

    @Test
    fun anythingElseNamesNoPlaylist() {
        assertNull(parsePlaylistReference(""))
        assertNull(parsePlaylistReference("https://y.qq.com/n/ryqq/songDetail/0039MnYb0qxYhV"))
        assertNull(parsePlaylistReference("qq:0039MnYb0qxYhV"))
        assertNull(parsePlaylistReference("晴天"))
    }
}
