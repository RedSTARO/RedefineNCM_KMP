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
     * How many polls in a row have gone unanswered, per code. The WeChat status route is a long
     * poll that the gateway holds for around fifteen seconds, so a single unanswered poll is a
     * slow upstream, not a dead gateway; only a run of them ends the flow.
     */
    private val unansweredPolls = mutableMapOf<String, Int>()

    override suspend fun poll(session: QrLoginSession): QrLoginPoll {
        val status = api.qrCodeStatus(kind.loginType, session.token)
        if (status == null) {
            val unanswered = (unansweredPolls[session.token] ?: 0) + 1
            unansweredPolls[session.token] = unanswered
            return if (unanswered >= MaxUnansweredPolls) {
                unansweredPolls.remove(session.token)
                QrLoginPoll.Failed("QQ音乐后端无响应")
            } else {
                QrLoginPoll.Waiting("等待后端响应…")
            }
        }
        unansweredPolls.remove(session.token)
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
        const val MaxUnansweredPolls = 3
        const val EventDone = 0
        const val EventWaiting = 1
        const val EventScanned = 2
        const val EventTimeout = 3
        const val EventRefused = 4
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
