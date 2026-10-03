package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.data.local.LocalPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface SettingsBackupImportResult {
    data object Imported : SettingsBackupImportResult
    data object Rejected : SettingsBackupImportResult
    data class SettingsSaveFailed(val cause: Exception) : SettingsBackupImportResult
    data class LibraryFailed(val cause: Exception) : SettingsBackupImportResult
}

private val settingsImportLock = Mutex()
private val importCount = MutableStateFlow(0)
private val importResult = MutableStateFlow<SettingsBackupImportResult?>(null)
internal val settingsImportCount = importCount.asStateFlow()
internal val settingsImportResult = importResult.asStateFlow()

internal fun consumeSettingsImportResult(result: SettingsBackupImportResult) {
    importResult.compareAndSet(result, null)
}

/** An import that has begun writing finishes independently of the settings page's lifetime.
 * Preferences and the library use different stores: report partial failure instead of promising
 * an atomic transaction across both. Imports are serialized, including across page recreation. */
internal suspend fun importSettingsBackup(
    json: String,
    applySettings: (String) -> Boolean,
    flushSettings: suspend () -> Unit,
    /** Called once after the write attempt, including a synchronous partial-write failure. */
    onSettingsApplied: () -> Unit,
    /** Refresh after the durable result; this does not announce another user write intent. */
    reloadSettings: () -> Unit,
    importLibrary: suspend (List<LocalPlaylist>) -> Result<Int>,
): SettingsBackupImportResult {
    currentCoroutineContext().ensureActive()
    importCount.update { it + 1 }
    try {
        return settingsImportLock.withLock {
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                // Keep the result even when the original page's coroutine has been cancelled.
                performImport(json, applySettings, flushSettings, onSettingsApplied, reloadSettings, importLibrary)
                    .also { importResult.value = it }
            }
        }
    } finally { importCount.update { it - 1 } }
}

private suspend fun performImport(
    json: String,
    applySettings: (String) -> Boolean,
    flushSettings: suspend () -> Unit,
    onSettingsApplied: () -> Unit,
    reloadSettings: () -> Unit,
    importLibrary: suspend (List<LocalPlaylist>) -> Result<Int>,
): SettingsBackupImportResult {
    val applied = applySettings(json)
    // In particular, revoke external lyric access before waiting for storage or playlists.
    onSettingsApplied()
    if (!applied) return SettingsBackupImportResult.Rejected
    try {
        flushSettings()
    } catch (cancelled: CancellationException) {
        reloadSettings()
        throw cancelled
    } catch (failure: Exception) {
        reloadSettings()
        return SettingsBackupImportResult.SettingsSaveFailed(failure)
    }
    reloadSettings()
    val playlists = decodeBackupLocalLibrary(json)
    if (!playlists.isNullOrEmpty()) {
        try {
            importLibrary(playlists).getOrThrow()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return SettingsBackupImportResult.LibraryFailed(failure)
        }
    }
    return SettingsBackupImportResult.Imported
}
