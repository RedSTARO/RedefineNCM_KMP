@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.recognition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberMicrophonePermissionRequester(
    onResult: (Boolean) -> Unit,
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    // getUserMedia is invoked by MicrophoneRecorder immediately after this user gesture. Let
    // that call preserve NotAllowed/NotFound/NotReadable/Security as distinct UI states instead
    // of collapsing every browser failure into a permission denial here.
    return remember { { currentOnResult.value(true) } }
}
