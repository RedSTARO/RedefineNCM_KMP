package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable

private object AlwaysAllowed : NotificationAccess {
    override val allowed: Boolean = true

    override fun request() = Unit
}

@Composable
actual fun rememberNotificationAccess(): NotificationAccess = AlwaysAllowed
