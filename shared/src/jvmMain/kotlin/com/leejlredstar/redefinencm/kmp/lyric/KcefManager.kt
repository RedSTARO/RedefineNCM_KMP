package com.leejlredstar.redefinencm.kmp.lyric

import dev.datlag.kcef.KCEF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Process-global KCEF (Chromium) lifecycle for the desktop WebView lyric page.
 *
 * KCEF.init() may be called only once per process and does not cleanly re-init, so this
 * guards a single init and **never disposes KCEF** — individual browsers are disposed by
 * their owning screen. The first init downloads/extracts a Chromium runtime (~150 MB) into
 * [installDir]; subsequent launches reuse it.
 */
object KcefManager {
    sealed interface State {
        data object Idle : State
        data class Downloading(val pct: Int) : State
        data object Initializing : State
        data object Ready : State
        /** KCEF extracted its bundled Chromium and needs an app restart to load it. */
        data object RestartRequired : State
        data class Failed(val error: Throwable) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    @Volatile
    private var started = false

    @Volatile
    private var lastUpdate = System.currentTimeMillis()

    private var lastLoggedDecile = -1

    private fun update(s: State) {
        lastUpdate = System.currentTimeMillis()
        _state.value = s
    }

    private fun log(msg: String) = println("AMLL[kcef] $msg")

    /** Kick off KCEF init once. Safe to call from every recomposition. */
    fun ensureInit() {
        if (started) return
        started = true
        val installDir = File(System.getProperty("user.home"), ".redefinencm/kcef-bundle")
        log("init starting; installDir=$installDir")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                update(State.Initializing)
                KCEF.init(
                    builder = {
                        installDir(installDir)
                        progress {
                            onDownloading { pct ->
                                val decile = (pct.toInt() / 10)
                                if (decile != lastLoggedDecile) {
                                    lastLoggedDecile = decile
                                    log("downloading ${pct.toInt()}%")
                                }
                                update(State.Downloading(pct.toInt()))
                            }
                            onInitialized {
                                log("initialized — ready")
                                update(State.Ready)
                            }
                        }
                    },
                    onError = { t ->
                        log("init error: ${t?.message}")
                        t?.printStackTrace()
                        update(State.Failed(t ?: RuntimeException("KCEF init failed")))
                    },
                    onRestartRequired = {
                        log("restart required — relaunch the app to finish KCEF init")
                        update(State.RestartRequired)
                    },
                )
                log("KCEF.init() returned")
            } catch (t: Throwable) {
                log("init threw: ${t.message}")
                t.printStackTrace()
                update(State.Failed(t))
            }
        }
        // Stall watchdog: if nothing progresses (no download %, no ready/error) for STALL_MS,
        // fail so the screen falls back to FullLyricScreen instead of spinning forever.
        scope.launch {
            while (true) {
                delay(5_000)
                val s = _state.value
                if (s is State.Ready || s is State.Failed || s is State.RestartRequired) return@launch
                if (System.currentTimeMillis() - lastUpdate > STALL_MS) {
                    log("init stalled (no progress for ${STALL_MS / 1000}s) — falling back")
                    update(State.Failed(RuntimeException("KCEF init stalled (no progress)")))
                    return@launch
                }
            }
        }
    }

    private const val STALL_MS = 90_000L
}
