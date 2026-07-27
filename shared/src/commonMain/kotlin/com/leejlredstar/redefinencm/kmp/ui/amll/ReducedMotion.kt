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
