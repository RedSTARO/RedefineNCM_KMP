package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable

/** Only Android keeps files the app may delete solely with the user's confirmation. */
@Composable
actual fun MediaDeletionConsentHost(onConfirmed: (songId: Long) -> Unit) = Unit
