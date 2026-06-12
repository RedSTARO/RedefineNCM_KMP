package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.api.dto.*
import com.leejlredstar.redefinencm.kmp.data.api.safeApiCall
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ported from the original Android LoginViewModel.
 * QR login + cookie login, platform-independent.
 */
class LoginViewModel(
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

    private val _qrLoginBitmap = MutableStateFlow<ByteArray?>(null)
    val qrLoginBitmap: StateFlow<ByteArray?> = _qrLoginBitmap.asStateFlow()

    private val _qrLoginUnikey = MutableStateFlow("")
    val qrLoginUnikey: StateFlow<String> = _qrLoginUnikey.asStateFlow()

    private val _qrLoginScanStatus = MutableStateFlow("Generating Code")
    val qrLoginScanStatus: StateFlow<String> = _qrLoginScanStatus.asStateFlow()

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

    fun setQrBitmap(bytes: ByteArray?) {
        _qrLoginBitmap.value = bytes
    }

    fun setQrUnikey(key: String) {
        _qrLoginUnikey.value = key
    }

    fun setQrScanStatus(status: String) {
        _qrLoginScanStatus.value = status
    }

    fun onCleared() {
        scope.cancel()
    }
}
