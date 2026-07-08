package com.leejlredstar.redefinencm.kmp.download

import com.leejlredstar.redefinencm.kmp.data.LyricCacheStatus
import com.leejlredstar.redefinencm.kmp.data.Repository
import com.leejlredstar.redefinencm.kmp.data.api.dto.SongDetailSongs
import com.leejlredstar.redefinencm.kmp.util.DownloadRequestItem
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongSnapshot
import com.leejlredstar.redefinencm.kmp.util.DownloadedSongsCache
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.SongDownloader
import com.leejlredstar.redefinencm.kmp.util.SoundQuality
import com.leejlredstar.redefinencm.kmp.util.deleteDownloadedSongFile
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.launch

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

class SongDownloadManager(
    private val repo: Repository,
    private val settings: PlatformSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _tasks = MutableStateFlow<List<SongDownloadTask>>(emptyList())

    val tasks: StateFlow<List<SongDownloadTask>> = _tasks.asStateFlow()
    val summary: StateFlow<DownloadQueueSummary> = _tasks
        .map { tasks ->
            DownloadQueueSummary(
                total = tasks.size,
                active = tasks.count { it.isActive },
                completed = tasks.count { it.status == DownloadTaskStatus.Completed },
                failed = tasks.count {
                    it.status == DownloadTaskStatus.Failed || it.status == DownloadTaskStatus.Cancelled
                },
                deleted = tasks.count { it.status == DownloadTaskStatus.Deleted },
                paused = tasks.count { it.status == DownloadTaskStatus.Paused },
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, DownloadQueueSummary())

    private var workerJob: Job? = null
    private var syncJob: Job? = null
    private var activeTaskId: Long? = null

    fun enqueuePlaylist(playlistId: Long) {
        scope.launch {
            val songs = repo.getPlaylistTrackAllOnce(playlistId)?.songs.orEmpty()
            enqueueResolvedSongs(songs, playlistId)
        }
    }

    fun enqueueSongs(songs: List<SongDetailSongs>, playlistId: Long? = null) {
        if (songs.isEmpty()) return
        scope.launch { enqueueResolvedSongs(songs, playlistId) }
    }

    private suspend fun enqueueResolvedSongs(songs: List<SongDetailSongs>, playlistId: Long? = null) {
        val localFiles = DownloadedSongsCache.snapshot()
        val newTasks = songs.map { it.toDownloadTask(playlistId, localFiles) }
        _tasks.update { current ->
            val songDetails = songs.associateBy { it.id }
            val syncedCurrent = current.map { it.syncWithLocalLibrary(localFiles[it.id], songDetails[it.id]) }
            val merged = syncedCurrent.associateBy { it.id }.toMutableMap()
            val order = current.map { it.id }.toMutableList()
            newTasks.forEach { incoming ->
                val existing = merged[incoming.id]
                if (existing == null) {
                    merged[incoming.id] = incoming
                    order.add(incoming.id)
                } else if (incoming.status == DownloadTaskStatus.Completed) {
                    merged[incoming.id] = existing.copy(
                        title = incoming.title,
                        artist = incoming.artist,
                        artworkUri = incoming.artworkUri,
                        playlistId = incoming.playlistId ?: existing.playlistId,
                        status = DownloadTaskStatus.Completed,
                        progressBytes = incoming.progressBytes,
                        totalBytes = incoming.totalBytes,
                        fileName = incoming.fileName,
                        errorMessage = null,
                    )
                } else if (existing.canRequeue()) {
                    merged[incoming.id] = existing.requeueFrom(incoming)
                }
            }
            order.mapNotNull { merged[it] }
        }
        ensureWorker()
    }

    fun pause(taskId: Long) {
        val shouldCancel = activeTaskId == taskId
        updateTask(taskId) { task ->
            if (task.isActive) task.copy(status = DownloadTaskStatus.Paused) else task
        }
        if (shouldCancel) workerJob?.cancel()
    }

    fun resume(taskId: Long) {
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
        ensureWorker()
    }

    fun cancel(taskId: Long) {
        val shouldCancel = activeTaskId == taskId
        updateTask(taskId) { task ->
            if (task.isTerminal) task else task.copy(status = DownloadTaskStatus.Cancelled)
        }
        if (shouldCancel) workerJob?.cancel()
    }

    fun remove(taskId: Long) {
        if (activeTaskId == taskId) workerJob?.cancel()
        _tasks.update { tasks -> tasks.filterNot { it.id == taskId } }
    }

    fun deleteDownloadedSong(taskId: Long) {
        val shouldCancel = activeTaskId == taskId
        if (shouldCancel) {
            updateTask(taskId) { task ->
                if (task.isActive) task.copy(status = DownloadTaskStatus.Cancelled) else task
            }
            workerJob?.cancel()
        }
        scope.launch {
            val deleted = deleteDownloadedSongFile(taskId)
            if (deleted) {
                DownloadedSongsCache.remove(taskId)
            }
            val localFiles = if (deleted) {
                DownloadedSongsCache.snapshot()
            } else {
                DownloadedSongsCache.refreshSnapshots()
            }
            _tasks.update { tasks ->
                tasks.map { task ->
                    when {
                        task.id != taskId -> task.syncWithLocalLibrary(localFiles[task.id])
                        task.id !in localFiles -> task.markDeleted(
                            message = if (deleted) "已删除本地歌曲" else "本地文件已删除",
                        )
                        else -> task.copy(
                            status = DownloadTaskStatus.Failed,
                            errorMessage = "删除本地歌曲失败",
                        )
                    }
                }
            }
        }
    }

    fun pauseAll() {
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.isActive) task.copy(status = DownloadTaskStatus.Paused) else task
            }
        }
        workerJob?.cancel()
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
        ensureWorker()
    }

    fun cancelAll() {
        _tasks.update { tasks ->
            tasks.map { task ->
                if (task.isTerminal) task else task.copy(status = DownloadTaskStatus.Cancelled)
            }
        }
        workerJob?.cancel()
    }

    fun clearFinished() {
        _tasks.update { tasks -> tasks.filterNot { it.isTerminal } }
    }

    fun clearCompleted() {
        _tasks.update { tasks -> tasks.filterNot { it.status == DownloadTaskStatus.Completed } }
    }

    fun syncWithLocalLibrary() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            reconcileWithLocalLibrary()
        }
    }

    fun onCleared() {
        scope.cancel()
    }

    private suspend fun reconcileWithLocalLibrary() {
        val localFiles = DownloadedSongsCache.refreshSnapshots()
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
    }

    private fun ensureWorker() {
        if (workerJob?.isActive == true) return
        val job = scope.launch {
            while (true) {
                val next = _tasks.value.firstOrNull { it.status == DownloadTaskStatus.Queued } ?: break
                activeTaskId = next.id
                try {
                    runTask(next.id)
                } finally {
                    activeTaskId = null
                }
            }
        }
        workerJob = job
        job.invokeOnCompletion {
            if (workerJob == job) workerJob = null
            activeTaskId = null
            if (_tasks.value.any { task -> task.status == DownloadTaskStatus.Queued }) {
                ensureWorker()
            }
        }
    }

    private suspend fun runTask(taskId: Long) {
        val task = _tasks.value.firstOrNull { it.id == taskId } ?: return
        try {
            val quality = settings.getStringAsync(SettingKeys.DOWNLOAD_QUALITY, SoundQuality.STANDARD.name)
            val requestedQuality = normalizeQualityLevel(quality)
            updateTask(taskId) {
                it.copy(
                    status = DownloadTaskStatus.Resolving,
                    requestedQuality = requestedQuality,
                    lyricStatus = DownloadLyricStatus.NotStarted,
                    errorMessage = null,
                )
            }
            val urlData = repo.getSongUrls(listOf(taskId), quality).firstOrNull { it.id == taskId }
            currentCoroutineContext().ensureActive()
            val url = urlData?.url.orEmpty()
            if (url.isBlank()) {
                updateTask(taskId) {
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
            updateTask(taskId) {
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
            val downloadedFile = SongDownloader.download(request) { downloaded, total ->
                updateTask(taskId) { current ->
                    if (current.status != DownloadTaskStatus.Downloading) current
                    else current.copy(
                        progressBytes = downloaded,
                        totalBytes = total ?: current.totalBytes,
                    )
                }
            }
            currentCoroutineContext().ensureActive()
            updateTask(taskId) {
                it.copy(
                    status = DownloadTaskStatus.SavingLyrics,
                    actualQuality = actualQuality,
                    lyricStatus = DownloadLyricStatus.Saving,
                    fileName = downloadedFile.fileName,
                    errorMessage = null,
                )
            }
            val lyricStatus = when (repo.cacheLyric(taskId)) {
                LyricCacheStatus.Saved -> DownloadLyricStatus.Saved
                LyricCacheStatus.NoLyric -> DownloadLyricStatus.NoLyric
                LyricCacheStatus.Failed -> DownloadLyricStatus.Failed
            }
            currentCoroutineContext().ensureActive()
            updateTask(taskId) {
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
                updateTask(taskId) { it.copy(status = DownloadTaskStatus.Paused) }
            }
            throw e
        } catch (t: Throwable) {
            updateTask(taskId) {
                it.copy(
                    status = DownloadTaskStatus.Failed,
                    errorMessage = t.message?.takeIf(String::isNotBlank) ?: "下载失败",
                )
            }
        }
    }

    private fun updateTask(taskId: Long, transform: (SongDownloadTask) -> SongDownloadTask) {
        _tasks.update { tasks -> tasks.map { task -> if (task.id == taskId) transform(task) else task } }
    }

    private fun normalizeQualityLevel(level: String?): String? =
        level?.trim()
            ?.takeIf { it.isNotEmpty() && it.lowercase() != "null" }
            ?.lowercase()

    private fun SongDetailSongs.toDownloadTask(
        playlistId: Long?,
        localFiles: Map<Long, DownloadedSongSnapshot>,
    ): SongDownloadTask {
        val localFile = localFiles[id]
        val downloaded = localFile != null
        val localSize = localFile?.sizeBytes
        return SongDownloadTask(
            id = id,
            title = displayTitle(),
            artist = displayArtist(),
            artworkUri = al.picUrl,
            playlistId = playlistId,
            status = if (downloaded) DownloadTaskStatus.Completed else DownloadTaskStatus.Queued,
            lyricStatus = DownloadLyricStatus.NotStarted,
            progressBytes = if (downloaded) localSize ?: 1 else 0,
            totalBytes = if (downloaded) localSize ?: 1 else null,
            fileName = localFile?.fileName,
        )
    }

    private fun SongDownloadTask.canRequeue(): Boolean =
        status == DownloadTaskStatus.Failed ||
            status == DownloadTaskStatus.Cancelled ||
            status == DownloadTaskStatus.Paused ||
            status == DownloadTaskStatus.Completed ||
            status == DownloadTaskStatus.Deleted

    private fun SongDownloadTask.requeueFrom(incoming: SongDownloadTask): SongDownloadTask =
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

    private fun List<SongDownloadTask>.reconcileWithLocalLibrary(
        localFiles: Map<Long, DownloadedSongSnapshot>,
        songDetails: Map<Long, SongDetailSongs>,
    ): List<SongDownloadTask> {
        val merged = linkedMapOf<Long, SongDownloadTask>()
        forEach { task ->
            merged[task.id] = task.syncWithLocalLibrary(
                snapshot = localFiles[task.id],
                songDetail = songDetails[task.id],
            )
        }
        localFiles.values
            .filter { snapshot -> snapshot.id !in merged }
            .sortedWith(localFileOrder())
            .forEach { snapshot ->
                merged[snapshot.id] = snapshot.toImportedTask(songDetails[snapshot.id])
            }
        return merged.values.toList()
    }

    private fun DownloadedSongSnapshot.toImportedTask(songDetail: SongDetailSongs?): SongDownloadTask {
        val size = sizeBytes
        return SongDownloadTask(
            id = id,
            title = songDetail?.displayTitle() ?: "本地歌曲 $id",
            artist = songDetail?.displayArtist() ?: "本地文件",
            artworkUri = songDetail?.al?.picUrl.orEmpty(),
            status = DownloadTaskStatus.Completed,
            lyricStatus = DownloadLyricStatus.NotStarted,
            progressBytes = size ?: 1,
            totalBytes = size ?: 1,
            fileName = fileName,
            errorMessage = null,
        )
    }

    private fun SongDownloadTask.syncWithLocalLibrary(
        snapshot: DownloadedSongSnapshot?,
        songDetail: SongDetailSongs? = null,
    ): SongDownloadTask {
        val existsOnDisk = snapshot != null
        return when {
            status == DownloadTaskStatus.Completed && !existsOnDisk -> markDeleted("本地文件已删除")
            !isActive && existsOnDisk -> completeFromLocalFile(snapshot, songDetail)
            else -> this
        }
    }

    private fun SongDownloadTask.completeFromLocalFile(
        snapshot: DownloadedSongSnapshot?,
        songDetail: SongDetailSongs?,
    ): SongDownloadTask {
        val localSize = snapshot?.sizeBytes
        return copy(
            title = songDetail?.displayTitle() ?: title,
            artist = songDetail?.displayArtist() ?: artist,
            artworkUri = songDetail?.al?.picUrl?.takeIf(String::isNotBlank) ?: artworkUri,
            status = DownloadTaskStatus.Completed,
            progressBytes = localSize ?: progressBytes.takeIf { it > 0L } ?: 1,
            totalBytes = localSize ?: totalBytes ?: 1,
            fileName = snapshot?.fileName ?: fileName,
            errorMessage = null,
        )
    }

    private fun SongDownloadTask.markDeleted(message: String): SongDownloadTask = copy(
        status = DownloadTaskStatus.Deleted,
        requestedQuality = null,
        actualQuality = null,
        lyricStatus = DownloadLyricStatus.NotStarted,
        progressBytes = 0,
        totalBytes = null,
        fileName = null,
        errorMessage = message,
    )

    private fun SongDetailSongs.displayTitle(): String =
        name.ifBlank { "本地歌曲 $id" }

    private fun SongDetailSongs.displayArtist(): String =
        ar.joinToString(" / ") { it.name }.ifBlank { "未知艺术家" }

    private fun localFileOrder(): Comparator<DownloadedSongSnapshot> =
        compareByDescending<DownloadedSongSnapshot> { it.lastModifiedEpochMillis ?: 0L }
            .thenBy { it.id }
}

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

private fun syncDownloadTaskWithLocalLibrary(
    task: SongDownloadTask,
    snapshot: DownloadedSongSnapshot?,
    songDetail: SongDetailSongs?,
): SongDownloadTask {
    val existsOnDisk = snapshot != null
    return when {
        task.status == DownloadTaskStatus.Completed && !existsOnDisk -> task.copy(
            status = DownloadTaskStatus.Deleted,
            requestedQuality = null,
            actualQuality = null,
            lyricStatus = DownloadLyricStatus.NotStarted,
            progressBytes = 0,
            totalBytes = null,
            fileName = null,
            errorMessage = "本地文件已删除",
        )
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
