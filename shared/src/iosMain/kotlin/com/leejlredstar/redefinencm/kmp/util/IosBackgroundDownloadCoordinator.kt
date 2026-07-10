@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSLock
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import platform.posix.remove
import platform.posix.rename

internal const val IOS_BACKGROUND_DOWNLOAD_SESSION_ID =
    "com.leejlredstar.redefinencm.kmp.background-downloads"

private data class PendingIosDownload(
    val fileName: String,
    val onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    val continuation: CancellableContinuation<DownloadedSongFile>,
)

private sealed interface IosDownloadOutcome {
    data class Success(val file: DownloadedSongFile) : IosDownloadOutcome
    data class Failure(val error: Throwable) : IosDownloadOutcome
}

internal data class IosDownloadLifecycleState(
    val cancelled: Boolean = false,
    val filePersisted: Boolean = false,
)

internal data class IosDownloadLifecycleDecision(
    val state: IosDownloadLifecycleState,
    val shouldPersistTemporaryFile: Boolean = false,
    val shouldDeletePersistedFile: Boolean = false,
)

internal fun iosDownloadDidFinishDecision(
    state: IosDownloadLifecycleState,
): IosDownloadLifecycleDecision = IosDownloadLifecycleDecision(
    state = state,
    shouldPersistTemporaryFile = !state.cancelled,
)

internal fun iosDownloadFilePersisted(
    state: IosDownloadLifecycleState,
): IosDownloadLifecycleState = state.copy(filePersisted = true)

internal fun iosDownloadCancelledDecision(
    state: IosDownloadLifecycleState,
): IosDownloadLifecycleDecision = IosDownloadLifecycleDecision(
    state = state.copy(cancelled = true, filePersisted = false),
    shouldDeletePersistedFile = state.filePersisted,
)

/**
 * One stable background NSURLSession for all iOS song downloads.
 *
 * `taskDescription` contains the validated destination file name. This is intentionally enough to
 * finish moving a system-owned temporary file after iOS relaunches the app and reconnects the
 * background session, even though the original coroutine no longer exists in that process.
 */
internal object IosBackgroundDownloadCoordinator : NSObject(), NSURLSessionDownloadDelegateProtocol {
    private val stateLock = NSLock()
    private val pending = mutableMapOf<ULong, PendingIosDownload>()
    private val outcomes = mutableMapOf<ULong, IosDownloadOutcome>()
    private val lifecycles = mutableMapOf<ULong, IosDownloadLifecycleState>()
    private var backgroundEventsCompletion: (() -> Unit)? = null

    private val delegateQueue = NSOperationQueue().apply {
        maxConcurrentOperationCount = 1
    }

    private val session: NSURLSession by lazy {
        val configuration = NSURLSessionConfiguration
            .backgroundSessionConfigurationWithIdentifier(IOS_BACKGROUND_DOWNLOAD_SESSION_ID)
            .apply {
                sessionSendsLaunchEvents = true
                discretionary = false
                allowsCellularAccess = true
            }
        NSURLSession.sessionWithConfiguration(
            configuration = configuration,
            delegate = this,
            delegateQueue = delegateQueue,
        )
    }

    fun ensureStarted() {
        session
    }

    fun handleBackgroundEvents(identifier: String, completionHandler: () -> Unit): Boolean {
        if (identifier != IOS_BACKGROUND_DOWNLOAD_SESSION_ID) return false
        val replaced = withStateLock {
            val previous = backgroundEventsCompletion
            backgroundEventsCompletion = completionHandler
            previous
        }
        // There must be exactly one outstanding UIKit completion handler. Complete a replaced
        // handler instead of silently leaking the app's background execution assertion.
        replaced?.invoke()
        // Store UIKit's completion before reconnecting the lazily-created session: on process
        // relaunch the system may deliver "did finish events" immediately during reconnection.
        ensureStarted()
        return true
    }

    suspend fun download(
        url: NSURL,
        fileName: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): DownloadedSongFile {
        require(isValidDownloadFileName(fileName)) { "无效的下载文件名" }
        return suspendCancellableCoroutine { continuation ->
            val task = session.downloadTaskWithURL(url)
            task.taskDescription = fileName
            val taskIdentifier = task.taskIdentifier
            withStateLock {
                check(pending.put(
                    taskIdentifier,
                    PendingIosDownload(fileName, onProgress, continuation),
                ) == null) { "Duplicate iOS background download task identifier" }
                lifecycles[taskIdentifier] = IosDownloadLifecycleState()
            }
            continuation.invokeOnCancellation {
                val activeCancellation = withStateLock {
                    val hadPending = pending.remove(taskIdentifier) != null
                    val hadOutcome = outcomes.remove(taskIdentifier) != null
                    val wasActive = hadPending || hadOutcome
                    if (wasActive) {
                        val current = lifecycles[taskIdentifier] ?: IosDownloadLifecycleState()
                        lifecycles[taskIdentifier] = iosDownloadCancelledDecision(current).state
                    }
                    wasActive
                }
                task.cancel()
                // Always remove both final and .part files. This also covers the narrow prompt-
                // cancellation race after didComplete has removed its lifecycle record but before
                // the resumed coroutine actually receives the successful result.
                deleteIosDownloadArtifacts(fileName)
                if (!activeCancellation) {
                    withStateLock { lifecycles.remove(taskIdentifier) }
                }
            }
            task.resume()
        }
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        val callback = withStateLock { pending[downloadTask.taskIdentifier]?.onProgress }
        callback?.invoke(
            totalBytesWritten,
            totalBytesExpectedToWrite.takeIf { it > 0L },
        )
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        val taskIdentifier = downloadTask.taskIdentifier
        withStateLock {
            val current = lifecycles[taskIdentifier] ?: IosDownloadLifecycleState()
            val decision = iosDownloadDidFinishDecision(current)
            if (!decision.shouldPersistTemporaryFile) return@withStateLock

            val outcome = persistDownloadedTemporaryFile(downloadTask, didFinishDownloadingToURL)
            outcomes[taskIdentifier] = outcome
            if (outcome is IosDownloadOutcome.Success) {
                lifecycles[taskIdentifier] = iosDownloadFilePersisted(current)
            }
        }
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        val taskIdentifier = task.taskIdentifier
        val (download, outcome, lifecycle) = withStateLock {
            Triple(
                pending.remove(taskIdentifier),
                outcomes.remove(taskIdentifier),
                lifecycles.remove(taskIdentifier),
            )
        }
        if (lifecycle?.cancelled == true) {
            task.taskDescription?.let(::deleteIosDownloadArtifacts)
            return
        }
        download ?: return

        val finalOutcome = when {
            didCompleteWithError != null -> IosDownloadOutcome.Failure(
                IllegalStateException(didCompleteWithError.localizedDescription),
            )
            outcome != null -> outcome
            else -> IosDownloadOutcome.Failure(
                IllegalStateException("iOS background download completed without a file"),
            )
        }
        when (finalOutcome) {
            is IosDownloadOutcome.Success -> {
                val received = task.countOfBytesReceived.coerceAtLeast(0L)
                val expected = task.countOfBytesExpectedToReceive.takeIf { it > 0L }
                download.onProgress(received, expected ?: received.takeIf { it > 0L })
                download.continuation.resumeWith(Result.success(finalOutcome.file))
            }
            is IosDownloadOutcome.Failure -> {
                download.continuation.resumeWith(Result.failure(finalOutcome.error))
            }
        }
    }

    override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
        val completion = withStateLock {
            backgroundEventsCompletion.also { backgroundEventsCompletion = null }
        }
        if (completion != null) {
            NSOperationQueue.mainQueue.addOperationWithBlock(completion)
        }
    }

    private fun persistDownloadedTemporaryFile(
        task: NSURLSessionDownloadTask,
        temporaryUrl: NSURL,
    ): IosDownloadOutcome {
        val statusCode = (task.response as? NSHTTPURLResponse)?.statusCode
        if (statusCode != null && statusCode !in 200L..299L) {
            return IosDownloadOutcome.Failure(
                IllegalStateException("下载失败：HTTP $statusCode"),
            )
        }
        val fileName = task.taskDescription
        if (fileName == null || !isValidDownloadFileName(fileName)) {
            return IosDownloadOutcome.Failure(IllegalStateException("后台下载缺少有效文件名"))
        }

        return runCatching {
            val directory = ensureIosDownloadDirectory()
            val targetPath = "$directory/$fileName"
            val partPath = "$targetPath.part"
            remove(partPath)
            val moved = NSFileManager.defaultManager.moveItemAtURL(
                srcURL = temporaryUrl,
                toURL = NSURL.fileURLWithPath(partPath),
                error = null,
            )
            if (!moved) error("无法移动 iOS 后台下载临时文件")
            if (rename(partPath, targetPath) != 0) {
                remove(partPath)
                error("无法原子保存 iOS 后台下载文件")
            }
            DownloadedSongFile(
                fileName = fileName,
                uri = NSURL.fileURLWithPath(targetPath).absoluteString,
            )
        }.fold(
            onSuccess = IosDownloadOutcome::Success,
            onFailure = IosDownloadOutcome::Failure,
        )
    }

    private inline fun <T> withStateLock(block: () -> T): T {
        stateLock.lock()
        return try {
            block()
        } finally {
            stateLock.unlock()
        }
    }
}

private fun deleteIosDownloadArtifacts(fileName: String) {
    if (!isValidDownloadFileName(fileName)) return
    runCatching {
        val targetPath = "${ensureIosDownloadDirectory()}/$fileName"
        remove("$targetPath.part")
        remove(targetPath)
    }
}

private fun isValidDownloadFileName(fileName: String): Boolean =
    IOS_DOWNLOAD_FILE_NAME.matches(fileName)

private val IOS_DOWNLOAD_FILE_NAME = Regex("^[0-9]+[.][A-Za-z0-9]{1,12}$")
