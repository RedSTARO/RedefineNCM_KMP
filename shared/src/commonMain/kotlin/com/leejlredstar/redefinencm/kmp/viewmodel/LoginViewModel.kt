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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _server = MutableStateFlow("")
    val server: StateFlow<String> = _server.asStateFlow()

    private val _cookie = MutableStateFlow("")
    val cookie: StateFlow<String> = _cookie.asStateFlow()

    private val _cookieLoginLoading = MutableStateFlow(false)
    val cookieLoginLoading: StateFlow<Boolean> = _cookieLoginLoading.asStateFlow()

    private val _cookieLoginErrorMessage = MutableStateFlow("")
    val cookieLoginErrorMessage: StateFlow<String> = _cookieLoginErrorMessage.asStateFlow()

    // QR login: store the base64 PNG as a data URI for Coil
    private val _qrDataUri = MutableStateFlow("")
    val qrDataUri: StateFlow<String> = _qrDataUri.asStateFlow()

    private val _qrUrl = MutableStateFlow("")
    val qrUrl: StateFlow<String> = _qrUrl.asStateFlow()

    private val _qrBitmapBytes = MutableStateFlow<ByteArray?>(null)
    val qrBitmapBytes: StateFlow<ByteArray?> = _qrBitmapBytes.asStateFlow()

    private val _qrUnikey = MutableStateFlow("")
    val qrUnikey: StateFlow<String> = _qrUnikey.asStateFlow()

    private val _qrScanStatus = MutableStateFlow("点击生成二维码")
    val qrScanStatus: StateFlow<String> = _qrScanStatus.asStateFlow()

    private val _qrLoading = MutableStateFlow(false)
    val qrLoading: StateFlow<Boolean> = _qrLoading.asStateFlow()

    private val _qrError = MutableStateFlow("")
    val qrError: StateFlow<String> = _qrError.asStateFlow()

    private val _qrSuccess = MutableStateFlow(false)
    val qrSuccess: StateFlow<Boolean> = _qrSuccess.asStateFlow()

    private var qrPollJob: Job? = null

    init {
        loadServer()
        _cookie.value = settings.getString(SettingKeys.COOKIE, "")
    }

    private fun loadServer() {
        scope.launch {
            _server.value = settings.getStringAsync(SettingKeys.SERVER, "http://ncm.tryagain.icu/")
        }
    }

    fun updateCookie(newCookie: String) {
        _cookie.value = newCookie
        settings.setString(SettingKeys.COOKIE, newCookie)
    }

    fun updateServer(newServer: String) {
        _server.value = newServer
        settings.setString(SettingKeys.SERVER, newServer)
    }

    fun setCookieLoginLoading(loading: Boolean) {
        _cookieLoginLoading.value = loading
    }

    fun setCookieLoginError(message: String) {
        _cookieLoginErrorMessage.value = message
    }

    /**
     * Full QR login flow: key → create → poll → save cookie.
     */
    fun startQrLogin() {
        scope.launch {
            try {
                _qrLoading.value = true
                _qrError.value = ""
                _qrSuccess.value = false
                _qrScanStatus.value = "正在生成二维码…"
                _qrDataUri.value = ""
        _qrUrl.value = ""
        _qrBitmapBytes.value = null

                // 1. Get QR key
                val keyResult = safeApiCall { api.loginQrKey() }
                println("[QR] loginQrKey result: code=${keyResult?.code}, unikey=${keyResult?.data?.unikey}")
                val key = keyResult?.data?.unikey
                if (key.isNullOrEmpty()) {
                    _qrScanStatus.value = "获取 key 失败，请重试"
                    _qrError.value = "服务器返回空 key"
                    _qrLoading.value = false
                    return@launch
                }
                _qrUnikey.value = key

                // 2. Create QR code
                val createResult = safeApiCall { api.loginQrCreate(key, qrimg = true) }
                val imgBase64 = createResult?.data?.qrimg
                val directUrl = createResult?.data?.qrurl
                if (imgBase64.isNullOrEmpty()) {
                    _qrScanStatus.value = "生成二维码失败"
                    _qrError.value = "服务器未返回二维码"
                    _qrLoading.value = false
                    return@launch
                }
                // Strip any data URI prefix and decode PNG bytes for Compose rendering
                val strippedBase64 = imgBase64!!.substringAfter("base64,")
                _qrDataUri.value = "data:image/png;base64,$strippedBase64"
                _qrBitmapBytes.value = decodeBase64(strippedBase64)
                _qrScanStatus.value = "请用网易云音乐 App 扫码"
                _qrLoading.value = false

                // 3. Poll login status every 2s
                qrPollJob?.cancel()
                qrPollJob = scope.launch {
                    while (isActive) {
                        delay(2000)
                        val checkResult = safeApiCall { api.loginQrCheck(key) }
                        when (checkResult?.code) {
                            800 -> {
                                _qrScanStatus.value = "二维码已过期，请重新生成"
                                break
                            }
                            801 -> { /* waiting for scan */ }
                            802 -> {
                                _qrScanStatus.value = "请在手机上确认登录"
                            }
                            803 -> {
                                val cookie = checkResult.cookie
                                if (!cookie.isNullOrEmpty()) {
                                    updateCookie(cookie)
                                    _cookie.value = cookie
                                    _qrScanStatus.value = "登录成功！"
                                    _qrSuccess.value = true
                                } else {
                                    _qrScanStatus.value = "登录成功但未获取到 Cookie"
                                    _qrError.value = "服务器返回空 Cookie"
                                }
                                break
                            }
                            else -> {
                                _qrScanStatus.value = checkResult?.message
                                    ?: "未知状态 (${checkResult?.code})"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _qrScanStatus.value = "网络错误"
                _qrError.value = e.message ?: "未知错误"
                _qrLoading.value = false
            }
        }
    }

    fun cancelQrLogin() {
        qrPollJob?.cancel()
        qrPollJob = null
        _qrDataUri.value = ""
        _qrUrl.value = ""
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

    fun onCleared() {
        qrPollJob?.cancel()
        scope.cancel()
    }
}
