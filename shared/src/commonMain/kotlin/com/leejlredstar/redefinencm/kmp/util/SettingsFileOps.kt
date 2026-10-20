package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable

/**
 * Returns a lambda that launches the platform file picker for a JSON settings file.
 * [onImported] is called with the raw file text on success, and [onFailed] when a file was chosen
 * but could not be read. Cancelling the picker calls neither.
 */
@Composable
expect fun rememberImportFileLauncher(onFailed: () -> Unit, onImported: (String) -> Unit): () -> Unit

/**
 * Returns a lambda that, when called with a JSON string, triggers the platform
 * save / share flow (share sheet on Android, save-file dialog on Desktop). [onFailed] is called
 * when the file could not be written or handed over; cancelling calls nothing.
 */
@Composable
expect fun rememberExportFileLauncher(onFailed: () -> Unit): (String) -> Unit
