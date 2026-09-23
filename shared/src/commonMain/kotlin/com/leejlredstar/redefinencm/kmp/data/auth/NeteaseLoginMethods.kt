package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.api.NCMApi
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.i18n.strings

/**
 * NetEase's QR flow against a NeteaseCloudMusicApi backend: `/login/qr/key` → `/login/qr/create`
 * → poll `/login/qr/check`, whose codes are 800 expired, 801 waiting, 802 scanned, 803 approved.
 */
class NeteaseQrLoginMethod(private val api: NCMApi) : QrLoginMethod {
    override val id: String = "ncm.qr"
    override val provider: MusicProviderId = MusicProviderId.NETEASE
    override val displayName: String get() = strings.signInWithQrCode
    override val scanHint: String get() = strings.neteaseQrScanHint

    override suspend fun start(): QrLoginSession {
        val key = api.loginQrKey().takeIf { it.code == SuccessCode }?.data?.unikey
        if (key.isNullOrEmpty()) throw LoginMethodException(strings.loginQrKeyEmpty)
        val image = api.loginQrCreate(key, qrimg = true).takeIf { it.code == SuccessCode }?.data?.qrimg
        if (image.isNullOrEmpty()) throw LoginMethodException(strings.loginQrMissing)
        val png = runCatching { decodeBase64Image(image) }
            .getOrElse { throw LoginMethodException(strings.loginQrUnreadable) }
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
                QrLoginPoll.Failed(strings.loginNoCookie)
            }
            // Anything else is shown and polling continues.
            else -> QrLoginPoll.Waiting(check.message.ifBlank { strings.loginUnknownStatus(check.code) })
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
    override val displayName: String get() = strings.enterManually
    override val fieldLabel: String = "Cookie"
    override val supportingText: String get() = strings.neteaseCookieHint

    override fun normalize(raw: String): Result<String> = Result.success(raw.trim())
}
