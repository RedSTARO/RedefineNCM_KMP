/*
 * Native Compose translation support for @applemusic-like-lyrics/core 0.5.2
 * packages/core/src/lyric-player/base/{index,scroll}.ts.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The browser is the only Compose target whose native wheel event carries a DOM
 * `WheelEvent.deltaMode`. The returned modifier owns the viewport bounds used by that bridge.
 *
 * [onWheel] returns whether AMLL accepted the event. Only accepted browser events are cancelled
 * before they reach CanvasKit, which prevents the same wheel delta from being handled twice.
 */
@Composable
internal expect fun Modifier.amllPlatformEvents(
    onWheel: (deltaY: Double, mode: AmllWheelDeltaMode) -> Boolean,
    onPageVisibilityChanged: (visible: Boolean, forceResync: Boolean) -> Unit,
): Modifier
