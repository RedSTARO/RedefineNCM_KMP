package com.leejlredstar.redefinencm.kmp.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsCategoryTest {
    @Test
    fun everyCategoryHasAUniqueJumpTargetAfterTheHero() {
        assertEquals((1..SettingsCategory.entries.size).toList(), SettingsCategory.entries.map { it.listIndex })
        assertEquals(
            listOf(
                SettingsCategory.Accounts, SettingsCategory.Playback, SettingsCategory.Transition,
                SettingsCategory.Lyrics, SettingsCategory.Downloads, SettingsCategory.General,
                SettingsCategory.Cache, SettingsCategory.Backup, SettingsCategory.About,
            ),
            SettingsCategory.entries.toList(),
        )
    }

    @Test
    fun keepsTheActiveCategoryFromTheViewportIncludingTheEnds() {
        assertEquals(SettingsCategory.Accounts, activeSettingsCategory(0, 0, 3, canScrollForward = true, landing = null))
        assertEquals(SettingsCategory.Downloads, activeSettingsCategory(5, 48, 7, canScrollForward = true, landing = null))
        assertEquals(SettingsCategory.About, activeSettingsCategory(7, 24, 9, canScrollForward = false, landing = null))
    }

    @Test
    fun aBottomClampedJumpKeepsItsOwnSelectionUntilScrollingAgain() {
        val landing = SettingsIndexLanding(SettingsCategory.Backup, listIndex = 7, scrollOffset = 40)
        assertEquals(SettingsCategory.Backup, activeSettingsCategory(7, 40, 9, canScrollForward = false, landing = landing))
        assertEquals(SettingsCategory.Cache, activeSettingsCategory(7, 39, 9, canScrollForward = true, landing = landing))
        assertEquals(SettingsCategory.Cache, activeSettingsCategory(7, 40, 7, canScrollForward = true, landing = landing))
    }
}
