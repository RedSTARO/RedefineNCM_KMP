package com.leejlredstar.redefinencm.kmp.download

actual object DownloadServiceController {
    actual fun supportsPersistentDownloadQueue(): Boolean = false

    actual fun ensureRunning() = Unit
}
