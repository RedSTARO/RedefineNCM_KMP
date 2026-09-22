package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId

/**
 * NetEase's QR flow against a NeteaseCloudMusicApi backend: `/login/qr/key` → `/login/qr/create`
 * → poll `/login/qr/check`, whose codes are 800 expired, 801 waiting, 802 scanned, 803 approved.
 */
class NeteaseQrLoginMethod(private val api: NCMApi) : QrLoginMethod {
    override val id: String = "ncm.qr"
    override val provider: MusicProviderId = MusicProviderId.NETEASE
    override val displayName: String = "扫码登录"
    override val scanHint: String = "请用网易云音乐 App 扫码"

    override suspend fun start(): QrLoginSession {
        val key = api.loginQrKey().takeIf { it.code == SuccessCode }?.data?.unikey
        if (key.isNullOrEmpty()) throw LoginMethodException("服务器返回空 key")
        val image = api.loginQrCreate(key, qrimg = true).takeIf { it.code == SuccessCode }?.data?.qrimg
        if (image.isNullOrEmpty()) throw LoginMethodException("服务器未返回二维码")
        val png = runCatching { decodeBase64Image(image) }
            .getOrElse { throw LoginMethodException("二维码数据无法解析") }
        return QrLoginSession(token = key, imagePng = png)
    }

    override suspend fun poll(session: QrLoginSession): QrLoginPoll {
        val check = api.loginQrCheck(session.token)
        return when (check.code) {
            800 -> QrLoginPoll.Expired
            801 -> QrLoginPoll.Waiting()
            802 -> QrLoginPoll.Scanned
            803 -> if (check.cookie.isNotEmpty()) {
                QrLoginPoll.Confirmed(check.cookie)
            } else {
                QrLoginPoll.Failed("登录成功但未获取到 Cookie")
            }
            // Anything else is shown and polling continues, as the page did before methods existed.
            else -> QrLoginPoll.Waiting(check.message.ifBlank { "未知状态 (${check.code})" })
        }
    }

    private companion object {
        const val SuccessCode = 200
    }
}

/** A `Cookie` header copied from a signed-in browser or another client. */
class NeteaseCookieLoginMethod : CredentialTextLoginMethod {
    override val id: String = "ncm.cookie"
    override val provider: MusicProviderId = MusicProviderId.NETEASE
    override val displayName: String = "手动输入"
    override val fieldLabel: String = "Cookie"
    override val supportingText: String = "已有登录 Cookie 时可以直接粘贴"

    override fun normalize(raw: String): Result<String> = Result.success(raw.trim())
}
