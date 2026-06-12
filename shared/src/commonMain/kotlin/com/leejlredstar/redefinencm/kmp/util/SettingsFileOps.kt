package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable

/**
 * Returns a lambda that launches the platform file picker for a JSON settings file.
 * [onImported] is called with the raw file text on success.
 */
@Composable
expect fun rememberImportFileLauncher(onImported: (String) -> Unit): () -> Unit

/**
 * Returns a lambda that, when called with a JSON string, triggers the platform
 * save / share flow (share sheet on Android, save-file dialog on Desktop).
 */
@Composable
expect fun rememberExportFileLauncher(): (String) -> Unit
