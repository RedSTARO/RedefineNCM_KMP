package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Something the app did on its own while starting that the user should hear about once. */
enum class StartupNotice {
    /** The settings file could not be read and was replaced with defaults. */
    SettingsReset;

    /** Worded when shown, in the language current then. */
    val message: String
        get() = when (this) {
            SettingsReset -> strings.settingsResetAfterDamage
        }
}

/**
 * Notices waiting for the app's snackbar. Storage code posts them, possibly before any UI exists,
 * and the app shows each one once.
 */
object StartupNotices {
    private val pending = MutableStateFlow<List<StartupNotice>>(emptyList())

    val notices: StateFlow<List<StartupNotice>> = pending.asStateFlow()

    fun post(notice: StartupNotice) {
        pending.update { if (notice in it) it else it + notice }
    }

    fun consume(notice: StartupNotice) {
        pending.update { it - notice }
    }
}
