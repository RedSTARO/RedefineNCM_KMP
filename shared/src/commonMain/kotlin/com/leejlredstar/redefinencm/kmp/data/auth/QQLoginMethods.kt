package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.i18n.strings

/**
 * The gateway's QR flow: `/login/qrcode/{qq|wx}` issues a code and `/login/qrcode/{type}/status`
 * reports on it with the events 0 done, 1 waiting, 2 scanned, 3 timed out, 4 refused, -1 other.
 * A finished login hands back the gateway's own credential, stored in its cookie form.
 */
class QQQrLoginMethod(
    private val api: QQMusicApi,
    private val kind: Kind,
) : QrLoginMethod {
    /** Which app scans the code; the gateway treats the two as separate login types. */
    enum class Kind(val loginType: String) {
        QQ("qq"),
        WECHAT("wx"),
        ;

        val displayName: String
            get() = when (this) {
                QQ -> strings.qqScanWithQq
                WECHAT -> strings.qqScanWithWechat
            }

        val scanHint: String
            get() = when (this) {
                QQ -> strings.qqScanWithQqHint
                WECHAT -> strings.qqScanWithWechatHint
            }
    }

    override val id: String = "qq.qr.${kind.loginType}"
    override val provider: MusicProviderId = MusicProviderId.QQ
    override val displayName: String get() = kind.displayName
    override val scanHint: String get() = kind.scanHint

    override suspend fun start(): QrLoginSession {
        val code = api.qrCodeStart(kind.loginType) ?: throw LoginMethodException(strings.qqServerNoResponse)
        if (code.identifier.isBlank() || code.data.isBlank()) throw LoginMethodException(strings.loginQrMissing)
        val png = runCatching { decodeBase64Image(code.data) }
            .getOrElse { throw LoginMethodException(strings.loginQrUnreadable) }
        return QrLoginSession(token = code.identifier, imagePng = png)
    }

    /**
     * The WeChat status route is a long poll the gateway holds for around fifteen seconds, so one
     * unanswered poll is a slow upstream rather than a dead gateway. The flow counts the run.
     */
    override suspend fun poll(session: QrLoginSession): QrLoginPoll {
        val status = api.qrCodeStatus(kind.loginType, session.token) ?: return QrLoginPoll.Unanswered
        return when (status.event) {
            EventDone -> status.credential
                ?.let(QQCredential::fromGateway)
                ?.takeIf { it.isSignedIn }
                ?.let { QrLoginPoll.Confirmed(it.toCookieHeader()) }
                ?: QrLoginPoll.Failed(strings.loginNoCredential)
            EventWaiting -> QrLoginPoll.Waiting()
            EventScanned -> QrLoginPoll.Scanned
            EventTimeout -> QrLoginPoll.Expired
            EventRefused -> QrLoginPoll.Refused
            else -> QrLoginPoll.Failed(strings.loginServerUnknownStatus(status.event))
        }
    }

    private companion object {
        const val EventDone = 0
        const val EventWaiting = 1
        const val EventScanned = 2
        const val EventTimeout = 3
        const val EventRefused = 4
    }
}

/**
 * The gateway's SMS login: `/login/phone/authcode` texts a code (events 0 sent, 1 a slider captcha
 * is wanted first, 2 too frequent, -1 other), `/login/phone/authorize` exchanges it for the
 * gateway's credential. The captcha cannot be shown here, so that answer ends the attempt with a
 * pointer to the QR methods.
 */
class QQPhoneCodeLoginMethod(private val api: QQMusicApi) : PhoneCodeLoginMethod {
    override val id: String = "qq.phone"
    override val provider: MusicProviderId = MusicProviderId.QQ
    override val displayName: String get() = strings.phoneVerificationCode
    override val phoneHint: String get() = strings.mainlandChinaPhoneHint

    override suspend fun sendCode(phone: String): PhoneCodeSend {
        val number = phone.toLongOrNull() ?: return PhoneCodeSend.Failed(strings.phoneNumberFormatInvalid)
        val answer = api.phoneSendCode(number) ?: throw LoginMethodException(strings.qqServerNoResponse)
        return when (answer.event) {
            SendEventSent -> PhoneCodeSend.Sent
            SendEventCaptcha -> PhoneCodeSend.Blocked(strings.qqSliderCaptchaRequired)
            SendEventFrequency -> PhoneCodeSend.Failed(strings.verificationCodeTooFrequent)
            else -> PhoneCodeSend.Failed(
                answer.info?.takeIf(String::isNotBlank) ?: strings.verificationCodeSendFailed(answer.event),
            )
        }
    }

    override suspend fun verify(phone: String, code: String): PhoneCodeVerify {
        val number = phone.toLongOrNull() ?: return PhoneCodeVerify.Failed(strings.phoneNumberFormatInvalid)
        // The gateway answers a non-zero code for a wrong or stale code, which the client reports
        // as null; a dead gateway reads the same way, so the message names both.
        val credential = api.phoneAuthorize(number, code)
            ?: return PhoneCodeVerify.Failed(strings.verificationCodeRejected)
        return QQCredential.fromGateway(credential)
            .takeIf { it.isSignedIn }
            ?.let { PhoneCodeVerify.Confirmed(it.toCookieHeader()) }
            ?: PhoneCodeVerify.Failed(strings.serverNoCredential)
    }

    private companion object {
        const val SendEventSent = 0
        const val SendEventCaptcha = 1
        const val SendEventFrequency = 2
    }
}

/** The gateway's credential, or a `y.qq.com` cookie, pasted by hand. */
class QQCredentialTextLoginMethod : CredentialTextLoginMethod {
    override val id: String = "qq.credential"
    override val provider: MusicProviderId = MusicProviderId.QQ
    override val displayName: String get() = strings.enterManually
    override val fieldLabel: String get() = strings.qqCredentialLabel
    override val supportingText: String =
        strings.qqCredentialHint

    override fun normalize(raw: String): Result<String> {
        if (raw.isBlank()) return Result.success("")
        val credential = QQCredential.parse(raw)
            ?: return Result.failure(
                LoginMethodException(strings.qqCredentialUnrecognized),
            )
        return Result.success(credential.toCookieHeader())
    }
}
