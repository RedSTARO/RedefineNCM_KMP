package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.auth.CredentialStore
import com.leejlredstar.redefinencm.kmp.data.auth.CredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodException
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodRegistry
import com.leejlredstar.redefinencm.kmp.data.auth.QrLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.QrLoginPoll
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.di.DEFAULT_NCM_SERVER
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.util.getStringAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/** The self-hosted backend address a provider reads, offered for editing beside its login. */
class ProviderServerSetting(
    val key: String,
    val default: String,
    val label: String,
    /** When a saved address is picked up, for the confirmation line. */
    val appliesWhen: String,
) {
    companion object {
        fun of(provider: MusicProviderId): ProviderServerSetting = when (provider) {
            MusicProviderId.NETEASE -> ProviderServerSetting(
                key = SettingKeys.SERVER,
                default = DEFAULT_NCM_SERVER,
                label = "服务器地址",
                appliesWhen = "服务器地址重启后生效",
            )
            MusicProviderId.QQ -> ProviderServerSetting(
                key = SettingKeys.QQ_SERVER,
                default = SettingKeys.QQ_SERVER_DEFAULT,
                label = "QQ 音乐后端地址",
                appliesWhen = "后端地址立即生效",
            )
        }
    }
}

/**
 * The login page's state for one provider, driven by whatever [LoginMethodRegistry] holds for it.
 *
 * The QR flow is written once against [QrLoginMethod] — issue, show, poll until a terminal answer,
 * persist — so a provider's method only maps its backend's codes onto [QrLoginPoll]. Persisting
 * goes through the provider's credential slot, which applies that provider's change rules.
 */
class LoginViewModel(
    val provider: MusicProviderId,
    registry: LoginMethodRegistry,
    credentials: CredentialStore,
    private val settings: PlatformSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val slot = credentials.require(provider)

    val qrMethods: List<QrLoginMethod> = registry.qrMethods(provider)
    val textMethod: CredentialTextLoginMethod? = registry.textMethod(provider)
    val serverSetting: ProviderServerSetting = ProviderServerSetting.of(provider)

    private val _selectedQrMethod = MutableStateFlow(qrMethods.firstOrNull())
    val selectedQrMethod: StateFlow<QrLoginMethod?> = _selectedQrMethod.asStateFlow()

    private val _server = MutableStateFlow("")
    val server: StateFlow<String> = _server.asStateFlow()

    private val _cookie = MutableStateFlow("")
    val cookie: StateFlow<String> = _cookie.asStateFlow()
    private val _cookiePersistError = MutableStateFlow<String?>(null)
    val cookiePersistError: StateFlow<String?> = _cookiePersistError.asStateFlow()

    private val _qrBitmapBytes = MutableStateFlow<ByteArray?>(null)
    val qrBitmapBytes: StateFlow<ByteArray?> = _qrBitmapBytes.asStateFlow()

    private val _qrScanStatus = MutableStateFlow(IdleStatus)
    val qrScanStatus: StateFlow<String> = _qrScanStatus.asStateFlow()

    private val _qrLoading = MutableStateFlow(false)
    val qrLoading: StateFlow<Boolean> = _qrLoading.asStateFlow()

    private val _qrError = MutableStateFlow("")
    val qrError: StateFlow<String> = _qrError.asStateFlow()

    private val _qrSuccess = MutableStateFlow(false)
    val qrSuccess: StateFlow<Boolean> = _qrSuccess.asStateFlow()

    // Expired is a state of its own: the old code only changed the status text, so the dead code
    // stayed on screen with a "取消" button beneath it.
    private val _qrExpired = MutableStateFlow(false)
    val qrExpired: StateFlow<Boolean> = _qrExpired.asStateFlow()

    private var qrLoginJob: Job? = null

    // Each QR flow gets a generation; a flow that has been replaced must not write its ending
    // (an error, or "not loading") over the flow that replaced it. Cancellation alone does not
    // guarantee that: the old job's catch and finally blocks run after the new job has started.
    @Volatile
    private var qrGeneration = 0

    init {
        scope.launch {
            settings.awaitLoaded()
            _server.value = settings.getStringAsync(serverSetting.key, serverSetting.default)
            _cookie.value = slot.read()
        }
    }

    /** Switches QR methods and, if a code is showing, replaces it with the new method's. */
    fun selectQrMethod(id: String) {
        val method = qrMethods.firstOrNull { it.id == id } ?: return
        if (method === _selectedQrMethod.value) return
        val wasRunning = qrLoginJob?.isActive == true
        cancelQrLogin()
        _selectedQrMethod.value = method
        if (wasRunning) startQrLogin()
    }

    fun updateCredentials(
        newServer: String,
        newCookie: String,
        onPersisted: (() -> Unit)? = null,
    ) {
        scope.launch {
            settings.awaitLoaded()
            val credential = (textMethod?.normalize(newCookie) ?: Result.success(newCookie.trim()))
                .getOrElse { failure ->
                    _cookiePersistError.value = failure.message ?: "凭证无法识别"
                    return@launch
                }
            val previousServer = settings.getString(serverSetting.key, _server.value)
            try {
                _server.value = newServer
                settings.setString(serverSetting.key, newServer)
                persistCredential(credential).getOrThrow()
                onPersisted?.invoke()
            } catch (failure: Exception) {
                // Credential persistence already rolled itself back. Restore the server too, as
                // the same user action must not report success after only half of its values
                // survived.
                runCatching {
                    settings.setString(serverSetting.key, previousServer)
                    settings.flush()
                }
                _server.value = settings.getString(serverSetting.key, previousServer)
                _cookiePersistError.value = failure.message ?: "设置保存失败"
            }
        }
    }

    /** Full QR login flow for the selected method: issue → show → poll → persist. */
    fun startQrLogin() {
        val method = _selectedQrMethod.value ?: return
        qrLoginJob?.cancel()
        val generation = ++qrGeneration
        // 网络必须离开 Main：桌面端 Main=Swing EDT，在 EDT 上跑 Ktor 连接协程会被 UI 渲染
        // 饿死导致零星 ConnectTimeout（与歌词拉取同源问题）。状态写回用 StateFlow，线程安全。
        qrLoginJob = scope.launch(Dispatchers.Default) {
            try {
                _qrLoading.value = true
                _qrError.value = ""
                _qrExpired.value = false
                _qrSuccess.value = false
                _qrScanStatus.value = "正在生成二维码…"
                _qrBitmapBytes.value = null

                val session = method.start()
                _qrBitmapBytes.value = session.imagePng
                _qrScanStatus.value = method.scanHint
                _qrLoading.value = false

                // Poll until a terminal answer, bounded by the code's lifetime. Keeping issue and
                // polling in one structured job guarantees that cancel/onCleared stops both.
                val finished = withTimeoutOrNull(method.lifetimeMillis) {
                    while (isActive) {
                        delay(method.pollIntervalMillis)
                        when (val poll = method.poll(session)) {
                            is QrLoginPoll.Waiting -> _qrScanStatus.value = poll.message ?: method.scanHint
                            QrLoginPoll.Scanned -> _qrScanStatus.value = "请在手机上确认登录"
                            is QrLoginPoll.Confirmed -> {
                                persistCredential(poll.credential)
                                    .onSuccess {
                                        _qrScanStatus.value = "登录成功！"
                                        _qrSuccess.value = true
                                    }
                                    .onFailure { failure ->
                                        _qrScanStatus.value = "登录状态保存失败"
                                        _qrError.value = failure.message ?: "凭证保存失败"
                                    }
                                return@withTimeoutOrNull true
                            }
                            QrLoginPoll.Expired -> {
                                expire()
                                return@withTimeoutOrNull true
                            }
                            QrLoginPoll.Refused -> {
                                _qrExpired.value = true
                                _qrScanStatus.value = "已在手机上取消登录，可重新生成二维码"
                                return@withTimeoutOrNull true
                            }
                            is QrLoginPoll.Failed -> {
                                _qrScanStatus.value = poll.message
                                _qrError.value = poll.message
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
                if (generation == qrGeneration) {
                    _qrScanStatus.value = "生成二维码失败"
                    _qrError.value = e.message ?: "未知错误"
                }
            } catch (e: Exception) {
                if (generation == qrGeneration) {
                    _qrScanStatus.value = "网络错误"
                    _qrError.value = e.message ?: "未知错误"
                }
            } finally {
                if (generation == qrGeneration) _qrLoading.value = false
            }
        }
    }

    fun cancelQrLogin() {
        qrLoginJob?.cancel()
        qrLoginJob = null
        qrGeneration += 1
        _qrBitmapBytes.value = null
        _qrScanStatus.value = IdleStatus
        _qrExpired.value = false
        _qrLoading.value = false
        _qrError.value = ""
    }

    private fun expire() {
        _qrExpired.value = true
        _qrScanStatus.value = "二维码已过期，请重新生成"
    }

    private suspend fun persistCredential(credential: String): Result<Unit> {
        val previous = _cookie.value
        _cookie.value = credential
        _cookiePersistError.value = null
        return slot.write(credential).onFailure { failure ->
            _cookie.value = previous
            _cookiePersistError.value = failure.message ?: "凭证保存失败"
        }
    }

    fun onCleared() {
        qrLoginJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val IdleStatus = "点击生成二维码"
    }
}
