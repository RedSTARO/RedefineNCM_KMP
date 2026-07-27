/*
 * Native Compose translation support for @applemusic-like-lyrics/core 0.5.2.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun Modifier.amllPlatformEvents(
    onWheel: (deltaY: Double, mode: AmllWheelDeltaMode) -> Boolean,
    onPageVisibilityChanged: (visible: Boolean, forceResync: Boolean) -> Unit,
): Modifier = this
