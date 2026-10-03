@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.leejlredstar.redefinencm.kmp.util

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
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

internal const val IOS_BACKGROUND_DOWNLOAD_SESSION_ID =
    "com.leejlredstar.redefinencm.kmp.background-downloads"

private data class PendingIosDownload(
    val fileName: String,
    val onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    val onReadyToPublish: () -> Boolean,
    val continuation: CancellableContinuation<DownloadedSongFile>,
)

private sealed interface IosDownloadOutcome {
    data class Success(val file: DownloadedSongFile) : IosDownloadOutcome
    data class Failure(val error: Throwable) : IosDownloadOutcome
}

/**
 * One stable background NSURLSession for all iOS song downloads.
 *
 * `taskDescription` contains the validated destination file name. That alone is enough to finish
 * moving a system-owned temporary file after iOS relaunches the app and reconnects the
 * background session, even though the original coroutine no longer exists in that process.
 *
 * A Kotlin `object` cannot inherit from an Obj-C class (Kotlin/Native aborts codegen with
 * "Allocation of Obj-C class ... should have been lowered"), so this stays a plain singleton and
 * delegates the protocol conformance to [IosBackgroundDownloadSessionDelegate].
 */
internal object IosBackgroundDownloadCoordinator {
    private val stateLock = NSLock()
    private val pending = mutableMapOf<ULong, PendingIosDownload>()
    private val outcomes = mutableMapOf<ULong, IosDownloadOutcome>()
    private val cancelledTasks = mutableSetOf<ULong>()
    private var backgroundEventsCompletion: (() -> Unit)? = null

    private val sessionDelegate = IosBackgroundDownloadSessionDelegate()

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
            delegate = sessionDelegate,
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
        onReadyToPublish: () -> Boolean,
    ): DownloadedSongFile {
        require(isValidIosDownloadFileName(fileName)) { strings.downloadFileNameInvalid }
        return suspendCancellableCoroutine { continuation ->
            val task = session.downloadTaskWithURL(url)
            task.taskDescription = fileName
            val taskIdentifier = task.taskIdentifier
            withStateLock {
                check(pending.put(
                    taskIdentifier,
                    PendingIosDownload(fileName, onProgress, onReadyToPublish, continuation),
                ) == null) { "Duplicate iOS background download task identifier" }
            }
            continuation.invokeOnCancellation {
                withStateLock {
                    val hadPending = pending.remove(taskIdentifier) != null
                    val hadOutcome = outcomes.remove(taskIdentifier) != null
                    if (hadPending || hadOutcome) cancelledTasks += taskIdentifier
                }
                task.cancel()
                // NSURLSession cancellation is asynchronous: its late callback may arrive
                // after another task has saved the same song. Never delete final audio here.
                // Publication owns and cleans its task-specific staging file under stateLock.
            }
            task.resume()
        }
    }

    fun handleProgress(
        downloadTask: NSURLSessionDownloadTask,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        val callback = withStateLock { pending[downloadTask.taskIdentifier]?.onProgress }
        callback?.invoke(
            totalBytesWritten,
            totalBytesExpectedToWrite.takeIf { it > 0L },
        )
    }

    fun handleFinishedDownload(
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        val taskIdentifier = downloadTask.taskIdentifier
        withStateLock {
            if (taskIdentifier in cancelledTasks) return@withStateLock
            val request = pending[taskIdentifier]
            outcomes[taskIdentifier] = persistDownloadedTemporaryFile(downloadTask, didFinishDownloadingToURL, request)
        }
    }

    fun handleCompletion(
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        val taskIdentifier = task.taskIdentifier
        val (download, outcome, cancelled) = withStateLock {
            Triple(
                pending.remove(taskIdentifier),
                outcomes.remove(taskIdentifier),
                cancelledTasks.remove(taskIdentifier),
            )
        }
        if (cancelled) return
        download ?: return

        val finalOutcome = when {
            // A validated, claimed file is already published and must stay successful even
            // if a later transport callback carries an error (for example cancellation).
            outcome is IosDownloadOutcome.Success -> outcome
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
                download.continuation.resumeWith(Result.success(finalOutcome.file))
            }
            is IosDownloadOutcome.Failure -> {
                download.continuation.resumeWith(Result.failure(finalOutcome.error))
            }
        }
    }

    fun handleBackgroundSessionEventsFinished() {
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
        request: PendingIosDownload?,
    ): IosDownloadOutcome {
        val statusCode = (task.response as? NSHTTPURLResponse)?.statusCode
        if (statusCode != null && statusCode != 200L) {
            return IosDownloadOutcome.Failure(
                IllegalStateException(strings.downloadHttpFailed(statusCode)),
            )
        }
        val fileName = request?.fileName ?: task.taskDescription
        if (fileName == null || !isValidIosDownloadFileName(fileName)) {
            return IosDownloadOutcome.Failure(IllegalStateException(strings.backgroundDownloadNoFileName))
        }

        return runCatching {
            publishIosDownload(
                temporaryUrl = temporaryUrl,
                directory = ensureIosDownloadDirectory(),
                fileName = fileName,
                taskIdentifier = task.taskIdentifier,
                onProgress = { bytes -> request?.onProgress?.invoke(bytes, bytes) },
                onReadyToPublish = {
                    // Relaunched background transfers have no in-process coroutine to ask.
                    request == null || (request.continuation.isActive && request.onReadyToPublish())
                },
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

/**
 * NSURLSession retains its delegate and calls it back on [IosBackgroundDownloadCoordinator]'s
 * serial queue. This class exists only to be a real Obj-C object; all state lives in the
 * coordinator.
 */
private class IosBackgroundDownloadSessionDelegate :
    NSObject(), NSURLSessionDownloadDelegateProtocol {

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didWriteData: Long,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        IosBackgroundDownloadCoordinator.handleProgress(
            downloadTask = downloadTask,
            totalBytesWritten = totalBytesWritten,
            totalBytesExpectedToWrite = totalBytesExpectedToWrite,
        )
    }

    override fun URLSession(
        session: NSURLSession,
        downloadTask: NSURLSessionDownloadTask,
        didFinishDownloadingToURL: NSURL,
    ) {
        IosBackgroundDownloadCoordinator.handleFinishedDownload(
            downloadTask = downloadTask,
            didFinishDownloadingToURL = didFinishDownloadingToURL,
        )
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        IosBackgroundDownloadCoordinator.handleCompletion(
            task = task,
            didCompleteWithError = didCompleteWithError,
        )
    }

    override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
        IosBackgroundDownloadCoordinator.handleBackgroundSessionEventsFinished()
    }
}
