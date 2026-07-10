@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol

private const val REQUEST_IMPORT_NOTIFICATION = "RedefineNCMRequestSettingsImport"
private const val IMPORTED_NOTIFICATION = "RedefineNCMSettingsImported"
private const val REQUEST_EXPORT_NOTIFICATION = "RedefineNCMRequestSettingsExport"
private const val SETTINGS_JSON_KEY = "json"

@Composable
actual fun rememberImportFileLauncher(onImported: (String) -> Unit): () -> Unit {
    val currentOnImported = rememberUpdatedState(onImported)
    val session = remember {
        IosSettingsImportSession { json -> currentOnImported.value(json) }
    }
    DisposableEffect(session) {
        onDispose(session::close)
    }
    return remember(session) { session::requestImport }
}

@Composable
actual fun rememberExportFileLauncher(): (String) -> Unit = remember {
    { json ->
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = REQUEST_EXPORT_NOTIFICATION,
            `object` = null,
            userInfo = mapOf(SETTINGS_JSON_KEY to json),
        )
    }
}

private class IosSettingsImportSession(
    private val onImported: (String) -> Unit,
) {
    private val observer: NSObjectProtocol = NSNotificationCenter.defaultCenter.addObserverForName(
        name = IMPORTED_NOTIFICATION,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { notification ->
        val json = notification?.userInfo?.get(SETTINGS_JSON_KEY) as? String
        if (!json.isNullOrBlank()) onImported(json)
    }

    fun requestImport() {
        NSNotificationCenter.defaultCenter.postNotificationName(
            aName = REQUEST_IMPORT_NOTIFICATION,
            `object` = null,
        )
    }

    fun close() {
        NSNotificationCenter.defaultCenter.removeObserver(observer)
    }
}
