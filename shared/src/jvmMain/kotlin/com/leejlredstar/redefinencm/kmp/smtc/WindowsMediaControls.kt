package com.leejlredstar.redefinencm.kmp.smtc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Windows System Media Transport Controls (SMTC) integration — desktop "windows 媒体协议".
 *
 * COMPILE-VERIFIED part (this file): observes the shared [MediaControlsIntegrator] and is the
 * single place that forwards now-playing metadata + playback status to the OS. The OS-level SMTC
 * call itself is NOT implemented here: SMTC is a WinRT API with no classic-COM / JNA-WinRT path
 * that is runtime-verifiable from plain JVM, so it needs a tiny native helper. Until that helper
 * is present this is a safe no-op (also on non-Windows hosts).
 *
 * Native binding recipe (build-gated — needs MSVC + the Windows SDK / WinRT):
 *  1. Native helper DLL (C++/WinRT) exporting a C ABI, e.g.:
 *       void* smtc_create(int64_t hwnd);
 *       void  smtc_update(void* h, const wchar_t* title, const wchar_t* artist, int isPlaying);
 *       void  smtc_set_button_callback(void* h, void(*cb)(int buttonId));
 *       void  smtc_destroy(void* h);
 *     Internals: ISystemMediaTransportControlsInterop::GetForWindow(hwnd) ->
 *     SystemMediaTransportControls; enable IsPlay/Pause/Next/Previous; DisplayUpdater with
 *     MusicProperties (Title/Artist), Type=Music; PlaybackStatus; Update(); ButtonPressed -> cb.
 *  2. Obtain the Compose Desktop window's HWND (ComposeWindow -> AWT Window -> JNA
 *     com.sun.jna.Native.getWindowPointer) and pass it to smtc_create.
 *  3. Load the DLL via JNA, forward [MediaControlsIntegrator.metadata] into smtc_update, and route
 *     ButtonPressed callbacks back to the PlatformPlayer (play/pause/next/previous).
 *  Linux MPRIS (D-Bus) and macOS MPNowPlayingInfoCenter are the analogous, separate bindings.
 */
class WindowsMediaControls {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Begin observing shared metadata and forwarding it to the OS. Call once at app startup. */
    fun start() {
        scope.launch {
            MediaControlsIntegrator.metadata.collect { meta ->
                pushToOs(meta)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun pushToOs(meta: MediaControlMetadata) {
        // TODO (build-gated, see class KDoc): forward to the native SMTC helper DLL via JNA.
        // No-op until the native helper exists.
    }

    fun stop() {
        scope.cancel()
    }
}
