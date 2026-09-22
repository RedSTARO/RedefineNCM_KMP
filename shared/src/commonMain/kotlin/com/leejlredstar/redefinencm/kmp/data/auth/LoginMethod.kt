package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId

/**
 * One way of signing in to one provider — a "login source".
 *
 * Every provider the app aggregates has its own idea of a credential and its own ways of obtaining
 * one, and the login page must not grow a branch per provider for each. Instead each way is an
 * object implementing one of the two shapes below, registered in [LoginMethodRegistry], and the
 * page renders whatever the registry holds for the provider it was opened for. Adding a login
 * method — WeChat for QQ Music, phone codes for NetEase, a third service altogether — is a new
 * implementation plus a registration, not an edit to the page.
 *
 * The credential itself is an opaque string here. What it means (a NetEase `Cookie` header, a QQ
 * gateway credential) is the concern of the provider's [ProviderCredentialSlot], which is the only
 * thing that reads it back.
 */
sealed interface LoginMethod {
    /** Stable and unique across the registry: `ncm.qr`, `qq.qr.wx`. */
    val id: String

    val provider: MusicProviderId

    /** The section or chip title: "扫码登录", "微信扫码", "手动输入". */
    val displayName: String
}

/** Shows a code for the provider's own app to scan, then waits for that app to approve it. */
interface QrLoginMethod : LoginMethod {
    /** Under the code: "请用网易云音乐 App 扫码". */
    val scanHint: String

    /** Milliseconds between two [poll] calls. */
    val pollIntervalMillis: Long get() = DefaultPollIntervalMillis

    /** How long a code is kept alive before the caller declares it expired on its own. */
    val lifetimeMillis: Long get() = DefaultLifetimeMillis

    /**
     * Issues a fresh code.
     *
     * @throws LoginMethodException when the provider could not issue one, with a message fit for
     *   the screen. Transport failures propagate as whatever the client throws.
     */
    suspend fun start(): QrLoginSession

    /** Asks once what became of [session]'s code. Same failure contract as [start]. */
    suspend fun poll(session: QrLoginSession): QrLoginPoll

    companion object {
        const val DefaultPollIntervalMillis = 2_000L
        const val DefaultLifetimeMillis = 5 * 60_000L
    }
}

/** A code on screen, and the handle the provider uses to say what happened to it. */
class QrLoginSession(
    val token: String,
    /** PNG bytes, ready for `decodePngToImageBitmap`. */
    val imagePng: ByteArray,
)

/** One answer to [QrLoginMethod.poll]. */
sealed interface QrLoginPoll {
    /**
     * Nothing has happened yet. [message] replaces the scan hint when the provider says something
     * the caller has no state for, so an unknown status is shown rather than ending the flow.
     */
    data class Waiting(val message: String? = null) : QrLoginPoll

    /** Scanned; the phone is asking the user to confirm. */
    data object Scanned : QrLoginPoll

    /** Approved. [credential] is what the provider's slot stores. */
    data class Confirmed(val credential: String) : QrLoginPoll

    /** The code outlived its validity; a new one has to be issued. */
    data object Expired : QrLoginPoll

    /** The user declined on the phone. */
    data object Refused : QrLoginPoll

    /** The provider answered in a way that ends the flow, such as approving without a credential. */
    data class Failed(val message: String) : QrLoginPoll
}

/** The user pastes a credential obtained elsewhere — a browser session, another client. */
interface CredentialTextLoginMethod : LoginMethod {
    /** The text field's label: "Cookie", "QQ 音乐凭证". */
    val fieldLabel: String

    /** Where such a credential comes from, shown under the field. */
    val supportingText: String

    /**
     * Validates pasted text and turns it into the stored form.
     *
     * An empty string is a valid result and means "signed out"; the page saves it as such. A
     * failure carries a message fit for the screen.
     */
    fun normalize(raw: String): Result<String>
}

/** A login method could not do what was asked; [message] is written for the screen. */
class LoginMethodException(message: String) : Exception(message)
