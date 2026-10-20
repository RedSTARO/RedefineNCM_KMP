package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.i18n.LanguageSetting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A heart tapped without an account must say why nothing happened, unless the lookup still runs. */
class FavoriteClickOutcomeTest {
    @BeforeTest
    fun showChinese() {
        I18n.apply(LanguageSetting.ZH)
    }

    @AfterTest
    fun followTheSystemAgain() {
        I18n.apply(LanguageSetting.SYSTEM)
    }

    @Test
    fun saysNothingWhileTheAccountIsStillBeingLookedUp() {
        assertNull(favoriteNotSignedInMessage(accountLoading = true, accountUnreachable = false, provider = "网易云音乐", title = "歌"))
    }

    @Test
    fun pointsToSignInWhenNoAccountIsSignedIn() {
        assertEquals(
            "登录网易云音乐后才能喜欢歌曲",
            favoriteNotSignedInMessage(accountLoading = false, accountUnreachable = false, provider = "网易云音乐", title = "歌"),
        )
    }

    @Test
    fun reportsAFailureWhenTheAccountServerCouldNotBeReached() {
        assertEquals(
            "未能喜欢「歌」，请重试",
            favoriteNotSignedInMessage(accountLoading = false, accountUnreachable = true, provider = "网易云音乐", title = "歌"),
        )
    }
}
