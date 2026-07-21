package com.leejlredstar.redefinencm.kmp.ui.component

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class NativeSurfaceOverlayCoordinatorTest {
    @Test
    fun oneSourceCannotClearAnotherActiveOverlay() {
        try {
            NativeSurfaceOverlayCoordinator.setActive(
                NativeSurfaceOverlaySource.DesktopNavigationRail,
                true,
            )
            NativeSurfaceOverlayCoordinator.setActive(
                NativeSurfaceOverlaySource.DesktopPlayerSheet,
                true,
            )

            NativeSurfaceOverlayCoordinator.setActive(
                NativeSurfaceOverlaySource.DesktopNavigationRail,
                false,
            )

            assertTrue(NativeSurfaceOverlayCoordinator.activeSources.value.isNotEmpty())
            assertTrue(
                NativeSurfaceOverlaySource.DesktopPlayerSheet in
                    NativeSurfaceOverlayCoordinator.activeSources.value,
            )
        } finally {
            NativeSurfaceOverlaySource.entries.forEach { source ->
                NativeSurfaceOverlayCoordinator.setActive(source, false)
            }
        }

        assertFalse(NativeSurfaceOverlayCoordinator.activeSources.value.isNotEmpty())
    }

    @Test
    fun overlayReadinessWaitsForNativeSurfaceToHide() = runTest {
        val source = NativeSurfaceOverlaySource.DesktopNavigationRail
        val owner = NativeSurfaceOverlayCoordinator.attachNativeSurface(initiallyVisible = true)
        try {
            NativeSurfaceOverlayCoordinator.setActive(source, true)

            val ready = async { NativeSurfaceOverlayCoordinator.awaitOverlayReady(source) }
            yield()
            assertFalse(ready.isCompleted)

            NativeSurfaceOverlayCoordinator.reportNativeSurfaceVisible(owner, false)
            assertTrue(ready.await())
        } finally {
            NativeSurfaceOverlayCoordinator.detachNativeSurface(owner)
            NativeSurfaceOverlayCoordinator.setActive(source, false)
        }
    }

    @Test
    fun overlayReadinessTimesOutWhenNativeSurfaceStaysVisible() = runTest {
        val source = NativeSurfaceOverlaySource.DesktopNavigationRail
        val owner = NativeSurfaceOverlayCoordinator.attachNativeSurface(initiallyVisible = true)
        try {
            NativeSurfaceOverlayCoordinator.setActive(source, true)

            assertFalse(
                NativeSurfaceOverlayCoordinator.awaitOverlayReady(
                    source = source,
                    timeoutMillis = 1L,
                ),
            )
        } finally {
            NativeSurfaceOverlayCoordinator.detachNativeSurface(owner)
            NativeSurfaceOverlayCoordinator.setActive(source, false)
        }
    }

    @Test
    fun overlayReadinessDoesNotOutliveItsSource() = runTest {
        val source = NativeSurfaceOverlaySource.DesktopNavigationRail
        val owner = NativeSurfaceOverlayCoordinator.attachNativeSurface(initiallyVisible = true)
        try {
            NativeSurfaceOverlayCoordinator.setActive(source, true)
            val ready = async {
                NativeSurfaceOverlayCoordinator.awaitOverlayReady(
                    source = source,
                    timeoutMillis = 1L,
                )
            }
            yield()

            NativeSurfaceOverlayCoordinator.setActive(source, false)
            NativeSurfaceOverlayCoordinator.reportNativeSurfaceVisible(owner, false)

            assertFalse(ready.await())
        } finally {
            NativeSurfaceOverlayCoordinator.detachNativeSurface(owner)
            NativeSurfaceOverlayCoordinator.setActive(source, false)
        }
    }

    @Test
    fun staleOwnerCannotDetachCurrentNativeSurface() = runTest {
        val source = NativeSurfaceOverlaySource.DesktopNavigationRail
        val staleOwner = NativeSurfaceOverlayCoordinator.attachNativeSurface(initiallyVisible = false)
        val currentOwner = NativeSurfaceOverlayCoordinator.attachNativeSurface(initiallyVisible = true)
        try {
            NativeSurfaceOverlayCoordinator.setActive(source, true)
            NativeSurfaceOverlayCoordinator.detachNativeSurface(staleOwner)

            assertFalse(
                NativeSurfaceOverlayCoordinator.awaitOverlayReady(
                    source = source,
                    timeoutMillis = 1L,
                ),
            )
            NativeSurfaceOverlayCoordinator.reportNativeSurfaceVisible(currentOwner, false)
            assertTrue(NativeSurfaceOverlayCoordinator.awaitOverlayReady(source))
        } finally {
            NativeSurfaceOverlayCoordinator.detachNativeSurface(currentOwner)
            NativeSurfaceOverlayCoordinator.setActive(source, false)
        }
    }

    @Test
    fun overlayReadinessWaitsForEveryAttachedSurface() = runTest {
        val source = NativeSurfaceOverlaySource.DesktopNavigationRail
        val visibleOwner = NativeSurfaceOverlayCoordinator.attachNativeSurface(initiallyVisible = true)
        val hiddenOwner = NativeSurfaceOverlayCoordinator.attachNativeSurface(initiallyVisible = false)
        try {
            NativeSurfaceOverlayCoordinator.setActive(source, true)

            assertFalse(
                NativeSurfaceOverlayCoordinator.awaitOverlayReady(
                    source = source,
                    timeoutMillis = 1L,
                ),
            )
            NativeSurfaceOverlayCoordinator.reportNativeSurfaceVisible(visibleOwner, false)
            assertTrue(NativeSurfaceOverlayCoordinator.awaitOverlayReady(source))
        } finally {
            NativeSurfaceOverlayCoordinator.detachNativeSurface(visibleOwner)
            NativeSurfaceOverlayCoordinator.detachNativeSurface(hiddenOwner)
            NativeSurfaceOverlayCoordinator.setActive(source, false)
        }
    }
}
