package com.leejlredstar.redefinencm.kmp.data.auth

import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId

/**
 * One way of signing in to one provider — a "login source".
 *
 * A login source is a plugin in two halves. This half, in the data layer, is the logic: what the
 * provider's backend is asked and what its answers mean. The other half is a
 * `ui/login/LoginMethodPresenter`, which knows how to draw a method of this shape and owns its
 * on-screen state. The login page is only a host: it lists the methods registered for a provider
 * in [LoginMethodRegistry], hands each to the presenter that supports it, and offers persistence
 * and navigation through [LoginHost].
 *
 * So adding a login method of an existing shape (another app that scans a code) is one class plus
 * a registration; adding a new shape (a code sent by SMS, a password) is a new sub-interface here,
 * a presenter for it, and their registrations. Nothing in the page changes either way.
 *
 * The credential itself is an opaque string here. What it means (a NetEase `Cookie` header, a QQ
 * gateway credential) is the concern of the provider's [ProviderCredentialSlot], which is the only
 * thing that reads it back.
 */
interface LoginMethod {
    /** Stable and unique across the registry: `ncm.qr`, `qq.qr.wx`, `qq.phone`. */
    val id: String

    val provider: MusicProviderId

    /** The section or chip title: "扫码登录", "微信扫码", "手机验证码", "手动输入". */
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
     * How many [QrLoginPoll.Unanswered] polls in a row the caller sits through before it gives up
     * on the backend. A status route that long-polls upstream can miss one answer without being
     * down.
     */
    val maxUnansweredPolls: Int get() = DefaultMaxUnansweredPolls

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
        const val DefaultMaxUnansweredPolls = 3
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

    /**
     * The backend did not answer this poll. Counting a run of these is the caller's job, so a
     * method holds no state between polls.
     */
    data object Unanswered : QrLoginPoll

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

/** A one-time code the provider texts to the user's phone, typed back in. */
interface PhoneCodeLoginMethod : LoginMethod {
    /** Under the phone field: which numbers the provider accepts. */
    val phoneHint: String

    /** Seconds the user is asked to wait before requesting another code. */
    val resendIntervalSeconds: Int get() = DefaultResendIntervalSeconds

    /**
     * Asks the provider to text a code to [phone].
     *
     * @throws LoginMethodException when the provider could not be asked at all, with a message fit
     *   for the screen. Transport failures propagate as whatever the client throws.
     */
    suspend fun sendCode(phone: String): PhoneCodeSend

    /** Exchanges the code for a credential. Same failure contract as [sendCode]. */
    suspend fun verify(phone: String, code: String): PhoneCodeVerify

    companion object {
        const val DefaultResendIntervalSeconds = 60
    }
}

/** One answer to [PhoneCodeLoginMethod.sendCode]. */
sealed interface PhoneCodeSend {
    data object Sent : PhoneCodeSend

    /** The provider wants a human check first, which this app cannot show; [message] says so. */
    data class Blocked(val message: String) : PhoneCodeSend

    data class Failed(val message: String) : PhoneCodeSend
}

/** One answer to [PhoneCodeLoginMethod.verify]. */
sealed interface PhoneCodeVerify {
    data class Confirmed(val credential: String) : PhoneCodeVerify

    data class Failed(val message: String) : PhoneCodeVerify
}

/** A login method could not do what was asked; [message] is written for the screen. */
class LoginMethodException(message: String) : Exception(message)
