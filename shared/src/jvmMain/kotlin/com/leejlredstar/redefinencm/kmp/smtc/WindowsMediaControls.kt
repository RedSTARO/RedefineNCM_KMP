package com.leejlredstar.redefinencm.kmp.smtc

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Windows System Media Transport Controls (SMTC) integration — desktop "windows 媒体协议".
 *
 * Observes the shared [MediaControlsIntegrator] and forwards now-playing metadata + playback
 * status to a tiny native WinRT helper when that helper is present. SMTC is a WinRT API, so the JVM
 * side owns lifecycle/state and the native side owns `ISystemMediaTransportControlsInterop`.
 *
 * Native binding recipe (build-gated — needs MSVC + the Windows SDK / WinRT):
 *  1. Native helper DLL (C++/WinRT) exporting this C ABI:
 *       int smtc_update(
 *           const wchar_t* title,
 *           const wchar_t* artist,
 *           const wchar_t* album,
 *           const wchar_t* artworkUri,
 *           long long durationMs,
 *           long long positionMs,
 *           int isPlaying
 *       );
 *       int smtc_clear();
 *     Internals: ISystemMediaTransportControlsInterop::GetForWindow(hwnd) ->
 *     SystemMediaTransportControls; enable IsPlay/Pause/Next/Previous; DisplayUpdater with
 *     MusicProperties (Title/Artist), Type=Music; PlaybackStatus; Update(); ButtonPressed -> cb.
 *  2. Put the DLL on `java.library.path` or beside the app executable as `redefinencm_smtc`.
 *  Linux MPRIS (D-Bus) and macOS MPNowPlayingInfoCenter are the analogous, separate bindings.
 */
class WindowsMediaControls {
    enum class IntegrationStatus {
        NotStarted,
        UnsupportedHost,
        MissingNativeHelper,
        Forwarding,
        NativeError,
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow(IntegrationStatus.NotStarted)
    val status: StateFlow<IntegrationStatus> = _status.asStateFlow()
    private val nativeHelper: SmtcNative? by lazy { loadNativeHelper() }

    /** Begin observing shared metadata and forwarding it to the OS. Call once at app startup. */
    fun start() {
        if (!isWindows()) {
            _status.value = IntegrationStatus.UnsupportedHost
            return
        }
        scope.launch {
            MediaControlsIntegrator.metadata.collect { meta ->
                pushToOs(meta)
            }
        }
    }

    private fun pushToOs(meta: MediaControlMetadata) {
        val helper = nativeHelper ?: run {
            _status.value = IntegrationStatus.MissingNativeHelper
            return
        }
        val result = if (meta.title.isBlank() && meta.artist.isBlank()) {
            helper.smtc_clear()
        } else {
            helper.smtc_update(
                WString(meta.title),
                WString(meta.artist),
                WString(meta.album),
                WString(meta.artworkUri),
                meta.duration,
                meta.position,
                if (meta.isPlaying) 1 else 0,
            )
        }
        _status.value = if (result == 0) IntegrationStatus.Forwarding else IntegrationStatus.NativeError
    }

    fun stop() {
        scope.cancel()
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").contains("Windows", ignoreCase = true)

    private fun loadNativeHelper(): SmtcNative? =
        runCatching {
            Native.load("redefinencm_smtc", SmtcNative::class.java)
        }.getOrNull()

    private interface SmtcNative : Library {
        fun smtc_update(
            title: WString,
            artist: WString,
            album: WString,
            artworkUri: WString,
            durationMs: Long,
            positionMs: Long,
            isPlaying: Int,
        ): Int

        fun smtc_clear(): Int
    }
}
