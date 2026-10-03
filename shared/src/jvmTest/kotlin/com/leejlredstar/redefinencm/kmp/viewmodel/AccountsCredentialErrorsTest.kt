package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.auth.AccountIdentitySource
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderCredentialSlot
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptor
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredentialSlot
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.local.LocalAccount
import com.leejlredstar.redefinencm.kmp.data.provider.*
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.uiText
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsCredentialErrorsTest {
    private object UnusedProvider : MusicProvider {
        override val id = MusicProviderId.QQ
        override val capabilities = emptySet<ProviderCapability>()
        override suspend fun isAvailable() = true
        override suspend fun search(keyword: String, limit: Int, offset: Int) = emptyList<ProviderTrack>()
        override suspend fun playlistDetail(id: ProviderItemId): ProviderPlaylist? = null
        override suspend fun lyric(id: ProviderItemId): ProviderLyric? = null
        override suspend fun resolveStream(id: ProviderItemId, quality: SoundQualityPreference) =
            StreamResolution.Failed(StreamFailureReason.NO_SOURCE)
    }

    private fun TestScope.page(settings: PlatformSettings, slot: ProviderCredentialSlot) = AccountsViewModel(
        ProviderRegistrations(listOf(ProviderRegistration(
            provider = UnusedProvider,
            credentialSlot = slot,
            descriptor = ProviderLoginDescriptor(
                MusicProviderId.QQ, uiText(""), uiText(""), uiText(""), uiText(""), server = null,
            ),
            loginMethods = listOf(QQCredentialTextLoginMethod()),
            identity = AccountIdentitySource { flowOf(null) },
        ))),
        settings,
        LocalAccount(settings),
        backgroundScope,
    )

    @Test
    fun theJvmPreferenceLengthFailureDoesNotExposeThePastedCredential() = runTest {
        val node = "accounts-error-test-${UUID.randomUUID()}"
        try {
            val settings = PlatformSettings(node)
            val slot = QQCredentialSlot(settings)
            val previous = "musicid=1; musickey=previous"
            slot.write(previous).getOrThrow()
            val host = page(settings, slot)
            val oversized = "musicid=1; musickey=PRIVATE_TEST_TOKEN" + "x".repeat(Preferences.MAX_VALUE_LENGTH)
            assertTrue(QQCredentialTextLoginMethod().normalize(oversized).isSuccess)
            host.saveCredential(MusicProviderId.QQ, oversized)
            runCurrent()
            assertEquals(strings.credentialSaveFailed, host.message.value)
            assertFalse(host.message.value.orEmpty().contains("PRIVATE_TEST_TOKEN"))
            assertEquals(previous, slot.read())
        } finally { Preferences.userRoot().node(node).removeNode() }
    }

    @Test
    fun signOutStorageFailuresDoNotExposeThePreviousCredential() = runTest {
        val node = "accounts-error-test-${UUID.randomUUID()}"
        try {
            val settings = PlatformSettings(node)
            val slot = object : ProviderCredentialSlot {
                override val provider = MusicProviderId.QQ
                override suspend fun read() = "musicid=1; musickey=TEST_SECRET"
                override fun credentialUpdates() = flowOf("musicid=1; musickey=TEST_SECRET")
                override suspend fun write(credential: String) =
                    Result.failure<Unit>(IllegalStateException("Could not restore musickey=TEST_SECRET"))
                override suspend fun replace(expected: String, credential: String) = Result.success(false)
            }
            val host = page(settings, slot)
            host.signOut(MusicProviderId.QQ)
            runCurrent()
            assertEquals(strings.signOutFailed(strings.credentialSaveFailed), host.message.value)
            assertFalse(host.message.value.orEmpty().contains("TEST_SECRET"))
        } finally { Preferences.userRoot().node(node).removeNode() }
    }
}
