/*
 * Native Compose translation support for @applemusic-like-lyrics/core 0.5.2
 * packages/core/src/lyric-player/base/{index,scroll}.ts.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Bridges native platform events AMLL needs but Compose's common pointer API does not carry.
 *
 * The browser is the only Compose target whose native wheel event carries a DOM
 * `WheelEvent.deltaMode`, and the only one with page visibility transitions to forward. It
 * installs an implementation through [LocalAmllPlatformEventBridge]; the other three targets
 * have nothing to add and previously declared three identical no-op actuals to say so.
 */
internal interface AmllPlatformEventBridge {
    /**
     * [onWheel] returns whether AMLL accepted the event. Only accepted browser events are
     * cancelled before they reach CanvasKit, which prevents the same wheel delta from being
     * handled twice. The returned modifier owns the viewport bounds used by that bridge.
     */
    @Composable
    fun attach(
        modifier: Modifier,
        onWheel: (deltaY: Double, mode: AmllWheelDeltaMode) -> Boolean,
        onPageVisibilityChanged: (visible: Boolean, forceResync: Boolean) -> Unit,
    ): Modifier
}

/** Null on every target whose OS delivers these events through Compose already. */
internal val LocalAmllPlatformEventBridge =
    staticCompositionLocalOf<AmllPlatformEventBridge?> { null }

@Composable
internal fun Modifier.amllPlatformEvents(
    onWheel: (deltaY: Double, mode: AmllWheelDeltaMode) -> Boolean,
    onPageVisibilityChanged: (visible: Boolean, forceResync: Boolean) -> Unit,
): Modifier =
    LocalAmllPlatformEventBridge.current
        ?.attach(this, onWheel, onPageVisibilityChanged)
        ?: this
