package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable

/**
 * Whether the app's lyric notification can be seen, and a way to ask for it. Only Android can
 * refuse (the notification permission on 13+, notifications or the lyric channel switched off in
 * system settings); elsewhere it is always allowed and asking does nothing.
 */
interface NotificationAccess {
    /** Read again each time the app returns to the foreground and after every request. */
    val allowed: Boolean

    /** Asks the system where it asks (Android 13+); otherwise only reads the state again. */
    fun request()
}

@Composable
expect fun rememberNotificationAccess(): NotificationAccess
