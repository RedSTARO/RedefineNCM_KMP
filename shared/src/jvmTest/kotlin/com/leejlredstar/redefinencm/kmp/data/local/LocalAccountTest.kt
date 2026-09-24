package com.leejlredstar.redefinencm.kmp.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.leejlredstar.redefinencm.kmp.data.db.AppDatabase
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderRegistry
import com.leejlredstar.redefinencm.kmp.player.MediaInfo
import com.leejlredstar.redefinencm.kmp.util.DEFAULT_SETTINGS_NODE
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.LocalLibraryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The local account can be switched off: the switch is stored, reaches whoever follows it, and
 * closes the ways into the library without touching what the library holds.
 */
class LocalAccountTest {
    private val nodeName = "$DEFAULT_SETTINGS_NODE.test.local-account"
    private val settings = run {
        Preferences.userRoot().node(nodeName).removeNode()
        PlatformSettings(nodeName = nodeName)
    }
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
    private val store = LocalLibraryStore(AppDatabase(driver))

    private val song = MediaInfo(id = "qq:0039MnYb0qxYhV", title = "晴天", artist = "周杰伦")

    @AfterTest
    fun cleanUp() {
        driver.close()
        Preferences.userRoot().node(nodeName).removeNode()
    }

    @Test
    fun theAccountIsOnUntilSwitchedOffAndTheSwitchIsStored() = runTest {
        val account = LocalAccount(settings)
        assertTrue(account.enabledUpdates().first())

        account.setEnabled(false).getOrThrow()
        assertFalse(account.enabledUpdates().first())
        // The next launch reads it off too.
        assertFalse(LocalAccount(PlatformSettings(nodeName = nodeName)).enabledUpdates().first())
    }

    @Test
    fun aCollectorAlreadyListeningSeesEachSwitch() = runTest {
        val account = LocalAccount(settings)
        val seen = mutableListOf<Boolean>()
        val listening = launch(UnconfinedTestDispatcher(testScheduler)) {
            account.enabledUpdates().collect { seen += it }
        }
        account.setEnabled(false).getOrThrow()
        account.setEnabled(true).getOrThrow()
        listening.cancel()
        assertEquals(listOf(true, false, true), seen)
    }

    @Test
    fun aSwitchWrittenAroundTheAccountIsPickedUpOnReload() = runTest {
        val account = LocalAccount(settings)
        assertTrue(account.enabledUpdates().first())

        // Importing a backup writes settings directly; the accounts view model's reload follows.
        settings.setBoolean(SettingKeys.LOCAL_ACCOUNT_ENABLED, false)
        assertTrue(account.enabledUpdates().first())
        account.reload()
        assertFalse(account.enabledUpdates().first())
    }

    @Test
    fun switchedOffNothingIsAddedAnOpenDialogClosesAndTheLibraryIsKept() = runBlocking {
        val playlist = store.createPlaylist("混合", listOf(song)).getOrThrow()
        val account = LocalAccount(settings)
        val viewModel = LocalLibraryViewModel(store, MusicProviderRegistry(emptyList(), settings), account)
        withTimeout(5_000) { viewModel.enabled.first { it } }

        viewModel.requestAddition(listOf(song))
        assertNotNull(viewModel.pendingAddition.value)

        account.setEnabled(false).getOrThrow()
        withTimeout(5_000) { viewModel.pendingAddition.first { it == null } }
        withTimeout(5_000) { viewModel.enabled.first { !it } }
        viewModel.requestAddition(listOf(song))
        assertNull(viewModel.pendingAddition.value)

        // Off hides the library; it does not empty it.
        assertEquals(listOf(song.id), store.snapshot().playlist(playlist.id)!!.tracks.map { it.toMediaInfo()!!.id })
    }
}
