package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.safeApiCall
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LoginViewModel with QR login support and cookie/server management.
 */
class LoginViewModel(
    private val api: NCMApi,
    private val settings: PlatformSettings,
    private val mainViewModel: MainViewModel,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _server = MutableStateFlow("")
    val server: StateFlow<String> = _server.asStateFlow()

    private val _cookie = MutableStateFlow("")
    val cookie: StateFlow<String> = _cookie.asStateFlow()
    private val _cookiePersistError = MutableStateFlow<String?>(null)
    val cookiePersistError: StateFlow<String?> = _cookiePersistError.asStateFlow()

    // QR login: store the base64 PNG as a data URI for Coil
    private val _qrDataUri = MutableStateFlow("")
    val qrDataUri: StateFlow<String> = _qrDataUri.asStateFlow()

    private val _qrBitmapBytes = MutableStateFlow<ByteArray?>(null)
    val qrBitmapBytes: StateFlow<ByteArray?> = _qrBitmapBytes.asStateFlow()

    private val _qrScanStatus = MutableStateFlow("点击生成二维码")
    val qrScanStatus: StateFlow<String> = _qrScanStatus.asStateFlow()

    private val _qrLoading = MutableStateFlow(false)
    val qrLoading: StateFlow<Boolean> = _qrLoading.asStateFlow()

    private val _qrError = MutableStateFlow("")
    val qrError: StateFlow<String> = _qrError.asStateFlow()

    private val _qrSuccess = MutableStateFlow(false)
    val qrSuccess: StateFlow<Boolean> = _qrSuccess.asStateFlow()

    private var qrLoginJob: Job? = null

    init {
        scope.launch {
            settings.awaitLoaded()
            _server.value = settings.getStringAsync(SettingKeys.SERVER, "https://ncm.tryagain.icu/")
            _cookie.value = settings.getStringAsync(SettingKeys.COOKIE, "")
        }
    }

    fun updateCredentials(
        newServer: String,
        newCookie: String,
        onPersisted: (() -> Unit)? = null,
    ) {
        scope.launch {
            settings.awaitLoaded()
            val previousServer = settings.getString(SettingKeys.SERVER, _server.value)
            try {
                _server.value = newServer
                settings.setString(SettingKeys.SERVER, newServer)
                persistCookie(newCookie).getOrThrow()
                onPersisted?.invoke()
            } catch (failure: Exception) {
                // Cookie persistence already rolls back identity state. Restore the server as the
                // same user action must not report success after only half of its values survived.
                runCatching {
                    settings.setString(SettingKeys.SERVER, previousServer)
                    settings.flush()
                }
                _server.value = settings.getString(SettingKeys.SERVER, previousServer)
                _cookiePersistError.value = failure.message ?: "设置保存失败"
            }
        }
    }

    /**
     * Full QR login flow: key → create → poll → save cookie.
     */
    fun startQrLogin() {
        qrLoginJob?.cancel()
        // 网络必须离开 Main：桌面端 Main=Swing EDT，在 EDT 上跑 Ktor 连接协程会被 UI 渲染
        // 饿死导致零星 ConnectTimeout（与歌词拉取同源问题）。状态写回用 StateFlow，线程安全。
        qrLoginJob = scope.launch(Dispatchers.Default) {
            try {
                _qrLoading.value = true
                _qrError.value = ""
                _qrSuccess.value = false
                _qrScanStatus.value = "正在生成二维码…"
                _qrDataUri.value = ""
                _qrBitmapBytes.value = null

                // 1. Get QR key
                val keyResult = safeApiCall { api.loginQrKey() }
                    ?.takeIf { it.code == 200 }
                val key = keyResult?.data?.unikey
                if (key.isNullOrEmpty()) {
                    _qrScanStatus.value = "获取 key 失败，请重试"
                    _qrError.value = "服务器返回空 key"
                    return@launch
                }

                // 2. Create QR code
                val createResult = safeApiCall { api.loginQrCreate(key, qrimg = true) }
                    ?.takeIf { it.code == 200 }
                val imgBase64 = createResult?.data?.qrimg
                if (imgBase64.isNullOrEmpty()) {
                    _qrScanStatus.value = "生成二维码失败"
                    _qrError.value = "服务器未返回二维码"
                    return@launch
                }
                // Strip any data URI prefix and decode PNG bytes for Compose rendering
                val strippedBase64 = imgBase64.substringAfter("base64,")
                _qrDataUri.value = "data:image/png;base64,$strippedBase64"
                _qrBitmapBytes.value = decodeBase64(strippedBase64)
                _qrScanStatus.value = "请用网易云音乐 App 扫码"
                _qrLoading.value = false

                // 3. Poll login status every 2s, bounded by the QR lifetime. Keeping creation and
                // polling in one structured job guarantees that cancel/onCleared stops both.
                val finished = withTimeoutOrNull(QR_POLL_TIMEOUT_MS) {
                    while (isActive) {
                        delay(QR_POLL_INTERVAL_MS)
                        val checkResult = safeApiCall { api.loginQrCheck(key) }
                        when (checkResult?.code) {
                            800 -> {
                                _qrScanStatus.value = "二维码已过期，请重新生成"
                                return@withTimeoutOrNull true
                            }
                            801 -> { /* waiting for scan */ }
                            802 -> {
                                _qrScanStatus.value = "请在手机上确认登录"
                            }
                            803 -> {
                                val cookie = checkResult.cookie
                                if (!cookie.isNullOrEmpty()) {
                                    persistCookie(cookie)
                                        .onSuccess {
                                            _qrScanStatus.value = "登录成功！"
                                            _qrSuccess.value = true
                                        }
                                        .onFailure { failure ->
                                            _qrScanStatus.value = "登录状态保存失败"
                                            _qrError.value = failure.message ?: "Cookie 保存失败"
                                        }
                                } else {
                                    _qrScanStatus.value = "登录成功但未获取到 Cookie"
                                    _qrError.value = "服务器返回空 Cookie"
                                }
                                return@withTimeoutOrNull true
                            }
                            else -> {
                                _qrScanStatus.value = checkResult?.message
                                    ?: "未知状态 (${checkResult?.code})"
                            }
                        }
                    }
                    false
                }
                if (finished == null && currentCoroutineContext().isActive) {
                    _qrScanStatus.value = "二维码已过期，请重新生成"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _qrScanStatus.value = "网络错误"
                _qrError.value = e.message ?: "未知错误"
            } finally {
                _qrLoading.value = false
            }
        }
    }

    fun cancelQrLogin() {
        qrLoginJob?.cancel()
        qrLoginJob = null
        _qrDataUri.value = ""
        _qrBitmapBytes.value = null
        _qrScanStatus.value = "点击生成二维码"
        _qrLoading.value = false
        _qrError.value = ""
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decodeBase64(input: String): ByteArray {
        // Fix missing padding: API's qrimg base64 may not be a multiple of 4
        val padded = when (input.length % 4) {
            1 -> input + "==="
            2 -> input + "=="
            3 -> input + "="
            else -> input
        }
        return kotlin.io.encoding.Base64.decode(padded)
    }

    private suspend fun persistCookie(newCookie: String): Result<Unit> {
        settings.awaitLoaded()
        val previousCookie = settings.getString(SettingKeys.COOKIE, _cookie.value)
        val previousUid = settings.getLong(SettingKeys.UID, 0L)
        val previousFingerprint = settings.getLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
        return withContext(NonCancellable) {
            try {
                _cookie.value = newCookie
                _cookiePersistError.value = null
                // Clear the identity binding before changing the credential. Even if the process
                // dies between individual DataStore edits, startup will never trust the old UID
                // for the new cookie.
                settings.setLong(SettingKeys.UID, 0L)
                settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, 0L)
                settings.setString(SettingKeys.COOKIE, newCookie)
                settings.flush()
                mainViewModel.refreshAccount()
                Result.success(Unit)
            } catch (failure: Exception) {
                // Setters update the process cache synchronously. Roll all three values back so a
                // failed commit cannot leave requests using an unpersisted credential.
                settings.setLong(SettingKeys.UID, previousUid)
                settings.setLong(SettingKeys.UID_COOKIE_FINGERPRINT, previousFingerprint)
                settings.setString(SettingKeys.COOKIE, previousCookie)
                runCatching { settings.flush() }
                _cookie.value = previousCookie
                _cookiePersistError.value = failure.message ?: "Cookie 保存失败"
                Result.failure(failure)
            }
        }
    }

    fun onCleared() {
        qrLoginJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val QR_POLL_INTERVAL_MS = 2_000L
        const val QR_POLL_TIMEOUT_MS = 5 * 60_000L
    }
}
