package com.leejlredstar.redefinencm.kmp.smtc

import com.leejlredstar.redefinencm.kmp.DesktopOs
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import java.awt.Window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Runtime-selected desktop transport surface. */
class DesktopMediaControls(
    private val player: PlatformPlayer,
    private val osName: String = DesktopOs.currentName,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow(DesktopMediaControlsStatus.NotStarted)
    val status: StateFlow<DesktopMediaControlsStatus> = _status.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var backend: DesktopMediaControlsBackend? = null
    private var statusJob: Job? = null
    private var errorJob: Job? = null

    /**
     * Starts the native integration for the current OS. Windows needs [window] for its HWND;
     * Linux and macOS keep it only for MPRIS `Raise` and future app activation handling.
     */
    fun start(window: Window) {
        stop()
        val selected = when (DesktopOs.of(osName)) {
            DesktopOs.Windows -> WindowsMediaControlsBackend(player)
            DesktopOs.Linux -> LinuxMprisMediaControls(player)
            DesktopOs.MacOs -> MacOsMediaControls(player)
            DesktopOs.Other -> UnsupportedMediaControlsBackend(osName)
        }
        backend = selected
        statusJob = scope.launch { selected.status.collect { _status.value = it } }
        errorJob = scope.launch { selected.lastError.collect { _lastError.value = it } }
        selected.start(window)
    }

    fun stop() {
        statusJob?.cancel()
        errorJob?.cancel()
        statusJob = null
        errorJob = null
        backend?.stop()
        backend = null
        _lastError.value = null
        _status.value = DesktopMediaControlsStatus.NotStarted
    }
}

enum class DesktopMediaControlsStatus {
    NotStarted,
    UnsupportedHost,
    Forwarding,
    NativeError,
}

internal interface DesktopMediaControlsBackend {
    val status: StateFlow<DesktopMediaControlsStatus>
    val lastError: StateFlow<String?>
    fun start(window: Window)
    fun stop()
}

private class WindowsMediaControlsBackend(
    player: PlatformPlayer,
) : DesktopMediaControlsBackend {
    private val delegate = WindowsMediaControls(player)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _status = MutableStateFlow(DesktopMediaControlsStatus.NotStarted)
    override val status: StateFlow<DesktopMediaControlsStatus> = _status.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()
    private var statusJob: Job? = null
    private var errorJob: Job? = null

    override fun start(window: Window) {
        statusJob?.cancel()
        errorJob?.cancel()
        statusJob = scope.launch {
            delegate.status.collect { state ->
                _status.value = when (state) {
                    WindowsMediaControls.IntegrationStatus.NotStarted -> DesktopMediaControlsStatus.NotStarted
                    WindowsMediaControls.IntegrationStatus.UnsupportedHost -> DesktopMediaControlsStatus.UnsupportedHost
                    WindowsMediaControls.IntegrationStatus.Forwarding -> DesktopMediaControlsStatus.Forwarding
                    WindowsMediaControls.IntegrationStatus.NativeError -> DesktopMediaControlsStatus.NativeError
                }
            }
        }
        errorJob = scope.launch { delegate.lastError.collect { _lastError.value = it } }
        delegate.start(window)
    }

    override fun stop() {
        statusJob?.cancel()
        errorJob?.cancel()
        statusJob = null
        errorJob = null
        delegate.stop()
        _lastError.value = null
        _status.value = DesktopMediaControlsStatus.NotStarted
    }
}

private class UnsupportedMediaControlsBackend(
    private val osName: String,
) : DesktopMediaControlsBackend {
    private val _status = MutableStateFlow(DesktopMediaControlsStatus.NotStarted)
    override val status: StateFlow<DesktopMediaControlsStatus> = _status.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override fun start(window: Window) {
        _lastError.value = "No desktop media-controls backend for os.name=$osName"
        _status.value = DesktopMediaControlsStatus.UnsupportedHost
        System.err.println("Desktop media controls unsupported: os.name=$osName")
    }

    override fun stop() {
        _lastError.value = null
        _status.value = DesktopMediaControlsStatus.NotStarted
    }
}
