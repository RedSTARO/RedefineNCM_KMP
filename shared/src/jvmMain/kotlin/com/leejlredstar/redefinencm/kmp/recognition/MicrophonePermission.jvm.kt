package com.leejlredstar.redefinencm.kmp.recognition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberMicrophonePermissionRequester(
    onResult: (Boolean) -> Unit,
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember { { currentOnResult.value(true) } }
}
