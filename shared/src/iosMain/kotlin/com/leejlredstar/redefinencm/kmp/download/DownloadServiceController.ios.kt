package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.util.IosBackgroundDownloadCoordinator

actual object DownloadServiceController {
    actual fun ensureRunning() {
        IosBackgroundDownloadCoordinator.ensureStarted()
    }

    /** Called by UIApplicationDelegate when iOS reconnects the durable background session. */
    fun handleBackgroundEvents(identifier: String, completionHandler: () -> Unit): Boolean =
        IosBackgroundDownloadCoordinator.handleBackgroundEvents(identifier, completionHandler)
}
