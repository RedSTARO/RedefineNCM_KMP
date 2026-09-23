package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.QQMusicApi
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId

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
    enum class Kind(val loginType: String, val displayName: String, val scanHint: String) {
        QQ("qq", "QQ 扫码", "请用手机 QQ 扫码"),
        WECHAT("wx", "微信扫码", "请用微信扫码"),
    }

    override val id: String = "qq.qr.${kind.loginType}"
    override val provider: MusicProviderId = MusicProviderId.QQ
    override val displayName: String = kind.displayName
    override val scanHint: String = kind.scanHint

    override suspend fun start(): QrLoginSession {
        val code = api.qrCodeStart(kind.loginType) ?: throw LoginMethodException("QQ音乐后端无响应")
        if (code.identifier.isBlank() || code.data.isBlank()) throw LoginMethodException("后端未返回二维码")
        val png = runCatching { decodeBase64Image(code.data) }
            .getOrElse { throw LoginMethodException("二维码数据无法解析") }
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
                ?: QrLoginPoll.Failed("登录成功但后端未返回凭证")
            EventWaiting -> QrLoginPoll.Waiting()
            EventScanned -> QrLoginPoll.Scanned
            EventTimeout -> QrLoginPoll.Expired
            EventRefused -> QrLoginPoll.Refused
            else -> QrLoginPoll.Failed("后端返回未知状态 (${status.event})")
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
    override val displayName: String = "手机验证码"
    override val phoneHint: String = "中国大陆手机号（+86）"

    override suspend fun sendCode(phone: String): PhoneCodeSend {
        val number = phone.toLongOrNull() ?: return PhoneCodeSend.Failed("手机号格式不正确")
        val answer = api.phoneSendCode(number) ?: throw LoginMethodException("QQ音乐后端无响应")
        return when (answer.event) {
            SendEventSent -> PhoneCodeSend.Sent
            SendEventCaptcha -> PhoneCodeSend.Blocked("QQ 要求先完成滑块验证，应用内无法完成；请改用扫码登录")
            SendEventFrequency -> PhoneCodeSend.Failed("验证码发送过于频繁，请稍后再试")
            else -> PhoneCodeSend.Failed(
                answer.info?.takeIf(String::isNotBlank) ?: "验证码发送失败 (${answer.event})",
            )
        }
    }

    override suspend fun verify(phone: String, code: String): PhoneCodeVerify {
        val number = phone.toLongOrNull() ?: return PhoneCodeVerify.Failed("手机号格式不正确")
        // The gateway answers a non-zero code for a wrong or stale code, which the client reports
        // as null; a dead gateway reads the same way, so the message names both.
        val credential = api.phoneAuthorize(number, code)
            ?: return PhoneCodeVerify.Failed("验证码错误、已过期，或后端无响应")
        return QQCredential.fromGateway(credential)
            .takeIf { it.isSignedIn }
            ?.let { PhoneCodeVerify.Confirmed(it.toCookieHeader()) }
            ?: PhoneCodeVerify.Failed("后端未返回凭证")
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
    override val displayName: String = "手动输入"
    override val fieldLabel: String = "QQ 音乐凭证"
    override val supportingText: String =
        "可粘贴 y.qq.com 登录后的 Cookie（含 uin 与 qm_keyst），或网关返回的 Credential JSON"

    override fun normalize(raw: String): Result<String> {
        if (raw.isBlank()) return Result.success("")
        val credential = QQCredential.parse(raw)
            ?: return Result.failure(
                LoginMethodException("无法识别的凭证：需要同时包含 musicid 与 musickey，或 uin 与 qm_keyst"),
            )
        return Result.success(credential.toCookieHeader())
    }
}
