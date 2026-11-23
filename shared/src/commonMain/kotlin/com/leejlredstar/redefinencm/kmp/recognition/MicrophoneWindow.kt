package com.leejlredstar.redefinencm.kmp.recognition

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/** Android publishes top-resumed ownership separately from ordinary visible/playing state. */
internal val LocalMicrophoneWindowActive = staticCompositionLocalOf<StateFlow<Boolean>?> { null }
