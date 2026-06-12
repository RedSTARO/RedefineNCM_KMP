package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImportFileLauncher(onImported: (String) -> Unit): () -> Unit {
    // TODO: UIDocumentPickerViewController — requires Swift-to-Kotlin callback bridge via iosApp
    return { }
}

@Composable
actual fun rememberExportFileLauncher(): (String) -> Unit {
    // TODO: UIActivityViewController — requires UIKit interop via iosApp
    return { _ -> }
}
