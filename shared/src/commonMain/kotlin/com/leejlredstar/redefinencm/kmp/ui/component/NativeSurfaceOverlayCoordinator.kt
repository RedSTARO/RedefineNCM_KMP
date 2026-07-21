package com.leejlredstar.redefinencm.kmp.ui.component

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

enum class NativeSurfaceOverlaySource {
    DesktopNavigationRail,
    DesktopPlayerSheet,
    AppSnackbar,
}

class NativeSurfaceOwner internal constructor()

private data class NativeSurfaceState(
    val surfaces: Map<NativeSurfaceOwner, Boolean> = emptyMap(),
)

/**
 * Coordinates Compose overlays that must appear above heavyweight native surfaces
 * such as the desktop AMLL WebView2 host.
 */
object NativeSurfaceOverlayCoordinator {
    private val _activeSources = MutableStateFlow(emptySet<NativeSurfaceOverlaySource>())
    val activeSources: StateFlow<Set<NativeSurfaceOverlaySource>> = _activeSources.asStateFlow()
    private val nativeSurfaceState = MutableStateFlow(NativeSurfaceState())

    fun setActive(source: NativeSurfaceOverlaySource, active: Boolean) {
        _activeSources.update { current ->
            if (active) current + source else current - source
        }
    }

    fun attachNativeSurface(initiallyVisible: Boolean): NativeSurfaceOwner {
        val owner = NativeSurfaceOwner()
        nativeSurfaceState.update { current ->
            current.copy(surfaces = current.surfaces + (owner to initiallyVisible))
        }
        return owner
    }

    fun reportNativeSurfaceVisible(owner: NativeSurfaceOwner, visible: Boolean) {
        nativeSurfaceState.update { current ->
            if (owner in current.surfaces) {
                current.copy(surfaces = current.surfaces + (owner to visible))
            } else {
                current
            }
        }
    }

    fun detachNativeSurface(owner: NativeSurfaceOwner) {
        nativeSurfaceState.update { current ->
            current.copy(surfaces = current.surfaces - owner)
        }
    }

    suspend fun awaitOverlayReady(
        source: NativeSurfaceOverlaySource,
        timeoutMillis: Long = 2_000L,
    ): Boolean = withTimeoutOrNull(timeoutMillis) {
        combine(activeSources, nativeSurfaceState) { sources, state ->
            source in sources && state.surfaces.values.none { it }
        }.first { ready -> ready }
        true
    } ?: false
}
