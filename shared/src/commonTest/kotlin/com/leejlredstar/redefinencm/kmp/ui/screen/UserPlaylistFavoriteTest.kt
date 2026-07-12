package com.leejlredstar.redefinencm.kmp.ui.screen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserPlaylistFavoriteTest {
    @Test
    fun specialTypeFiveIdentifiesFavoritePlaylistWithoutLocalizedName() {
        assertTrue(
            isFavoritePlaylist(
                specialType = 5,
                name = "Liked Songs",
                creatorUserId = 42L,
                currentUserId = 42L,
            ),
        )
    }

    @Test
    fun legacyCachedNameStillIdentifiesFavoritePlaylist() {
        assertTrue(
            isFavoritePlaylist(
                specialType = 0,
                name = "测试用户喜欢的音乐",
                creatorUserId = 42L,
                currentUserId = 42L,
            ),
        )
    }

    @Test
    fun regularPlaylistIsNotFavoritePlaylist() {
        assertFalse(
            isFavoritePlaylist(
                specialType = 0,
                name = "通勤歌单",
                creatorUserId = 42L,
                currentUserId = 42L,
            ),
        )
    }

    @Test
    fun anotherSpecialPlaylistDoesNotUseLegacyNameFallback() {
        assertFalse(
            isFavoritePlaylist(
                specialType = 10,
                name = "喜欢的音乐推荐",
                creatorUserId = 42L,
                currentUserId = 42L,
            ),
        )
    }

    @Test
    fun foreignSpecialTypeFivePlaylistIsNotFavoritePlaylist() {
        assertFalse(
            isFavoritePlaylist(
                specialType = 5,
                name = "Liked Songs",
                creatorUserId = 7L,
                currentUserId = 42L,
            ),
        )
    }

    @Test
    fun legacyFallbackRequiresStandardNameSuffix() {
        assertFalse(
            isFavoritePlaylist(
                specialType = 0,
                name = "我喜欢的音乐风格",
                creatorUserId = 42L,
                currentUserId = 42L,
            ),
        )
    }

    @Test
    fun favoritePlaylistRequiresLoggedInUser() {
        assertFalse(
            isFavoritePlaylist(
                specialType = 5,
                name = "Liked Songs",
                creatorUserId = 42L,
                currentUserId = 0L,
            ),
        )
    }
}
