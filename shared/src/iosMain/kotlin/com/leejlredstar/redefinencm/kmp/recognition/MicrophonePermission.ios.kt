package com.leejlredstar.redefinencm.kmp.recognition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch

@Composable
actual fun rememberMicrophonePermissionRequester(
    onResult: (Boolean) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val currentOnResult = rememberUpdatedState(onResult)
    return remember(scope) {
        {
            requestIosMicrophonePermission { granted ->
                // AVAudioSession does not guarantee the permission callback thread. Keep
                // ViewModel state and AVPlayer operations on Compose's main scope.
                scope.launch { currentOnResult.value(granted) }
            }
        }
    }
}
