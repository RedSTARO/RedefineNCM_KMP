package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.data.LyricCacheStatus
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.util.DownloadRequestItem
import com.leejlredstar.redefinencm.kmp.util.DownloadScanResult
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongSnapshot
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SongDownloader
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.deleteDownloadedSongFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

enum class DownloadTaskStatus {
    Queued,
    Resolving,
    Downloading,
    SavingLyrics,
    Paused,
    Completed,
    Deleted,
    Failed,
    Cancelled,
}

enum class DownloadLyricStatus {
    NotStarted,
    Saving,
    Saved,
    NoLyric,
    Failed,
}

data class SongDownloadTask(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: String,
    val playlistId: Long? = null,
    val status: DownloadTaskStatus = DownloadTaskStatus.Queued,
    val requestedQuality: String? = null,
    val actualQuality: String? = null,
    val lyricStatus: DownloadLyricStatus = DownloadLyricStatus.NotStarted,
    val progressBytes: Long = 0,
    val totalBytes: Long? = null,
    val fileName: String? = null,
    val errorMessage: String? = null,
    internal val executionGeneration: Long = 0L,
) {
    val progressFraction: Float
        get() {
            val total = totalBytes ?: return 0f
            if (total <= 0L) return 0f
            return (progressBytes.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

    val isActive: Boolean
        get() = status == DownloadTaskStatus.Queued ||
            status == DownloadTaskStatus.Resolving ||
            status == DownloadTaskStatus.Downloading ||
            status == DownloadTaskStatus.SavingLyrics

    val isTerminal: Boolean
        get() = status == DownloadTaskStatus.Completed ||
            status == DownloadTaskStatus.Deleted ||
            status == DownloadTaskStatus.Failed ||
            status == DownloadTaskStatus.Cancelled
}

data class DownloadQueueSummary(
    val total: Int = 0,
    val active: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val deleted: Int = 0,
    val paused: Int = 0,
)

internal data class DownloadTaskStatistics(
    val summary: DownloadQueueSummary,
    val firstActiveTask: SongDownloadTask?,
)

internal fun analyzeDownloadTasks(tasks: List<SongDownloadTask>): DownloadTaskStatistics {
    var active = 0
    var completed = 0
    var failed = 0
    var deleted = 0
    var paused = 0
    var firstActiveTask: SongDownloadTask? = null
    tasks.forEach { task ->
        if (task.isActive) {
            active += 1
            if (firstActiveTask == null) firstActiveTask = task
        }
        when (task.status) {
            DownloadTaskStatus.Completed -> completed += 1
            DownloadTaskStatus.Failed,
            DownloadTaskStatus.Cancelled,
            -> failed += 1
            DownloadTaskStatus.Deleted -> deleted += 1
            DownloadTaskStatus.Paused -> paused += 1
            else -> Unit
        }
    }
    return DownloadTaskStatistics(
        summary = DownloadQueueSummary(
            total = tasks.size,
            active = active,
            completed = completed,
            failed = failed,
            deleted = deleted,
            paused = paused,
        ),
        firstActiveTask = firstActiveTask,
    )
}

sealed interface LocalLibrarySyncState {
    data object Idle : LocalLibrarySyncState
    data object Syncing : LocalLibrarySyncState
    data class Error(val message: String) : LocalLibrarySyncState
}

class SongDownloadManager(
    private val repo: Repository,
    private val settings: PlatformSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _tasks = MutableStateFlow<List<SongDownloadTask>>(emptyList())
    private val _localLibrarySyncState = MutableStateFlow<LocalLibrarySyncState>(LocalLibrarySyncState.Idle)

    val tasks: StateFlow<List<SongDownloadTask>> = _tasks.asStateFlow()
    val localLibrarySyncState: StateFlow<LocalLibrarySyncState> = _localLibrarySyncState.asStateFlow()
    val summary: StateFlow<DownloadQueueSummary> = _tasks
        .map { tasks -> analyzeDownloadTasks(tasks).summary }
        .stateIn(scope, SharingStarted.Eagerly, DownloadQueueSummary())

    @Volatile
    private var workerJob: Job? = null
    private val activeExecution = MutableStateFlow<ActiveDownloadExecution?>(null)
    private val workerStartMutex = Mutex()
    private val syncMutex = Mutex()
    private var localLibrarySyncJob: Job? = null
    private val executionSequence = MutableStateFlow(0L)

    fun enqueuePlaylist(playlistId: Long) {
        DownloadServiceController.ensureRunning()
        scope.launch {
            val songs = repo.getPlaylistTrackAllOnce(playlistId)?.songs.orEmpty()
            enqueueResolvedSongs(songs, playlistId)
        }
    }

    fun enqueueSongs(songs: List<SongDetailSongs>, playlistId: Long? = null) {
        if (songs.isEmpty()) return
        DownloadServiceController.ensureRunning()
        scope.launch { enqueueResolvedSongs(songs, playlistId) }
    }

    private suspend fun enqueueResolvedSongs(songs: List<SongDetailSongs>, playlistId: Long? = null) {
        syncMutex.withLock {
            val localFiles = when (val scan = DownloadedSongsCache.refreshSnapshots()) {
                is DownloadScanResult.Success -> scan.snapshots.associateBy { it.id }
                is DownloadScanResult.Failure -> DownloadedSongsCache.snapshot()
            }
            val newTasks = songs.map { it.toDownloadTask(playlistId, localFiles) }
            _tasks.update { current ->
                val songDetails = songs.associateBy { it.id }
                mergeDownloadTasksForEnqueue(
                    current = current,
                    incoming = newTasks,
                    localFiles = localFiles,
                    songDetails = songDetails,
                )
            }
        }
        DownloadServiceController.ensureRunning()
        ensureWorker()
    }

    fun pause(taskId: Long) {
        val invalidatedGeneration = nextExecutionGeneration()
        updateTask(taskId) { task ->
            if (task.isActive) {
                task.copy(
                    status = DownloadTaskStatus.Paused,
                    executionGeneration = invalidatedGeneration,
                )
            } else {
                task
            }
        }
        cancelActiveTask(taskId)
    }

    fun resume(taskId: Long) {
        updateTask(taskId) { task ->
            if (task.status == DownloadTaskStatus.Paused) {
                task.copy(status = DownloadTaskStatus.Queued, errorMessage = null)
            } else {
                task
            }
        }
        DownloadServiceController.ensureRunning()
        ensureWorker()
    }

    fun retry(taskId: Long) {
        updateTask(taskId) { task ->
            if (
                task.status == DownloadTaskStatus.Failed ||
                task.status == DownloadTaskStatus.Cancelled ||
                task.status == DownloadTaskStatus.Deleted
            ) {
                task.copy(
                    status = DownloadTaskStatus.Queued,
                    progressBytes = 0,
                    totalBytes = null,
                    requestedQuality = null,
                    actualQuality = null,
                    lyricStatus = DownloadLyricStatus.NotStarted,
                    fileName = null,
                    errorMessage = null,
                )
            } else {
                task
            }
        }
        DownloadServiceController.ensureRunning()
        ensureWorker()
    }

    fun cancel(taskId: Long) {
        val invalidatedGeneration = nextExecutionGeneration()
        updateTask(taskId) { task ->
            if (task.isTerminal) {
                task
            } else {
                task.copy(
                    status = DownloadTaskStatus.Cancelled,
                    executionGeneration = invalidatedGeneration,
                )
            }
        }
        cancelActiveTask(taskId)
    }

    fun remove(taskId: Long) {
        cancelActiveTask(taskId)
        _tasks.update { tasks -> tasks.filterNot { it.id == taskId } }
    }

    fun deleteDownloadedSong(taskId: Long) {
        val invalidatedGeneration = nextExecutionGeneration()
        updateTask(taskId) { task ->
            if (task.isActive) {
                task.copy(
                    status = DownloadTaskStatus.Cancelled,
                    executionGeneration = invalidatedGeneration,
                )
            } else {
                task
            }
        }
        val cancelledExecution = cancelActiveTask(taskId)
        scope.launch {
            syncMutex.withLock {
                cancelledExecution?.join()
                val deleted = deleteDownloadedSongFile(taskId)
                if (deleted) DownloadedSongsCache.remove(taskId)
                // Always rescan. A platform delete may remove one duplicate while another provider
                // row/file remains; trusting a Boolean OR would falsely report the song as gone.
                val localFiles = when (val scanResult = DownloadedSongsCache.refreshSnapshots()) {
                    is DownloadScanResult.Failure -> {
                        updateTask(taskId) { task ->
                            task.copy(errorMessage = scanResult.message.ifBlank { "无法确认本地文件状态" })
                        }
                        return@withLock
                    }
                    is DownloadScanResult.Success -> scanResult.snapshots.associateBy { it.id }
                }
                _tasks.update { tasks ->
                    tasks.map { task ->
                        when {
                            task.id != taskId -> syncDownloadTaskWithLocalLibrary(
                                task = task,
                                snapshot = localFiles[task.id],
                                songDetail = null,
                            )
                            task.id !in localFiles -> markDownloadTaskDeleted(
                                task = task,
                                message = if (deleted) "已删除本地歌曲" else "本地文件已删除",
                            )
                            else -> task.copy(
                                // The file is still present, so keep the truthful state and leave
                                // the destructive action available for another attempt.
                                status = DownloadTaskStatus.Completed,
                                fileName = localFiles[taskId]?.fileName ?: task.fileName,
                                errorMessage = "删除本地歌曲失败",
                            )
                        }
                    }
                }
            }
        }
    }

    fun pauseAll() {
        val invalidatedGeneration = nextExecutionGeneration()
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.isActive) {
                    task.copy(
                        status = DownloadTaskStatus.Paused,
                        executionGeneration = invalidatedGeneration,
                    )
                } else {
                    task
                }
            }
        }
        activeExecution.value?.job?.cancel()
    }

    fun resumeAll() {
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.status == DownloadTaskStatus.Paused) {
                    task.copy(status = DownloadTaskStatus.Queued, errorMessage = null)
                } else {
                    task
                }
            }
        }
        DownloadServiceController.ensureRunning()
        ensureWorker()
    }

    fun cancelAll() {
        val invalidatedGeneration = nextExecutionGeneration()
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.isTerminal) {
                    task
                } else {
                    task.copy(
                        status = DownloadTaskStatus.Cancelled,
                        executionGeneration = invalidatedGeneration,
                    )
                }
            }
        }
        activeExecution.value?.job?.cancel()
    }

    fun clearFinished() {
        _tasks.update { tasks -> tasks.filterNot { it.isTerminal } }
    }

    fun clearCompleted() {
        _tasks.update { tasks -> tasks.filterNot { it.status == DownloadTaskStatus.Completed } }
    }

    fun syncWithLocalLibrary() {
        if (localLibrarySyncJob?.isActive == true) return
        localLibrarySyncJob = scope.launch {
            _localLibrarySyncState.value = LocalLibrarySyncState.Syncing
            try {
                val failureMessage = syncMutex.withLock {
                    reconcileWithLocalLibrary()
                }
                _localLibrarySyncState.value = if (failureMessage == null) {
                    LocalLibrarySyncState.Idle
                } else {
                    LocalLibrarySyncState.Error(failureMessage)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _localLibrarySyncState.value = LocalLibrarySyncState.Error(
                    failure.message ?: "本地音乐库同步失败",
                )
            }
        }
    }

    fun onCleared() {
        scope.cancel()
    }

    private suspend fun reconcileWithLocalLibrary(): String? {
        val localFiles = when (val scan = DownloadedSongsCache.refreshSnapshots()) {
            is DownloadScanResult.Failure ->
                return scan.message.ifBlank { "无法读取本地音乐库" }
            is DownloadScanResult.Success -> scan.snapshots.associateBy { it.id }
        }
        val knownIds = _tasks.value.mapTo(mutableSetOf()) { it.id }
        val localOnlyIds = localFiles.keys
            .filter { it !in knownIds }
            .sorted()
        val localOnlyDetails = if (localOnlyIds.isEmpty()) {
            emptyMap()
        } else {
            repo.getSongDetails(localOnlyIds).associateBy { it.id }
        }
        _tasks.update { tasks ->
            reconcileDownloadTasksWithLocalLibrary(
                tasks = tasks,
                localFiles = localFiles,
                songDetails = localOnlyDetails,
            )
        }
        return null
    }

    private fun ensureWorker() {
        DownloadServiceController.ensureRunning()
        scope.launch {
            workerStartMutex.withLock {
                // A cancelled Job remains here until invokeOnCompletion runs after all cleanup.
                // Do not open a new worker slot merely because isActive already became false.
                if (!canStartDownloadWorker(workerJob)) return@withLock
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    while (true) {
                        val next = _tasks.value
                            .firstOrNull { it.status == DownloadTaskStatus.Queued }
                            ?: break
                        val taskJob = scope.launch(start = CoroutineStart.LAZY) {
                            runTask(next.id)
                        }
                        val execution = ActiveDownloadExecution(next.id, taskJob)
                        activeExecution.value = execution
                        try {
                            taskJob.start()
                            taskJob.join()
                        } finally {
                            activeExecution.compareAndSet(execution, null)
                        }
                    }
                }
                workerJob = job
                job.invokeOnCompletion {
                    scope.launch {
                        val shouldRestart = workerStartMutex.withLock {
                            if (workerJob === job) workerJob = null
                            _tasks.value.any { task -> task.status == DownloadTaskStatus.Queued }
                        }
                        if (shouldRestart) ensureWorker()
                    }
                }
                job.start()
            }
        }
    }

    private fun cancelActiveTask(taskId: Long): Job? =
        activeDownloadJobForTask(activeExecution.value, taskId)?.also { it.cancel() }

    private suspend fun runTask(taskId: Long) {
        val claimed = claimQueuedTask(taskId) ?: return
        val task = claimed.task
        val generation = claimed.generation
        try {
            val quality = settings.getStringAsync(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)
            val requestedQuality = normalizeQualityLevel(quality)
            if (
                !transitionTask(taskId, generation, DownloadTaskStatus.Resolving) {
                    it.copy(
                        requestedQuality = requestedQuality,
                        lyricStatus = DownloadLyricStatus.NotStarted,
                        errorMessage = null,
                    )
                }
            ) return
            val urlData = repo.getSongUrls(listOf(taskId), quality).firstOrNull { it.id == taskId }
            currentCoroutineContext().ensureActive()
            val url = urlData?.url.orEmpty()
            if (url.isBlank()) {
                transitionTask(taskId, generation, DownloadTaskStatus.Resolving) {
                    it.copy(
                        status = DownloadTaskStatus.Failed,
                        errorMessage = "无法解析歌曲直链",
                    )
                }
                return
            }

            val totalBytes = urlData?.size?.takeIf { it > 0L } ?: task.totalBytes
            val actualQuality = normalizeQualityLevel(urlData?.level)
            val request = DownloadRequestItem(
                id = task.id,
                title = task.title,
                artist = task.artist,
                artworkUri = task.artworkUri,
                url = url,
                expectedBytes = totalBytes,
            )
            if (
                !transitionTask(taskId, generation, DownloadTaskStatus.Resolving) {
                    it.copy(
                        status = DownloadTaskStatus.Downloading,
                        requestedQuality = requestedQuality,
                        actualQuality = actualQuality,
                        progressBytes = 0,
                        totalBytes = totalBytes,
                        lyricStatus = DownloadLyricStatus.NotStarted,
                        errorMessage = null,
                    )
                }
            ) return
            val downloadedFile = SongDownloader.download(request) { downloaded, total ->
                updateOwnedTask(taskId, generation) { current ->
                    if (current.status != DownloadTaskStatus.Downloading) current
                    else current.copy(
                        progressBytes = downloaded,
                        totalBytes = total ?: current.totalBytes,
                    )
                }
            }
            currentCoroutineContext().ensureActive()
            if (
                !transitionTask(taskId, generation, DownloadTaskStatus.Downloading) {
                    it.copy(
                        status = DownloadTaskStatus.SavingLyrics,
                        actualQuality = actualQuality,
                        lyricStatus = DownloadLyricStatus.Saving,
                        fileName = downloadedFile.fileName,
                        errorMessage = null,
                    )
                }
            ) return
            val lyricStatus = when (repo.cacheLyric(taskId)) {
                LyricCacheStatus.Saved -> DownloadLyricStatus.Saved
                LyricCacheStatus.NoLyric -> DownloadLyricStatus.NoLyric
                LyricCacheStatus.Failed -> DownloadLyricStatus.Failed
            }
            currentCoroutineContext().ensureActive()
            if (
                !transitionTask(taskId, generation, DownloadTaskStatus.SavingLyrics) {
                    it.copy(
                        status = DownloadTaskStatus.Completed,
                        requestedQuality = requestedQuality,
                        actualQuality = actualQuality,
                        lyricStatus = lyricStatus,
                        progressBytes = it.totalBytes ?: it.progressBytes,
                        totalBytes = it.totalBytes ?: it.progressBytes.takeIf { bytes -> bytes > 0L },
                        fileName = downloadedFile.fileName,
                        errorMessage = null,
                    )
                }
            ) return
            DownloadedSongsCache.upsert(
                DownloadedSongSnapshot(
                    id = taskId,
                    fileName = downloadedFile.fileName,
                    uri = downloadedFile.uri,
                    sizeBytes = _tasks.value
                        .firstOrNull { it.id == taskId }
                        ?.progressBytes
                        ?.takeIf { it > 0L },
                    lastModifiedEpochMillis = null,
                )
            )
        } catch (e: CancellationException) {
            val status = _tasks.value.firstOrNull { it.id == taskId }?.status
            if (status != DownloadTaskStatus.Paused && status != DownloadTaskStatus.Cancelled) {
                updateOwnedTask(taskId, generation) { it.copy(status = DownloadTaskStatus.Paused) }
            }
            throw e
        } catch (t: Throwable) {
            updateOwnedTask(taskId, generation) {
                if (it.status == DownloadTaskStatus.Paused || it.status == DownloadTaskStatus.Cancelled) {
                    it
                } else {
                    it.copy(
                        status = DownloadTaskStatus.Failed,
                        errorMessage = t.message?.takeIf(String::isNotBlank) ?: "下载失败",
                    )
                }
            }
        }
    }

    private fun updateTask(taskId: Long, transform: (SongDownloadTask) -> SongDownloadTask) {
        _tasks.update { tasks -> tasks.map { task -> if (task.id == taskId) transform(task) else task } }
    }

    private data class ClaimedDownloadTask(
        val task: SongDownloadTask,
        val generation: Long,
    )

    /** Atomically moves one queued task to Resolving so stale worker selections cannot revive it. */
    private fun claimQueuedTask(taskId: Long): ClaimedDownloadTask? {
        val generation = nextExecutionGeneration()
        while (true) {
            val current = _tasks.value
            val index = current.indexOfFirst { it.id == taskId }
            if (index < 0) return null
            val task = current[index]
            if (task.status != DownloadTaskStatus.Queued) return null
            val updated = current.toMutableList().apply {
                this[index] = task.copy(
                    status = DownloadTaskStatus.Resolving,
                    lyricStatus = DownloadLyricStatus.NotStarted,
                    errorMessage = null,
                    executionGeneration = generation,
                )
            }
            if (_tasks.compareAndSet(current, updated)) {
                return ClaimedDownloadTask(task = task, generation = generation)
            }
        }
    }

    private fun nextExecutionGeneration(): Long = executionSequence.updateAndGet { it + 1L }

    private fun updateOwnedTask(
        taskId: Long,
        generation: Long,
        transform: (SongDownloadTask) -> SongDownloadTask,
    ) {
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.id == taskId && task.executionGeneration == generation) transform(task) else task
            }
        }
    }

    private fun transitionTask(
        taskId: Long,
        generation: Long,
        expectedStatus: DownloadTaskStatus,
        transform: (SongDownloadTask) -> SongDownloadTask,
    ): Boolean {
        while (true) {
            val current = _tasks.value
            val index = current.indexOfFirst { it.id == taskId }
            if (index < 0) return false
            val task = current[index]
            if (!canOwnedDownloadTaskTransition(task, generation, expectedStatus)) return false
            val updated = current.toMutableList().apply { this[index] = transform(task) }
            if (_tasks.compareAndSet(current, updated)) return true
        }
    }

    private fun normalizeQualityLevel(level: String?): String? {
        val normalized = level?.trim()?.lowercase()
        return normalized?.takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun SongDetailSongs.toDownloadTask(
        playlistId: Long?,
        localFiles: Map<Long, DownloadedSongSnapshot>,
    ): SongDownloadTask {
        val localFile = localFiles[id]
        val downloaded = localFile != null
        val localSize = localFile?.sizeBytes
        return SongDownloadTask(
            id = id,
            title = downloadDisplayTitle(),
            artist = downloadDisplayArtist(),
            artworkUri = al.picUrl,
            playlistId = playlistId,
            status = if (downloaded) DownloadTaskStatus.Completed else DownloadTaskStatus.Queued,
            lyricStatus = DownloadLyricStatus.NotStarted,
            progressBytes = if (downloaded) localSize ?: 1 else 0,
            totalBytes = if (downloaded) localSize ?: 1 else null,
            fileName = localFile?.fileName,
        )
    }

}

internal fun canStartDownloadWorker(existingWorker: Job?): Boolean = existingWorker == null

internal data class ActiveDownloadExecution(
    val taskId: Long,
    val job: Job,
)

internal fun activeDownloadJobForTask(
    execution: ActiveDownloadExecution?,
    taskId: Long,
): Job? = execution?.takeIf { it.taskId == taskId }?.job

internal fun canOwnedDownloadTaskTransition(
    task: SongDownloadTask,
    generation: Long,
    expectedStatus: DownloadTaskStatus,
): Boolean =
    task.status == expectedStatus && task.executionGeneration == generation

internal fun reconcileDownloadTasksWithLocalLibrary(
    tasks: List<SongDownloadTask>,
    localFiles: Map<Long, DownloadedSongSnapshot>,
    songDetails: Map<Long, SongDetailSongs> = emptyMap(),
): List<SongDownloadTask> {
    val merged = linkedMapOf<Long, SongDownloadTask>()
    tasks.forEach { task ->
        merged[task.id] = syncDownloadTaskWithLocalLibrary(
            task = task,
            snapshot = localFiles[task.id],
            songDetail = songDetails[task.id],
        )
    }
    localFiles.values
        .filter { snapshot -> snapshot.id !in merged }
        .sortedWith(
            compareByDescending<DownloadedSongSnapshot> { it.lastModifiedEpochMillis ?: 0L }
                .thenBy { it.id }
        )
        .forEach { snapshot ->
            merged[snapshot.id] = importedDownloadTask(snapshot, songDetails[snapshot.id])
        }
    return merged.values.toList()
}

internal fun mergeDownloadTasksForEnqueue(
    current: List<SongDownloadTask>,
    incoming: List<SongDownloadTask>,
    localFiles: Map<Long, DownloadedSongSnapshot>,
    songDetails: Map<Long, SongDetailSongs> = emptyMap(),
): List<SongDownloadTask> {
    val syncedCurrent = current.map { task ->
        syncDownloadTaskWithLocalLibrary(
            task = task,
            snapshot = localFiles[task.id],
            songDetail = songDetails[task.id],
        )
    }
    val merged = syncedCurrent.associateBy { it.id }.toMutableMap()
    val order = current.map { it.id }.toMutableList()
    incoming.forEach { task ->
        val existing = merged[task.id]
        when {
            existing == null -> {
                merged[task.id] = task
                order.add(task.id)
            }
            task.status == DownloadTaskStatus.Completed -> {
                merged[task.id] = existing.copy(
                    title = task.title,
                    artist = task.artist,
                    artworkUri = task.artworkUri,
                    playlistId = task.playlistId ?: existing.playlistId,
                    status = DownloadTaskStatus.Completed,
                    progressBytes = task.progressBytes,
                    totalBytes = task.totalBytes,
                    fileName = task.fileName,
                    errorMessage = null,
                )
            }
            existing.canRequeueForDownload() -> {
                merged[task.id] = existing.requeueFromDownload(task)
            }
        }
    }
    return order.mapNotNull { merged[it] }
}

private fun syncDownloadTaskWithLocalLibrary(
    task: SongDownloadTask,
    snapshot: DownloadedSongSnapshot?,
    songDetail: SongDetailSongs?,
): SongDownloadTask {
    val existsOnDisk = snapshot != null
    return when {
        task.status == DownloadTaskStatus.Completed && !existsOnDisk ->
            markDownloadTaskDeleted(task, "本地文件已删除")
        !task.isActive && existsOnDisk -> {
            val localSize = snapshot.sizeBytes
            task.copy(
                title = songDetail?.downloadDisplayTitle() ?: task.title,
                artist = songDetail?.downloadDisplayArtist() ?: task.artist,
                artworkUri = songDetail?.al?.picUrl?.takeIf(String::isNotBlank) ?: task.artworkUri,
                status = DownloadTaskStatus.Completed,
                progressBytes = localSize ?: task.progressBytes.takeIf { it > 0L } ?: 1,
                totalBytes = localSize ?: task.totalBytes ?: 1,
                fileName = snapshot.fileName,
                errorMessage = null,
            )
        }
        else -> task
    }
}

private fun markDownloadTaskDeleted(
    task: SongDownloadTask,
    message: String,
): SongDownloadTask = task.copy(
    status = DownloadTaskStatus.Deleted,
    requestedQuality = null,
    actualQuality = null,
    lyricStatus = DownloadLyricStatus.NotStarted,
    progressBytes = 0,
    totalBytes = null,
    fileName = null,
    errorMessage = message,
)

private fun SongDownloadTask.canRequeueForDownload(): Boolean =
    status == DownloadTaskStatus.Failed ||
        status == DownloadTaskStatus.Cancelled ||
        status == DownloadTaskStatus.Paused ||
        status == DownloadTaskStatus.Completed ||
        status == DownloadTaskStatus.Deleted

private fun SongDownloadTask.requeueFromDownload(incoming: SongDownloadTask): SongDownloadTask =
    copy(
        title = incoming.title,
        artist = incoming.artist,
        artworkUri = incoming.artworkUri,
        playlistId = incoming.playlistId ?: playlistId,
        status = DownloadTaskStatus.Queued,
        requestedQuality = null,
        actualQuality = null,
        lyricStatus = DownloadLyricStatus.NotStarted,
        progressBytes = 0,
        totalBytes = null,
        fileName = null,
        errorMessage = null,
    )

private fun importedDownloadTask(
    snapshot: DownloadedSongSnapshot,
    songDetail: SongDetailSongs?,
): SongDownloadTask {
    val size = snapshot.sizeBytes
    return SongDownloadTask(
        id = snapshot.id,
        title = songDetail?.downloadDisplayTitle() ?: "本地歌曲 ${snapshot.id}",
        artist = songDetail?.downloadDisplayArtist() ?: "本地文件",
        artworkUri = songDetail?.al?.picUrl.orEmpty(),
        status = DownloadTaskStatus.Completed,
        lyricStatus = DownloadLyricStatus.NotStarted,
        progressBytes = size ?: 1,
        totalBytes = size ?: 1,
        fileName = snapshot.fileName,
        errorMessage = null,
    )
}

private fun SongDetailSongs.downloadDisplayTitle(): String =
    name.ifBlank { "本地歌曲 $id" }

private fun SongDetailSongs.downloadDisplayArtist(): String =
    ar.joinToString(" / ") { it.name }.ifBlank { "未知艺术家" }
