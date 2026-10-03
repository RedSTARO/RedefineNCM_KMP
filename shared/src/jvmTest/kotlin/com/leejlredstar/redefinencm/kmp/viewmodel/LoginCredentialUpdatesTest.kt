package com.leejlredstar.redefinencm.kmp.viewmodel

import com.leejlredstar.redefinencm.kmp.data.auth.CredentialStore
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodRegistry
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptor
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptorRegistry
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredentialSlot
import com.leejlredstar.redefinencm.kmp.data.auth.QQCredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.i18n.uiText
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginCredentialUpdatesTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun anOpenLoginPageFollowsRenewalAndSignOutWithoutFinishingItself() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val nodeName = "login-updates-test-${UUID.randomUUID()}"
        var host: LoginViewModel? = null
        try {
            val settings = PlatformSettings(nodeName)
            val slot = QQCredentialSlot(settings)
            val original = "musicid=1; musickey=old"
            val renewed = "musicid=1; musickey=new"
            slot.write(original).getOrThrow()
            val descriptor = ProviderLoginDescriptor(
                provider = MusicProviderId.QQ,
                introductionText = uiText(""),
                accountLabelText = uiText(""),
                signedOutHintText = uiText(""),
                logoutWarningText = uiText(""),
                server = null,
            )
            val page = LoginViewModel(
                MusicProviderId.QQ,
                LoginMethodRegistry(listOf(QQCredentialTextLoginMethod())),
                ProviderLoginDescriptorRegistry(listOf(descriptor)),
                CredentialStore(listOf(slot)),
                settings,
            ).also { host = it }
            assertEquals(original, page.storedCredential.value)

            assertTrue(slot.replace(original, renewed).getOrThrow())
            assertEquals(renewed, page.storedCredential.value)
            assertTrue(page.signedIn.value)
            assertFalse(page.finished.value)

            slot.clear().getOrThrow()
            assertEquals("", page.storedCredential.value)
            assertFalse(page.signedIn.value)
            assertFalse(page.finished.value)
        } finally {
            host?.onCleared()
            Preferences.userRoot().node(nodeName).removeNode()
            Dispatchers.resetMain()
        }
    }
}
