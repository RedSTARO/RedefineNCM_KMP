package com.leejlredstar.redefinencm.kmp.ui.component

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates Compose overlays that must appear above heavyweight native surfaces
 * such as the desktop AMLL WebView2 host.
 */
object NativeSurfaceOverlayCoordinator {
    private val _externalOverlayActive = MutableStateFlow(false)
    val externalOverlayActive: StateFlow<Boolean> = _externalOverlayActive.asStateFlow()

    fun setExternalOverlayActive(active: Boolean) {
        _externalOverlayActive.value = active
    }
}
