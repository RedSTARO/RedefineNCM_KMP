package com.leejlredstar.redefinencm.kmp.util

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A song's files the system will delete only once the user confirms; see [RefusedDeletions]. */
internal class MediaDeletionRequest(val songId: Long, val intentSender: IntentSender)

/** Confirmations waiting for a screen that can show them. */
internal object MediaDeletionConsentRequests {
    private val _pending = MutableStateFlow<List<MediaDeletionRequest>>(emptyList())
    val pending: StateFlow<List<MediaDeletionRequest>> = _pending.asStateFlow()

    fun add(request: MediaDeletionRequest) {
        _pending.update { requests -> requests.filterNot { it.songId == request.songId } + request }
    }

    fun remove(request: MediaDeletionRequest) {
        _pending.update { requests -> requests - request }
    }
}

/**
 * Shows the system's confirmation for downloads an earlier install of the app saved, one song at
 * a time. They failed to delete with a bare "couldn't delete", and the user had to find and
 * remove them in another app.
 */
@Composable
actual fun MediaDeletionConsentHost(onConfirmed: (songId: Long) -> Unit) {
    val pending by MediaDeletionConsentRequests.pending.collectAsState()
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)
    var inFlight by remember { mutableStateOf<MediaDeletionRequest?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val finished = inFlight ?: return@rememberLauncherForActivityResult
        inFlight = null
        MediaDeletionConsentRequests.remove(finished)
        if (result.resultCode == Activity.RESULT_OK) currentOnConfirmed(finished.songId)
    }
    val next = pending.firstOrNull()
    LaunchedEffect(next, inFlight) {
        if (next == null || inFlight != null) return@LaunchedEffect
        inFlight = next
        runCatching { launcher.launch(IntentSenderRequest.Builder(next.intentSender).build()) }
            .onFailure {
                inFlight = null
                MediaDeletionConsentRequests.remove(next)
            }
    }
}
