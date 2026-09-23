package com.leejlredstar.redefinencm.kmp.data.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * The state machine every QR login shares: issue a code, show it, poll until a terminal answer,
 * persist, tell the host. A [QrLoginMethod] supplies only the provider-specific steps.
 *
 * Compose-free so that tests can drive it and any presenter can draw it. One instance serves one
 * method on one page; [start] may be called again to issue a fresh code.
 */
class QrLoginFlow(
    private val method: QrLoginMethod,
    private val host: LoginHost,
    /** Where the network work runs; the page's own dispatcher is the Swing EDT on desktop. */
    private val networkDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _imagePng = MutableStateFlow<ByteArray?>(null)
    val imagePng: StateFlow<ByteArray?> = _imagePng.asStateFlow()

    private val _status = MutableStateFlow(IdleStatus)
    val status: StateFlow<String> = _status.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    // Expired is a state of its own. Changing only the status text would leave the dead code on
    // screen with a "取消" button beneath it.
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    val scanHint: String get() = method.scanHint

    private var job: Job? = null

    // Each run gets a generation; a run that has been replaced must not write its ending (an
    // error, or "not loading") over the run that replaced it. Cancellation alone does not
    // guarantee that: the old job's catch and finally blocks run after the new job has started.
    @Volatile
    private var generation = 0

    /** Issues a code and polls it to a terminal answer, replacing any run in progress. */
    fun start() {
        job?.cancel()
        val myGeneration = ++generation
        // 网络必须离开 Main：桌面端 Main=Swing EDT，在 EDT 上跑 Ktor 连接协程会被 UI 渲染
        // 饿死导致零星 ConnectTimeout（与歌词拉取同源问题）。状态写回用 StateFlow，线程安全。
        job = host.scope.launch(networkDispatcher) {
            try {
                _loading.value = true
                _error.value = ""
                _expired.value = false
                _success.value = false
                _status.value = "正在生成二维码…"
                _imagePng.value = null

                val session = method.start()
                _imagePng.value = session.imagePng
                _status.value = method.scanHint
                _loading.value = false

                // Poll until a terminal answer, bounded by the code's lifetime. Keeping issue and
                // polling in one structured job guarantees that cancel stops both.
                val finished = withTimeoutOrNull(method.lifetimeMillis) {
                    // Unanswered polls in a row, for this code only; any answer ends the run.
                    var unanswered = 0
                    while (isActive) {
                        delay(method.pollIntervalMillis)
                        val poll = method.poll(session)
                        unanswered = if (poll == QrLoginPoll.Unanswered) unanswered + 1 else 0
                        when (poll) {
                            QrLoginPoll.Unanswered -> {
                                if (unanswered >= method.maxUnansweredPolls) {
                                    val message = "${method.provider.displayName}服务器无响应"
                                    _status.value = message
                                    _error.value = message
                                    return@withTimeoutOrNull true
                                }
                                _status.value = "等待服务器响应…"
                            }
                            is QrLoginPoll.Waiting -> _status.value = poll.message ?: method.scanHint
                            QrLoginPoll.Scanned -> _status.value = "请在手机上确认登录"
                            is QrLoginPoll.Confirmed -> {
                                host.persist(poll.credential)
                                    .onSuccess {
                                        _status.value = "登录成功"
                                        _success.value = true
                                        host.onSignedIn()
                                    }
                                    .onFailure { failure ->
                                        _status.value = "登录状态保存失败"
                                        _error.value = failure.message ?: "凭证保存失败"
                                    }
                                return@withTimeoutOrNull true
                            }
                            QrLoginPoll.Expired -> {
                                expire()
                                return@withTimeoutOrNull true
                            }
                            QrLoginPoll.Refused -> {
                                _expired.value = true
                                _status.value = "已在手机上取消登录，可重新生成二维码"
                                return@withTimeoutOrNull true
                            }
                            is QrLoginPoll.Failed -> {
                                _status.value = poll.message
                                _error.value = poll.message
                                return@withTimeoutOrNull true
                            }
                        }
                    }
                    false
                }
                if (finished == null && currentCoroutineContext().isActive) expire()
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginMethodException) {
                if (myGeneration == generation) {
                    _status.value = "生成二维码失败"
                    _error.value = e.message ?: "未知错误"
                }
            } catch (e: Exception) {
                if (myGeneration == generation) {
                    _status.value = "网络错误"
                    _error.value = e.message ?: "未知错误"
                }
            } finally {
                if (myGeneration == generation) _loading.value = false
            }
        }
    }

    /** Stops the run in progress and clears the screen state back to idle. */
    fun cancel() {
        job?.cancel()
        job = null
        generation += 1
        _imagePng.value = null
        _status.value = IdleStatus
        _expired.value = false
        _loading.value = false
        _error.value = ""
    }

    private fun expire() {
        _expired.value = true
        _status.value = "二维码已过期，请重新生成"
    }

    private companion object {
        const val IdleStatus = "点按生成二维码"
    }
}
