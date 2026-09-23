package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.i18n.strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The state machine every SMS-code login shares: take a phone number, ask for a code, hold the
 * user off from asking again for a while, exchange the typed code for a credential, persist, tell
 * the host. A [PhoneCodeLoginMethod] supplies only the provider-specific calls.
 */
class PhoneCodeLoginFlow(
    private val method: PhoneCodeLoginMethod,
    private val host: LoginHost,
    private val networkDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    /** A request is in flight: sending a code or verifying one. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** A code has been sent to [phone] and may be typed in. */
    private val _codeSent = MutableStateFlow(false)
    val codeSent: StateFlow<Boolean> = _codeSent.asStateFlow()

    /** Seconds until another code may be requested; zero when it may. */
    private val _resendInSeconds = MutableStateFlow(0)
    val resendInSeconds: StateFlow<Int> = _resendInSeconds.asStateFlow()

    /** A line of guidance: the code was sent, the provider wants a human check, and so on. */
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _error = MutableStateFlow("")
    val error: StateFlow<String> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    val phoneHint: String get() = method.phoneHint

    private var countdown: Job? = null

    fun setPhone(value: String) {
        _phone.value = value
        _error.value = ""
    }

    fun setCode(value: String) {
        _code.value = value.trim()
        _error.value = ""
    }

    val canSendCode: Boolean
        get() = !_busy.value && _resendInSeconds.value == 0 && normalizedPhone() != null

    val canVerify: Boolean
        get() = !_busy.value && _codeSent.value && _code.value.isNotBlank() && !_success.value

    /** Asks the provider for a code and starts the resend countdown on success. */
    fun sendCode() {
        val phone = normalizedPhone()
        if (phone == null) {
            _error.value = strings.phoneNumberInvalid
            return
        }
        if (!canSendCode) return
        host.scope.launch(networkDispatcher) {
            _busy.value = true
            _error.value = ""
            try {
                when (val answer = method.sendCode(phone)) {
                    PhoneCodeSend.Sent -> {
                        _codeSent.value = true
                        _message.value = strings.verificationCodeSent(phone)
                        startCountdown()
                    }
                    is PhoneCodeSend.Blocked -> {
                        _codeSent.value = false
                        _message.value = answer.message
                    }
                    is PhoneCodeSend.Failed -> _error.value = answer.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginMethodException) {
                _error.value = e.message ?: strings.unknownError
            } catch (e: Exception) {
                _error.value = strings.networkErrorWithMessage(e.message ?: strings.unknownError)
            } finally {
                _busy.value = false
            }
        }
    }

    /** Exchanges the typed code for a credential and persists it. */
    fun verify() {
        val phone = normalizedPhone() ?: return
        if (!canVerify) return
        host.scope.launch(networkDispatcher) {
            _busy.value = true
            _error.value = ""
            try {
                when (val answer = method.verify(phone, _code.value)) {
                    is PhoneCodeVerify.Confirmed -> host.persist(answer.credential)
                        .onSuccess {
                            _message.value = strings.signInSucceeded
                            _success.value = true
                            host.onSignedIn()
                        }
                        .onFailure { failure ->
                            _error.value = failure.message ?: strings.credentialSaveFailed
                        }
                    is PhoneCodeVerify.Failed -> _error.value = answer.message
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginMethodException) {
                _error.value = e.message ?: strings.unknownError
            } catch (e: Exception) {
                _error.value = strings.networkErrorWithMessage(e.message ?: strings.unknownError)
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancel() {
        countdown?.cancel()
        countdown = null
        _resendInSeconds.value = 0
    }

    /** Digits only, long enough to be a number; null otherwise. Country codes are the method's. */
    private fun normalizedPhone(): String? =
        _phone.value.filter(Char::isDigit).takeIf { it.length >= MinimumPhoneDigits }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = host.scope.launch {
            var remaining = method.resendIntervalSeconds
            while (remaining > 0) {
                _resendInSeconds.value = remaining
                delay(1_000L)
                remaining -= 1
            }
            _resendInSeconds.value = 0
        }
    }

    private companion object {
        const val MinimumPhoneDigits = 5
    }
}
