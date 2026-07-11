package com.leejlredstar.redefinencm.kmp.download

actual object DownloadServiceController {
    // The common manager owns the active coroutine queue in-page, matching the JVM target.
    // Browser navigation must not be changed as a side effect of enqueue/pause/resume operations.
    actual fun ensureRunning() = Unit
}
