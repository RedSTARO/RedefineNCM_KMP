package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.lyric.LyricQuery
import com.leejlredstar.redefinencm.kmp.lyric.LyricResolution
import com.leejlredstar.redefinencm.kmp.lyric.LyricResolver
import com.leejlredstar.redefinencm.kmp.lyric.LyricSourceMode
import com.leejlredstar.redefinencm.kmp.util.DownloadRequestItem
import com.leejlredstar.redefinencm.kmp.util.DownloadScanResult
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongSnapshot
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.LocalLyricFormat
import com.leejlredstar.redefinencm.kmp.util.LocalMediaAssetSnapshot
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SongDownloader
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.deleteDownloadedSongFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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

enum class DownloadArtworkStatus {
    NotStarted,
    Saving,
    Saved,
    NoArtwork,
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
    val artworkStatus: DownloadArtworkStatus = DownloadArtworkStatus.NotStarted,
    val lyricFormat: LocalLyricFormat? = null,
    val lyricFileName: String? = null,
    val artworkFileName: String? = null,
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
    private val lyricResolver: LyricResolver,
    private val localMediaAssets: LocalMediaAssets,
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
    private val persistenceMutex = Mutex()
    private val assetJobsMutex = Mutex()
    private val assetJobs = mutableMapOf<Long, Job>()
    private val assetSaveSemaphore = Semaphore(MAX_CONCURRENT_ASSET_SAVES)
    private var localLibrarySyncJob: Job? = null
    private val executionSequence = MutableStateFlow(0L)
    private val restoreCompleted = CompletableDeferred<Unit>()
    private val persistenceRequests = Channel<Unit>(Channel.CONFLATED)
    private val _persistenceError = MutableStateFlow<String?>(null)
    private val destructiveTaskIds = MutableStateFlow<Set<Long>>(emptySet())
    private val persistentDownloadQueueSupported =
        DownloadServiceController.supportsPersistentDownloadQueue()

    val persistenceError: StateFlow<String?> = _persistenceError.asStateFlow()
    internal val destructiveTaskIdsInProgress: StateFlow<Set<Long>> =
        destructiveTaskIds.asStateFlow()

    init {
        if (persistentDownloadQueueSupported) {
            scope.launch { restorePersistedQueue() }
            scope.launch {
                restoreCompleted.await()
                _tasks.drop(1).collect {
                    persistenceRequests.trySend(Unit)
                }
            }
            scope.launch {
                restoreCompleted.await()
                for (ignored in persistenceRequests) {
                    delay(PERSISTENCE_INTERVAL_MS)
                    persistCurrentQueue()
                }
            }
        } else {
            restoreCompleted.complete(Unit)
        }
    }

    fun enqueuePlaylist(playlistId: Long) {
        scope.launch {
            restoreCompleted.await()
            val songs = repo.getPlaylistTrackAllOnce(playlistId)?.songs.orEmpty()
            enqueueResolvedSongs(songs, playlistId)
        }
    }

    fun enqueueSongs(songs: List<SongDetailSongs>, playlistId: Long? = null) {
        if (songs.isEmpty()) return
        scope.launch {
            restoreCompleted.await()
            enqueueResolvedSongs(songs, playlistId)
        }
    }

    private suspend fun enqueueResolvedSongs(songs: List<SongDetailSongs>, playlistId: Long? = null) {
        val enqueued = syncMutex.withLock {
            val acceptedSongs = songs.filter { song ->
                canReactivateDownloadTask(song.id, destructiveTaskIds.value)
            }
            if (acceptedSongs.isEmpty()) return@withLock false
            val localFiles = when (val scan = DownloadedSongsCache.refreshSnapshots()) {
                is DownloadScanResult.Success -> scan.snapshots.associateBy { it.id }
                is DownloadScanResult.Failure -> DownloadedSongsCache.snapshot()
            }
            val localAssets = supervisorScope {
                acceptedSongs.mapNotNull { song ->
                    if (song.id !in localFiles) return@mapNotNull null
                    async {
                        song.id to runCatching { localMediaAssets.inspect(song.id) }
                            .getOrDefault(LocalMediaAssetSnapshot())
                    }
                }.awaitAll().toMap()
            }
            val newTasks = acceptedSongs.map {
                it.toDownloadTask(playlistId, localFiles, localAssets)
            }
            _tasks.update { current ->
                val songDetails = acceptedSongs.associateBy { it.id }
                mergeDownloadTasksForEnqueue(
                    current = current,
                    incoming = newTasks,
                    localFiles = localFiles,
                    songDetails = songDetails,
                    localAssets = localAssets,
                )
            }
            true
        }
        if (!enqueued) return
        if (!persistCurrentQueue()) {
            failQueuedTasks("无法保存下载队列，下载未启动")
            return
        }
        ensureWorker()
    }

    fun pause(taskId: Long) {
        val affectedIds = invalidateTasksForControl(DownloadTaskStatus.Paused) { task ->
            task.id == taskId && canPauseDownloadTask(task)
        }
        if (taskId in affectedIds) {
            cancelActiveTask(taskId)
            persistStructuralChange()
        }
    }

    fun resume(taskId: Long) {
        if (!canReactivateDownloadTask(taskId, destructiveTaskIds.value)) return
        updateTask(taskId) { task ->
            if (task.status == DownloadTaskStatus.Paused) {
                task.copy(status = DownloadTaskStatus.Queued, errorMessage = null)
            } else {
                task
            }
        }
        ensureWorker()
    }

    fun retry(taskId: Long) {
        if (!canReactivateDownloadTask(taskId, destructiveTaskIds.value)) return
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
                    artworkStatus = DownloadArtworkStatus.NotStarted,
                    lyricFormat = null,
                    lyricFileName = null,
                    artworkFileName = null,
                    fileName = null,
                    errorMessage = null,
                )
            } else {
                task
            }
        }
        ensureWorker()
    }

    /** Saves or refreshes only the lyric sidecars for an already downloaded song. */
    fun saveLyrics(taskId: Long) {
        startAssetBackfill(taskId, saveLyrics = true, saveArtwork = false)
    }

    /** Saves or refreshes only the artwork sidecar for an already downloaded song. */
    fun saveArtwork(taskId: Long) {
        startAssetBackfill(taskId, saveLyrics = false, saveArtwork = true)
    }

    private fun startAssetBackfill(
        taskId: Long,
        saveLyrics: Boolean,
        saveArtwork: Boolean,
    ) {
        if (!saveLyrics && !saveArtwork) return
        if (!canReactivateDownloadTask(taskId, destructiveTaskIds.value)) return
        scope.launch {
            restoreCompleted.await()
            assetJobsMutex.withLock {
                if (assetJobs[taskId]?.isActive == true) return@withLock
                val task = _tasks.value.firstOrNull {
                    it.id == taskId && it.status == DownloadTaskStatus.Completed
                } ?: return@withLock
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    backfillLocalAssets(task, saveLyrics, saveArtwork)
                }
                assetJobs[taskId] = job
                job.invokeOnCompletion {
                    scope.launch {
                        assetJobsMutex.withLock {
                            if (assetJobs[taskId] === job) assetJobs.remove(taskId)
                        }
                    }
                }
                job.start()
            }
        }
    }

    private suspend fun backfillLocalAssets(
        originalTask: SongDownloadTask,
        saveLyrics: Boolean,
        saveArtwork: Boolean,
    ) {
        val taskId = originalTask.id
        if (!canReactivateDownloadTask(taskId, destructiveTaskIds.value)) return
        val localAudio = DownloadedSongsCache.snapshot()[taskId]
            ?: when (val scan = DownloadedSongsCache.refreshSnapshots()) {
                is DownloadScanResult.Success -> scan.snapshots.firstOrNull { it.id == taskId }
                is DownloadScanResult.Failure -> null
            }
            ?: return

        var task = originalTask
        if (task.artworkUri.isBlank() || task.title.startsWith("本地歌曲 ")) {
            repo.getSongDetails(listOf(taskId)).firstOrNull { it.id == taskId }?.let { detail ->
                task = task.copy(
                    title = detail.downloadDisplayTitle(),
                    artist = detail.downloadDisplayArtist(),
                    artworkUri = detail.al.picUrl,
                )
                val enriched = task
                updateTask(taskId) { current ->
                    if (current.status == DownloadTaskStatus.Completed) {
                        current.copy(
                            title = enriched.title,
                            artist = enriched.artist,
                            artworkUri = enriched.artworkUri,
                        )
                    } else {
                        current
                    }
                }
            }
        }

        updateTask(taskId) { current ->
            if (current.status != DownloadTaskStatus.Completed) {
                current
            } else {
                current.copy(
                    fileName = localAudio.fileName,
                    lyricStatus = if (saveLyrics) {
                        DownloadLyricStatus.Saving
                    } else {
                        current.lyricStatus
                    },
                    artworkStatus = if (saveArtwork) {
                        DownloadArtworkStatus.Saving
                    } else {
                        current.artworkStatus
                    },
                    errorMessage = null,
                )
            }
        }

        val results = saveSelectedLocalAssets(task, saveLyrics, saveArtwork)
        currentCoroutineContext().ensureActive()
        if (!canReactivateDownloadTask(taskId, destructiveTaskIds.value)) return
        updateTask(taskId) { current ->
            if (current.status != DownloadTaskStatus.Completed || current.fileName != localAudio.fileName) {
                current
            } else {
                current.withLocalAssetResults(results)
            }
        }
        persistCurrentQueue()
    }

    fun cancel(taskId: Long) {
        val affectedIds = invalidateTasksForControl(DownloadTaskStatus.Cancelled) { task ->
            task.id == taskId && canCancelDownloadTask(task)
        }
        if (taskId in affectedIds) {
            cancelActiveTask(taskId)
            persistStructuralChange()
        }
    }

    fun remove(taskId: Long) {
        if (_tasks.value.none { it.id == taskId && it.isTerminal }) return
        val acquiredIds = beginDestructiveOperation(setOf(taskId))
        if (acquiredIds.isEmpty()) return
        val cancelledExecution = cancelActiveTask(taskId)
        scope.launch {
            try {
                restoreCompleted.await()
                cancelledExecution?.join()
                syncMutex.withLock {
                    if (!discardPartialOrRecordFailure(taskId)) return@withLock
                    _tasks.update { tasks -> tasks.filterNot { it.id == taskId } }
                }
                persistCurrentQueue()
            } finally {
                finishDestructiveOperation(acquiredIds)
            }
        }
    }

    fun deleteDownloadedSong(taskId: Long) {
        if (_tasks.value.none {
                it.id == taskId &&
                    (it.status == DownloadTaskStatus.Completed ||
                        it.status == DownloadTaskStatus.Deleted)
            }
        ) return
        val acquiredIds = beginDestructiveOperation(setOf(taskId))
        if (acquiredIds.isEmpty()) return
        val cancelledExecution = cancelActiveTask(taskId)
        scope.launch {
            try {
                val cancelledAssetJob = assetJobsMutex.withLock {
                    assetJobs.remove(taskId)?.also(Job::cancel)
                }
                cancelledAssetJob?.join()
                syncMutex.withLock {
                    cancelledExecution?.join()
                    if (!discardPartialOrRecordFailure(taskId)) return@withLock
                    val deleted = deleteDownloadedSongFile(taskId)
                    val assetDeleteResult = runCatching { localMediaAssets.delete(taskId) }
                    val deletedAssets = assetDeleteResult.getOrDefault(false)
                    val assetDeleteFailure = assetDeleteResult.exceptionOrNull()
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
                                task.id !in localFiles -> if (assetDeleteFailure == null) {
                                    markDownloadTaskDeleted(
                                        task = task,
                                        message = if (deleted || deletedAssets) {
                                            "已删除本地歌曲"
                                        } else {
                                            "本地文件已删除"
                                        },
                                    )
                                } else {
                                    task.copy(
                                        status = DownloadTaskStatus.Deleted,
                                        requestedQuality = null,
                                        actualQuality = null,
                                        progressBytes = 0,
                                        totalBytes = null,
                                        fileName = null,
                                        errorMessage = assetDeleteFailure.message
                                            ?.takeIf(String::isNotBlank)
                                            ?.let { "音频已删除，但歌词或封面清理失败：$it" }
                                            ?: "音频已删除，但歌词或封面清理失败",
                                    )
                                }
                                else -> task.copy(
                                    // The file is still present, so keep the truthful state and leave
                                    // the destructive action available for another attempt.
                                    status = DownloadTaskStatus.Completed,
                                    fileName = localFiles[taskId]?.fileName ?: task.fileName,
                                    lyricStatus = if (assetDeleteFailure == null) {
                                        DownloadLyricStatus.NotStarted
                                    } else {
                                        task.lyricStatus
                                    },
                                    artworkStatus = if (assetDeleteFailure == null) {
                                        DownloadArtworkStatus.NotStarted
                                    } else {
                                        task.artworkStatus
                                    },
                                    lyricFormat = if (assetDeleteFailure == null) null else task.lyricFormat,
                                    lyricFileName = if (assetDeleteFailure == null) null else task.lyricFileName,
                                    artworkFileName = if (assetDeleteFailure == null) null else task.artworkFileName,
                                    errorMessage = "删除本地歌曲失败",
                                )
                            }
                        }
                    }
                }
                persistCurrentQueue()
            } finally {
                finishDestructiveOperation(acquiredIds)
            }
        }
    }

    fun pauseAll() {
        val affectedIds = invalidateTasksForControl(DownloadTaskStatus.Paused) { task ->
            canPauseDownloadTask(task)
        }
        if (affectedIds.isNotEmpty()) {
            cancelActiveExecutionForTasks(affectedIds)
            persistStructuralChange()
        }
    }

    fun resumeAll() {
        val blockedIds = destructiveTaskIds.value
        _tasks.update { tasks ->
            tasks.map { task ->
                if (
                    task.status == DownloadTaskStatus.Paused &&
                    canReactivateDownloadTask(task.id, blockedIds)
                ) {
                    task.copy(status = DownloadTaskStatus.Queued, errorMessage = null)
                } else {
                    task
                }
            }
        }
        ensureWorker()
    }

    fun cancelAll() {
        val affectedIds = invalidateTasksForControl(DownloadTaskStatus.Cancelled) { task ->
            canCancelDownloadTask(task)
        }
        if (affectedIds.isNotEmpty()) {
            cancelActiveExecutionForTasks(affectedIds)
            persistStructuralChange()
        }
    }

    fun clearFinished() {
        val candidateIds = _tasks.value
            .filter { it.isTerminal }
            .mapTo(mutableSetOf()) { it.id }
        val acquiredIds = beginDestructiveOperation(candidateIds)
        if (acquiredIds.isEmpty()) return
        val cancelledExecution = cancelActiveExecutionForTasks(acquiredIds)
        scope.launch {
            try {
                restoreCompleted.await()
                cancelledExecution?.join()
                syncMutex.withLock {
                    val removableIds = _tasks.value
                        .filter { it.id in acquiredIds && it.isTerminal }
                        .filter { discardPartialOrRecordFailure(it.id) }
                        .mapTo(mutableSetOf()) { it.id }
                    _tasks.update { tasks -> tasks.filterNot { it.id in removableIds } }
                }
                persistCurrentQueue()
            } finally {
                finishDestructiveOperation(acquiredIds)
            }
        }
    }

    fun clearCompleted() {
        val candidateIds = _tasks.value
            .filter { it.status == DownloadTaskStatus.Completed }
            .mapTo(mutableSetOf()) { it.id }
        val acquiredIds = beginDestructiveOperation(candidateIds)
        if (acquiredIds.isEmpty()) return
        val cancelledExecution = cancelActiveExecutionForTasks(acquiredIds)
        scope.launch {
            try {
                restoreCompleted.await()
                cancelledExecution?.join()
                syncMutex.withLock {
                    val removableIds = _tasks.value
                        .filter {
                            it.id in acquiredIds &&
                                it.status == DownloadTaskStatus.Completed
                        }
                        .filter { discardPartialOrRecordFailure(it.id) }
                        .mapTo(mutableSetOf()) { it.id }
                    _tasks.update { tasks -> tasks.filterNot { it.id in removableIds } }
                }
                persistCurrentQueue()
            } finally {
                finishDestructiveOperation(acquiredIds)
            }
        }
    }

    fun syncWithLocalLibrary() {
        if (localLibrarySyncJob?.isActive == true) return
        localLibrarySyncJob = scope.launch {
            restoreCompleted.await()
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

    suspend fun awaitRestored() {
        restoreCompleted.await()
    }

    /** Forces the latest Android queue snapshot to durable storage before its service stops. */
    internal suspend fun flushPersistentDownloadQueue(): Boolean {
        restoreCompleted.await()
        return persistCurrentQueue()
    }

    private suspend fun restorePersistedQueue() {
        try {
            val restored = repo.getDownloadQueue().fold(
                onSuccess = { queue -> queue?.toDownloadTasks().orEmpty() },
                onFailure = { failure ->
                    _persistenceError.value = failure.message ?: "无法恢复下载队列"
                    emptyList()
                },
            )
            _tasks.value = recoverPersistedDownloadTasks(restored)
        } catch (cancelled: CancellationException) {
            restoreCompleted.completeExceptionally(cancelled)
            throw cancelled
        } catch (failure: Throwable) {
            _tasks.value = emptyList()
            _persistenceError.value = failure.message ?: "无法恢复下载队列"
        } finally {
            if (!restoreCompleted.isCompleted) restoreCompleted.complete(Unit)
        }

        if (_tasks.value.any { it.status == DownloadTaskStatus.Queued }) {
            ensureWorker()
        }
    }

    private suspend fun persistCurrentQueue(): Boolean {
        if (!persistentDownloadQueueSupported) return true
        return persistenceMutex.withLock {
            repo.saveDownloadQueue(_tasks.value.toPersistedDownloadQueue()).fold(
                onSuccess = {
                    _persistenceError.value = null
                    true
                },
                onFailure = { failure ->
                    _persistenceError.value = failure.message ?: "无法保存下载队列"
                    false
                },
            )
        }
    }

    private fun persistStructuralChange() {
        if (!persistentDownloadQueueSupported) return
        scope.launch {
            restoreCompleted.await()
            persistCurrentQueue()
        }
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
        val localAssets = supervisorScope {
            localFiles.keys.map { songId ->
                async {
                    songId to runCatching { localMediaAssets.inspect(songId) }
                        .getOrDefault(LocalMediaAssetSnapshot())
                }
            }.awaitAll().toMap()
        }
        _tasks.update { tasks ->
            reconcileDownloadTasksWithLocalLibrary(
                tasks = tasks,
                localFiles = localFiles,
                songDetails = localOnlyDetails,
                localAssets = localAssets,
            )
        }
        return null
    }

    private fun ensureWorker() {
        scope.launch {
            restoreCompleted.await()
            workerStartMutex.withLock {
                // A cancelled Job remains here until invokeOnCompletion runs after all cleanup.
                // Do not open a new worker slot merely because isActive already became false.
                if (!canStartDownloadWorker(workerJob)) return@withLock
                if (nextQueuedDownloadTask(_tasks.value, destructiveTaskIds.value) == null) {
                    return@withLock
                }
                if (!persistCurrentQueue()) {
                    failQueuedTasks("无法保存下载队列，下载未启动")
                    return@withLock
                }
                val serviceStartFailure = runCatching {
                    DownloadServiceController.ensureRunning()
                }.exceptionOrNull()
                if (serviceStartFailure != null) {
                    failQueuedTasks(
                        serviceStartFailure.message?.let { "无法启动后台下载服务：$it" }
                            ?: "无法启动后台下载服务",
                    )
                    return@withLock
                }
                val job = scope.launch(start = CoroutineStart.LAZY) {
                    while (true) {
                        val next = nextQueuedDownloadTask(_tasks.value, destructiveTaskIds.value)
                            ?: break
                        // A running worker can observe tasks appended while it was downloading.
                        // Persist the latest ordered queue before it claims each next item.
                        if (!persistCurrentQueue()) {
                            failQueuedTasks("无法保存下载队列，后续下载已停止")
                            break
                        }
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
                            nextQueuedDownloadTask(_tasks.value, destructiveTaskIds.value) != null
                        }
                        if (shouldRestart) ensureWorker()
                    }
                }
                job.start()
            }
        }
    }

    private fun failQueuedTasks(message: String) {
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.status == DownloadTaskStatus.Queued) {
                    task.copy(status = DownloadTaskStatus.Failed, errorMessage = message)
                } else {
                    task
                }
            }
        }
    }

    private fun cancelActiveTask(taskId: Long): Job? =
        activeDownloadJobForTask(activeExecution.value, taskId)?.also { it.cancel() }

    private fun cancelActiveExecutionForTasks(taskIds: Set<Long>): Job? =
        activeDownloadJobForTasks(activeExecution.value, taskIds)?.also { it.cancel() }

    private suspend fun runTask(taskId: Long) {
        val claimed = claimQueuedTask(taskId) ?: return
        val task = claimed.task
        val generation = claimed.generation
        try {
            // An interrupted task must keep the quality that produced its partial file.
            // Retry explicitly clears requestedQuality, allowing a later user choice to apply.
            val quality = task.requestedQuality
                ?: settings.getStringAsync(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)
            val requestedQuality = normalizeQualityLevel(quality)
                ?: SoundQuality.STANDARD.name.lowercase()
            if (
                !transitionTask(taskId, generation, DownloadTaskStatus.Resolving) {
                    it.copy(
                        requestedQuality = requestedQuality,
                        lyricStatus = DownloadLyricStatus.NotStarted,
                        artworkStatus = DownloadArtworkStatus.NotStarted,
                        errorMessage = null,
                    )
                }
            ) return
            if (!persistCurrentQueue()) {
                transitionTask(taskId, generation, DownloadTaskStatus.Resolving) {
                    it.copy(
                        status = DownloadTaskStatus.Failed,
                        errorMessage = "无法保存下载音质，下载未启动",
                    )
                }
                return
            }
            val urlData = repo.getSongUrls(listOf(taskId), requestedQuality)
                .firstOrNull { it.id == taskId }
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
                resumeKey = requestedQuality,
                representationKey = actualQuality ?: requestedQuality,
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
                        artworkStatus = DownloadArtworkStatus.NotStarted,
                        errorMessage = null,
                    )
                }
            ) return
            val downloadedFile = SongDownloader.download(
                item = request,
                onProgress = { downloaded, total ->
                    updateOwnedTask(taskId, generation) { current ->
                        if (current.status != DownloadTaskStatus.Downloading) current
                        else current.copy(
                            progressBytes = downloaded,
                            totalBytes = total ?: current.totalBytes,
                        )
                    }
                },
                onReadyToPublish = {
                    transitionTask(taskId, generation, DownloadTaskStatus.Downloading) {
                        it.copy(
                            status = DownloadTaskStatus.SavingLyrics,
                            actualQuality = actualQuality,
                            lyricStatus = DownloadLyricStatus.Saving,
                            artworkStatus = DownloadArtworkStatus.Saving,
                            errorMessage = null,
                        )
                    }
                },
            )
            if (
                !transitionTask(taskId, generation, DownloadTaskStatus.SavingLyrics) {
                    it.copy(
                        fileName = downloadedFile.fileName,
                        errorMessage = null,
                    )
                }
            ) return
            // The audio is already atomically published. Make it available to every local-first
            // player immediately; lyric/artwork requests are best-effort and may take longer.
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
            val assetResults = saveSelectedLocalAssets(
                task = task,
                saveLyrics = true,
                saveArtwork = true,
            )
            currentCoroutineContext().ensureActive()
            if (
                !transitionTask(taskId, generation, DownloadTaskStatus.SavingLyrics) {
                    it.copy(
                        status = DownloadTaskStatus.Completed,
                        requestedQuality = requestedQuality,
                        actualQuality = actualQuality,
                        progressBytes = it.totalBytes ?: it.progressBytes,
                        totalBytes = it.totalBytes ?: it.progressBytes.takeIf { bytes -> bytes > 0L },
                        fileName = downloadedFile.fileName,
                        errorMessage = null,
                    ).withLocalAssetResults(assetResults)
                }
            ) return
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

    private suspend fun saveSelectedLocalAssets(
        task: SongDownloadTask,
        saveLyrics: Boolean,
        saveArtwork: Boolean,
    ): LocalAssetSaveResults = assetSaveSemaphore.withPermit {
        supervisorScope {
            val lyric = if (saveLyrics) {
                async { saveLyricAsset(task) }
            } else {
                null
            }
            val artwork = if (saveArtwork) {
                async { saveArtworkAsset(task) }
            } else {
                null
            }
            LocalAssetSaveResults(
                lyric = lyric?.await(),
                artwork = artwork?.await(),
            )
        }
    }

    private suspend fun saveLyricAsset(task: SongDownloadTask): LocalLyricAssetResult {
        return try {
            val mode = LyricSourceMode.fromStoredWireValue(
                settings.getStringAsync(
                    SettingKeys.LYRIC_SOURCE_MODE,
                    LyricSourceMode.DEFAULT.wireValue,
                ),
            )
            when (
                val resolution = lyricResolver.resolveLatest(
                    query = LyricQuery(
                        songId = task.id,
                        title = task.title,
                        artist = task.artist,
                    ),
                    mode = mode,
                    // A save/refresh operation must resolve the currently selected upstream source
                    // instead of reading back the file it is meant to create.
                    preferLocal = false,
                )
            ) {
                is LyricResolution.Found -> {
                    val snapshot = localMediaAssets.saveLyrics(task.id, resolution.document)
                    LocalLyricAssetResult(
                        status = DownloadLyricStatus.Saved,
                        format = snapshot.lyricFormat,
                        fileName = snapshot.lyricFileName,
                    )
                }
                is LyricResolution.Untimed -> {
                    val snapshot = localMediaAssets.saveLyrics(task.id, resolution.document)
                    LocalLyricAssetResult(
                        status = DownloadLyricStatus.Saved,
                        format = snapshot.lyricFormat,
                        fileName = snapshot.lyricFileName,
                    )
                }
                LyricResolution.Empty -> LocalLyricAssetResult(DownloadLyricStatus.NoLyric)
                is LyricResolution.Error -> LocalLyricAssetResult(
                    status = DownloadLyricStatus.Failed,
                    errorMessage = resolution.message,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            LocalLyricAssetResult(
                status = DownloadLyricStatus.Failed,
                errorMessage = failure.message?.takeIf(String::isNotBlank) ?: "歌词保存失败",
            )
        }
    }

    private suspend fun saveArtworkAsset(task: SongDownloadTask): LocalArtworkAssetResult {
        if (task.artworkUri.isBlank()) {
            return LocalArtworkAssetResult(DownloadArtworkStatus.NoArtwork)
        }
        return try {
            val fileName = localMediaAssets.saveArtwork(task.id, task.artworkUri)
                ?: return LocalArtworkAssetResult(DownloadArtworkStatus.NoArtwork)
            LocalArtworkAssetResult(
                status = DownloadArtworkStatus.Saved,
                fileName = fileName,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            LocalArtworkAssetResult(
                status = DownloadArtworkStatus.Failed,
                errorMessage = failure.message?.takeIf(String::isNotBlank) ?: "封面保存失败",
            )
        }
    }

    private fun updateTask(taskId: Long, transform: (SongDownloadTask) -> SongDownloadTask) {
        _tasks.update { tasks -> tasks.map { task -> if (task.id == taskId) transform(task) else task } }
    }

    private fun discardPartialOrRecordFailure(taskId: Long): Boolean {
        val failure = runCatching { SongDownloader.discardPartial(taskId) }.exceptionOrNull()
            ?: return true
        updateTask(taskId) { task ->
            task.copy(
                errorMessage = failure.message
                    ?.takeIf(String::isNotBlank)
                    ?.let { "无法清理断点文件：$it" }
                    ?: "无法清理断点文件",
            )
        }
        return false
    }

    private fun beginDestructiveOperation(requestedIds: Set<Long>): Set<Long> {
        while (true) {
            val current = destructiveTaskIds.value
            val acquired = requestedIds - current
            if (acquired.isEmpty()) return emptySet()
            if (destructiveTaskIds.compareAndSet(current, current + acquired)) return acquired
        }
    }

    private fun finishDestructiveOperation(acquiredIds: Set<Long>) {
        destructiveTaskIds.update { current -> current - acquiredIds }
        if (nextQueuedDownloadTask(_tasks.value, destructiveTaskIds.value) != null) {
            ensureWorker()
        }
    }

    private fun invalidateTasksForControl(
        targetStatus: DownloadTaskStatus,
        canInvalidate: (SongDownloadTask) -> Boolean,
    ): Set<Long> {
        val generation = nextExecutionGeneration()
        while (true) {
            val current = _tasks.value
            val affectedIds = current
                .filter(canInvalidate)
                .mapTo(mutableSetOf()) { it.id }
            if (affectedIds.isEmpty()) return emptySet()
            val updated = current.map { task ->
                if (task.id in affectedIds) {
                    task.copy(
                        status = targetStatus,
                        executionGeneration = generation,
                    )
                } else {
                    task
                }
            }
            if (_tasks.compareAndSet(current, updated)) return affectedIds
        }
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
            if (!canReactivateDownloadTask(taskId, destructiveTaskIds.value)) return null
            if (task.status != DownloadTaskStatus.Queued) return null
            val updated = current.toMutableList().apply {
                this[index] = task.copy(
                    status = DownloadTaskStatus.Resolving,
                    lyricStatus = DownloadLyricStatus.NotStarted,
                    artworkStatus = DownloadArtworkStatus.NotStarted,
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
        localAssets: Map<Long, LocalMediaAssetSnapshot>,
    ): SongDownloadTask {
        val localFile = localFiles[id]
        val localAsset = localAssets[id]
        val downloaded = localFile != null
        val localSize = localFile?.sizeBytes
        return SongDownloadTask(
            id = id,
            title = downloadDisplayTitle(),
            artist = downloadDisplayArtist(),
            artworkUri = al.picUrl,
            playlistId = playlistId,
            status = if (downloaded) DownloadTaskStatus.Completed else DownloadTaskStatus.Queued,
            lyricStatus = if (localAsset?.lyricFormat != null) {
                DownloadLyricStatus.Saved
            } else {
                DownloadLyricStatus.NotStarted
            },
            artworkStatus = if (localAsset?.artworkFileName != null) {
                DownloadArtworkStatus.Saved
            } else {
                DownloadArtworkStatus.NotStarted
            },
            lyricFormat = localAsset?.lyricFormat,
            lyricFileName = localAsset?.lyricFileName,
            artworkFileName = localAsset?.artworkFileName,
            progressBytes = if (downloaded) localSize ?: 1 else 0,
            totalBytes = if (downloaded) localSize ?: 1 else null,
            fileName = localFile?.fileName,
        )
    }

}

private data class LocalLyricAssetResult(
    val status: DownloadLyricStatus,
    val format: LocalLyricFormat? = null,
    val fileName: String? = null,
    val errorMessage: String? = null,
)

private data class LocalArtworkAssetResult(
    val status: DownloadArtworkStatus,
    val fileName: String? = null,
    val errorMessage: String? = null,
)

private data class LocalAssetSaveResults(
    val lyric: LocalLyricAssetResult? = null,
    val artwork: LocalArtworkAssetResult? = null,
) {
    val errorMessage: String?
        get() = listOfNotNull(
            lyric?.errorMessage?.takeIf(String::isNotBlank),
            artwork?.errorMessage?.takeIf(String::isNotBlank),
        ).joinToString("；").ifBlank { null }
}

private fun SongDownloadTask.withLocalAssetResults(
    results: LocalAssetSaveResults,
): SongDownloadTask = copy(
    lyricStatus = results.lyric?.status ?: lyricStatus,
    artworkStatus = results.artwork?.status ?: artworkStatus,
    lyricFormat = results.lyric?.format ?: lyricFormat,
    lyricFileName = results.lyric?.fileName ?: lyricFileName,
    artworkFileName = results.artwork?.fileName ?: artworkFileName,
    errorMessage = results.errorMessage,
)

internal fun canStartDownloadWorker(existingWorker: Job?): Boolean = existingWorker == null

internal fun canPauseDownloadTask(task: SongDownloadTask): Boolean =
    task.status == DownloadTaskStatus.Queued ||
        task.status == DownloadTaskStatus.Resolving ||
        task.status == DownloadTaskStatus.Downloading

internal fun canCancelDownloadTask(task: SongDownloadTask): Boolean =
    canPauseDownloadTask(task) || task.status == DownloadTaskStatus.Paused

internal fun canReactivateDownloadTask(
    taskId: Long,
    destructiveTaskIds: Set<Long>,
): Boolean = taskId !in destructiveTaskIds

internal fun nextQueuedDownloadTask(
    tasks: List<SongDownloadTask>,
    destructiveTaskIds: Set<Long>,
): SongDownloadTask? = tasks.firstOrNull { task ->
    task.status == DownloadTaskStatus.Queued &&
        canReactivateDownloadTask(task.id, destructiveTaskIds)
}

internal fun recoverPersistedDownloadTasks(
    tasks: List<SongDownloadTask>,
): List<SongDownloadTask> =
    tasks.map { task ->
        if (task.isActive) {
            task.copy(
                status = DownloadTaskStatus.Queued,
                lyricStatus = DownloadLyricStatus.NotStarted,
                artworkStatus = DownloadArtworkStatus.NotStarted,
                errorMessage = null,
                executionGeneration = 0L,
            )
        } else {
            task.copy(executionGeneration = 0L)
        }
    }

internal data class ActiveDownloadExecution(
    val taskId: Long,
    val job: Job,
)

internal fun activeDownloadJobForTask(
    execution: ActiveDownloadExecution?,
    taskId: Long,
): Job? = execution?.takeIf { it.taskId == taskId }?.job

internal fun activeDownloadJobForTasks(
    execution: ActiveDownloadExecution?,
    taskIds: Set<Long>,
): Job? = execution?.takeIf { it.taskId in taskIds }?.job

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
    localAssets: Map<Long, LocalMediaAssetSnapshot> = emptyMap(),
): List<SongDownloadTask> {
    val merged = linkedMapOf<Long, SongDownloadTask>()
    tasks.forEach { task ->
        merged[task.id] = syncDownloadTaskWithLocalLibrary(
            task = task,
            snapshot = localFiles[task.id],
            songDetail = songDetails[task.id],
            assetSnapshot = localAssets[task.id],
        )
    }
    localFiles.values
        .filter { snapshot -> snapshot.id !in merged }
        .sortedWith(
            compareByDescending<DownloadedSongSnapshot> { it.lastModifiedEpochMillis ?: 0L }
                .thenBy { it.id }
        )
        .forEach { snapshot ->
            merged[snapshot.id] = importedDownloadTask(
                snapshot = snapshot,
                songDetail = songDetails[snapshot.id],
                assetSnapshot = localAssets[snapshot.id],
            )
        }
    return merged.values.toList()
}

internal fun mergeDownloadTasksForEnqueue(
    current: List<SongDownloadTask>,
    incoming: List<SongDownloadTask>,
    localFiles: Map<Long, DownloadedSongSnapshot>,
    songDetails: Map<Long, SongDetailSongs> = emptyMap(),
    localAssets: Map<Long, LocalMediaAssetSnapshot> = emptyMap(),
): List<SongDownloadTask> {
    val syncedCurrent = current.map { task ->
        syncDownloadTaskWithLocalLibrary(
            task = task,
            snapshot = localFiles[task.id],
            songDetail = songDetails[task.id],
            assetSnapshot = localAssets[task.id],
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
                    lyricStatus = task.lyricStatus,
                    artworkStatus = task.artworkStatus,
                    lyricFormat = task.lyricFormat,
                    lyricFileName = task.lyricFileName,
                    artworkFileName = task.artworkFileName,
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
    assetSnapshot: LocalMediaAssetSnapshot? = null,
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
                lyricStatus = task.syncedLyricStatus(assetSnapshot),
                artworkStatus = task.syncedArtworkStatus(assetSnapshot),
                lyricFormat = if (assetSnapshot == null) task.lyricFormat else assetSnapshot.lyricFormat,
                lyricFileName = if (assetSnapshot == null) {
                    task.lyricFileName
                } else {
                    assetSnapshot.lyricFileName
                },
                artworkFileName = if (assetSnapshot == null) {
                    task.artworkFileName
                } else {
                    assetSnapshot.artworkFileName
                },
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
    artworkStatus = DownloadArtworkStatus.NotStarted,
    lyricFormat = null,
    lyricFileName = null,
    artworkFileName = null,
    progressBytes = 0,
    totalBytes = null,
    fileName = null,
    errorMessage = message,
)

private fun SongDownloadTask.syncedLyricStatus(
    assetSnapshot: LocalMediaAssetSnapshot?,
): DownloadLyricStatus = when {
    assetSnapshot == null -> lyricStatus
    assetSnapshot.lyricFormat != null -> DownloadLyricStatus.Saved
    lyricStatus == DownloadLyricStatus.Saved || lyricStatus == DownloadLyricStatus.Saving ->
        DownloadLyricStatus.NotStarted
    else -> lyricStatus
}

private fun SongDownloadTask.syncedArtworkStatus(
    assetSnapshot: LocalMediaAssetSnapshot?,
): DownloadArtworkStatus = when {
    assetSnapshot == null -> artworkStatus
    assetSnapshot.artworkFileName != null -> DownloadArtworkStatus.Saved
    artworkStatus == DownloadArtworkStatus.Saved || artworkStatus == DownloadArtworkStatus.Saving ->
        DownloadArtworkStatus.NotStarted
    else -> artworkStatus
}

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
        artworkStatus = DownloadArtworkStatus.NotStarted,
        lyricFormat = null,
        lyricFileName = null,
        artworkFileName = null,
        progressBytes = 0,
        totalBytes = null,
        fileName = null,
        errorMessage = null,
    )

private fun importedDownloadTask(
    snapshot: DownloadedSongSnapshot,
    songDetail: SongDetailSongs?,
    assetSnapshot: LocalMediaAssetSnapshot? = null,
): SongDownloadTask {
    val size = snapshot.sizeBytes
    return SongDownloadTask(
        id = snapshot.id,
        title = songDetail?.downloadDisplayTitle() ?: "本地歌曲 ${snapshot.id}",
        artist = songDetail?.downloadDisplayArtist() ?: "本地文件",
        artworkUri = songDetail?.al?.picUrl.orEmpty(),
        status = DownloadTaskStatus.Completed,
        lyricStatus = if (assetSnapshot?.lyricFormat != null) {
            DownloadLyricStatus.Saved
        } else {
            DownloadLyricStatus.NotStarted
        },
        artworkStatus = if (assetSnapshot?.artworkFileName != null) {
            DownloadArtworkStatus.Saved
        } else {
            DownloadArtworkStatus.NotStarted
        },
        lyricFormat = assetSnapshot?.lyricFormat,
        lyricFileName = assetSnapshot?.lyricFileName,
        artworkFileName = assetSnapshot?.artworkFileName,
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

private const val PERSISTENCE_INTERVAL_MS = 500L
private const val MAX_CONCURRENT_ASSET_SAVES = 2
