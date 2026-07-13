package com.leejlredstar.redefinencm.kmp.download

expect object DownloadServiceController {
    fun ensureRunning()

    /** Whether this platform persists and restores the common download queue across processes. */
    fun supportsPersistentDownloadQueue(): Boolean
}
