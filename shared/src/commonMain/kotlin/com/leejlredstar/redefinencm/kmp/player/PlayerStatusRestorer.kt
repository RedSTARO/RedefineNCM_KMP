package com.leejlredstar.redefinencm.kmp.player

import com.leejlredstar.redefinencm.kmp.data.PlayerStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PlayerStatusRestoreState {
    data object Loading : PlayerStatusRestoreState
    data class Restored(val status: PlayerStatus?) : PlayerStatusRestoreState
    data class Failed(val message: String) : PlayerStatusRestoreState
}

/**
 * Restores the persisted queue once per process, independently from any screen or account model.
 *
 * Android's MediaSession service awaits this coordinator before exposing transport controls, so a
 * cold-start play command cannot be delivered to an empty player. The restored queue remains
 * paused until the user explicitly continues playback.
 */
class PlayerStatusRestorer internal constructor(
    private val awaitSettings: suspend () -> Unit,
    private val playerProvider: () -> PlatformPlayer,
    private val statusLoader: suspend () -> Result<PlayerStatus?>,
    private val onReady: () -> Unit,
    private val playerDispatcher: CoroutineDispatcher,
    workerDispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + workerDispatcher)
    private val ready = CompletableDeferred<Unit>()
    private val mutableState =
        MutableStateFlow<PlayerStatusRestoreState>(PlayerStatusRestoreState.Loading)

    val state: StateFlow<PlayerStatusRestoreState> = mutableState.asStateFlow()

    init {
        scope.launch {
            var playerInitialized = false
            try {
                awaitSettings()
                val player = withContext(playerDispatcher) { playerProvider() }
                playerInitialized = true
                val result = statusLoader()
                val failure = result.exceptionOrNull()
                if (failure != null) {
                    mutableState.value = PlayerStatusRestoreState.Failed(
                        failure.message ?: "播放状态读取失败",
                    )
                    return@launch
                }

                val status = result.getOrNull()
                if (status != null && status.playlist.isNotEmpty()) {
                    val items = status.playlist.map { persisted ->
                        MediaInfo(
                            id = persisted.id,
                            title = persisted.title,
                            artist = persisted.artist,
                            albumTitle = persisted.albumTitle,
                            artworkUri = persisted.artworkUri,
                            placeholderUri =
                                "redefinencm://playbackPlaceHolder?id=${persisted.id}",
                            duration = persisted.duration,
                            sourceId = persisted.sourceId,
                        )
                    }
                    withContext(playerDispatcher) {
                        // A platform service may already have restored or received a new queue.
                        if (player.queueSnapshot.value.items.isEmpty()) {
                            player.restoreQueue(
                                items = items,
                                startIndex = status.index.coerceIn(0, items.lastIndex),
                                positionMs = status.position,
                            )
                            player.setShuffleEnabled(status.isShuffling)
                        }
                    }
                }
                mutableState.value = PlayerStatusRestoreState.Restored(status)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = PlayerStatusRestoreState.Failed(
                    failure.message ?: "播放状态恢复失败",
                )
            } finally {
                if (playerInitialized) {
                    try {
                        withContext(playerDispatcher) { onReady() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        mutableState.value = PlayerStatusRestoreState.Failed(
                            failure.message ?: "播放状态初始化失败",
                        )
                    }
                }
                ready.complete(Unit)
            }
        }
    }

    suspend fun awaitRestored(): PlayerStatusRestoreState {
        ready.await()
        return state.value
    }
}
