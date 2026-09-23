package com.leejlredstar.redefinencm.kmp.data.provider

import com.leejlredstar.redefinencm.kmp.data.auth.AccountIdentitySource
import com.leejlredstar.redefinencm.kmp.data.auth.CredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderCredentialSlot
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderEnabledSetting
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptor
import com.leejlredstar.redefinencm.kmp.util.DEFAULT_SETTINGS_NODE
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A provider registers once, and everything derived from the registrations — the login methods a
 * platform offers, the startup sign-in check — follows from what was registered.
 */
class ProviderRegistrationTest {
    private class MemorySlot(
        override val provider: MusicProviderId,
        var value: String = "",
    ) : ProviderCredentialSlot {
        override suspend fun read(): String = value
        override suspend fun write(credential: String): Result<Unit> {
            value = credential
            return Result.success(Unit)
        }
        override suspend fun replace(expected: String, credential: String): Result<Boolean> =
            Result.success(false)
        override fun credentialUpdates(): Flow<String> = flowOf(value)
    }

    private class PastedCredential(override val provider: MusicProviderId) : CredentialTextLoginMethod {
        override val id = "${provider.key}.pasted"
        override val displayName = "手动输入"
        override val fieldLabel = "凭证"
        override val supportingText = ""
        override fun normalize(raw: String): Result<String> = Result.success(raw.trim())
    }

    private class NoProvider(override val id: MusicProviderId) : MusicProvider {
        override val capabilities: Set<ProviderCapability> = emptySet()
        override suspend fun isAvailable() = true
        override suspend fun search(keyword: String, limit: Int, offset: Int) = emptyList<ProviderTrack>()
        override suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? = null
        override suspend fun lyric(id: ProviderItemId): ProviderLyric? = null
        override suspend fun resolveStream(id: ProviderItemId, quality: SoundQualityPreference) =
            StreamResolution.Failed(StreamFailureReason.NO_SOURCE)
    }

    private val nodeName = "$DEFAULT_SETTINGS_NODE.test.provider-registration"
    private val settings = run {
        Preferences.userRoot().node(nodeName).removeNode()
        PlatformSettings(nodeName = nodeName)
    }

    @AfterTest
    fun removeNode() {
        Preferences.userRoot().node(nodeName).removeNode()
    }

    private fun descriptor(
        provider: MusicProviderId,
        switchKey: String? = null,
        signInUnavailableReason: String? = null,
    ) = ProviderLoginDescriptor(
        provider = provider,
        introduction = "",
        accountLabel = "",
        signedOutHint = "",
        logoutWarning = "",
        server = null,
        enabledSetting = switchKey?.let { ProviderEnabledSetting(it, default = false, label = "", supportingText = "") },
        signInUnavailableReason = signInUnavailableReason,
    )

    private fun registration(
        provider: MusicProviderId,
        slot: MemorySlot = MemorySlot(provider),
        switchKey: String? = null,
        signInUnavailableReason: String? = null,
    ) = ProviderRegistration(
        provider = NoProvider(provider),
        credentialSlot = slot,
        descriptor = descriptor(provider, switchKey, signInUnavailableReason),
        loginMethods = listOf(PastedCredential(provider)),
        identity = AccountIdentitySource { flowOf(null) },
    )

    @Test
    fun aPartFiledUnderAnotherProviderIsRejectedAtRegistration() {
        assertFailsWith<IllegalArgumentException> {
            ProviderRegistration(
                provider = NoProvider(MusicProviderId.QQ),
                credentialSlot = MemorySlot(MusicProviderId.NETEASE),
                descriptor = descriptor(MusicProviderId.QQ),
                loginMethods = emptyList(),
                identity = AccountIdentitySource { flowOf(null) },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProviderRegistration(
                provider = NoProvider(MusicProviderId.QQ),
                credentialSlot = MemorySlot(MusicProviderId.QQ),
                descriptor = descriptor(MusicProviderId.QQ),
                loginMethods = listOf(PastedCredential(MusicProviderId.NETEASE)),
                identity = AccountIdentitySource { flowOf(null) },
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProviderRegistrations(listOf(registration(MusicProviderId.QQ), registration(MusicProviderId.QQ)))
        }
    }

    @Test
    fun aPlatformThatCannotSignInToAProviderIsOfferedNoneOfItsMethods() {
        val registrations = ProviderRegistrations(
            listOf(
                registration(MusicProviderId.NETEASE),
                registration(MusicProviderId.QQ, signInUnavailableReason = "Web 无法携带凭证"),
            ),
        )
        val methods = registrations.loginMethodRegistry()
        assertEquals(1, methods.forProvider(MusicProviderId.NETEASE).size)
        assertTrue(methods.forProvider(MusicProviderId.QQ).isEmpty())
        // The descriptor stays: the accounts page still has to say why there is no sign-in.
        assertEquals(2, registrations.descriptorRegistry().all.size)
    }

    @Test
    fun onlyASwitchedOnProvidersAccountCountsAsSignedInAtLaunch() = runTest {
        val qqSlot = MemorySlot(MusicProviderId.QQ, value = "musicid=1; musickey=k")
        val registrations = ProviderRegistrations(
            listOf(
                registration(MusicProviderId.NETEASE),
                registration(MusicProviderId.QQ, slot = qqSlot, switchKey = "qqSwitch"),
            ),
        )
        // Signed in to QQ, but QQ is switched off: the launch still asks for an account.
        assertFalse(registrations.anySignedIn(settings))

        settings.setBoolean("qqSwitch", true)
        assertTrue(registrations.anySignedIn(settings))
    }

    @Test
    fun anAccountStoredWhereThePlatformCannotSignInDoesNotCount() = runTest {
        // Web cannot send QQ's credential, so one saved there earlier is never used.
        val qqSlot = MemorySlot(MusicProviderId.QQ, value = "musicid=1; musickey=k")
        val qq = registration(MusicProviderId.QQ, slot = qqSlot, signInUnavailableReason = "Web 无法携带凭证")
        val registrations = ProviderRegistrations(listOf(registration(MusicProviderId.NETEASE), qq))

        assertFalse(qq.holdsAccount(qqSlot.value))
        assertFalse(registrations.anySignedIn(settings))
    }
}
