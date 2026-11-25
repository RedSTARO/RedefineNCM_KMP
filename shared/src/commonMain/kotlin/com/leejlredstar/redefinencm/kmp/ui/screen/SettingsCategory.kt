package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.ui.graphics.vector.ImageVector
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons

/** Stable identities for the index and list, even when a platform hides some rows or copy changes. */
internal enum class SettingsCategory(val icon: ImageVector) {
    Accounts(AppIcons.Person),
    Playback(AppIcons.VolumeUp),
    Transition(AppIcons.GraphicEq),
    Lyrics(AppIcons.FormatQuote),
    Downloads(AppIcons.Download),
    General(AppIcons.Settings),
    Cache(AppIcons.FolderOpen),
    Backup(AppIcons.AttachFile),
    About(AppIcons.Info),
    ;

    // The hero occupies index zero. Each category occupies one keyed slot, not one slot per row.
    val listIndex: Int get() = ordinal + 1

    val label: String
        get() = when (this) {
            Accounts -> strings.settingsSectionAccounts
            Playback -> strings.play
            Transition -> strings.songTransitions
            Lyrics -> strings.lyrics
            Downloads -> strings.downloads
            General -> strings.settingsSectionGeneral
            Cache -> strings.metadataCache
            Backup -> strings.backup
            About -> strings.about
        }
}

/** A short final section may be visible without being able to reach the top of the viewport. */
internal data class SettingsIndexLanding(val category: SettingsCategory, val listIndex: Int, val scrollOffset: Int)

internal fun activeSettingsCategory(
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    lastVisibleIndex: Int,
    canScrollForward: Boolean,
    landing: SettingsIndexLanding?,
): SettingsCategory {
    if (landing != null && landing.listIndex == firstVisibleIndex && landing.scrollOffset == firstVisibleOffset &&
        landing.category.listIndex in firstVisibleIndex..lastVisibleIndex
    ) return landing.category
    val index = if (canScrollForward || firstVisibleIndex == 0) firstVisibleIndex else lastVisibleIndex
    return SettingsCategory.entries.getOrElse(index - 1) { SettingsCategory.Accounts }
}
