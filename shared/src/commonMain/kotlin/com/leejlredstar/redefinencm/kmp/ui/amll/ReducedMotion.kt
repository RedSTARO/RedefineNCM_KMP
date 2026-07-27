/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Native Compose translation/adaptation of Apple Music-like Lyrics and the former
 * RedefineNCM AMLL host.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.redefinencm.kmp.ui.amll

import androidx.compose.runtime.Composable

/**
 * Remembers whether the current platform asks applications to reduce non-essential motion.
 *
 * Platform implementations keep the returned value in Compose state when the operating system
 * exposes a change notification. An unavailable or unreadable preference is treated as disabled.
 */
@Composable
expect fun rememberReducedMotionEnabled(): Boolean
