package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.provider.LibraryAggregationMode
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.AccountCard
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsButton
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsExpanderRow
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsSectionLabel
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsSwitch
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsTextField
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.viewmodel.AccountsViewModel
import org.koin.compose.koinInject

/**
 * Every account and every provider's configuration: one group per provider, then how several
 * providers are shown together, then the device-local account with its own switch.
 *
 * A provider's group holds its switch (when it can be turned off), its account, its backend
 * address with a check, and its pasted credential folded away as an advanced option. The groups
 * come from the provider registrations, so a new provider appears here by registration; the page
 * itself names no provider.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountsScreen(
    scaffoldPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenLogin: (MusicProviderId) -> Unit,
    viewModel: AccountsViewModel = koinInject(),
) {
    val palette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)
    val accounts by viewModel.accounts.collectAsState()
    val localName by viewModel.localName.collectAsState()
    val localEnabled by viewModel.localEnabled.collectAsState()
    val aggregationMode by viewModel.aggregationMode.collectAsState()
    val mergeSameSongs by viewModel.mergeSameSongs.collectAsState()
    val message by viewModel.message.collectAsState()

    // Settings can change behind this page, for example through a backup import, so each
    // visit reads them afresh.
    LaunchedEffect(viewModel) { viewModel.reload() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    var logoutConfirmationFor by remember { mutableStateOf<MusicProviderId?>(null) }
    var renamingLocal by remember { mutableStateOf(false) }
    val expandedCredentialFields = remember { mutableStateMapOf<MusicProviderId, Boolean>() }
    val credentialDrafts = remember { mutableStateMapOf<MusicProviderId, String>() }
    val serverDrafts = remember { mutableStateMapOf<MusicProviderId, String>() }

    val appBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    ExpressivePage(
        accentPalette = palette,
        maxContentWidth = ExpressiveLayout.ReadingContentMaxWidth,
    ) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.nestedScroll(appBarScrollBehavior.nestedScrollConnection),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    LargeFlexibleTopAppBar(
                        title = { Text(strings.accountsAndServices) },
                        subtitle = { Text(strings.accountsSubtitle) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(AppIcons.ArrowBack, contentDescription = strings.back)
                            }
                        },
                        scrollBehavior = appBarScrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = palette.pageStart,
                            titleContentColor = palette.onPageStart,
                        ),
                    )
                },
            ) { appBarPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = appBarPadding.calculateTopPadding(),
                            bottom = scaffoldPadding.calculateBottomPadding(),
                        ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        accounts.forEach { account ->
                            val provider = account.provider
                            val descriptor = account.descriptor
                            val switch = descriptor.enabledSetting
                            val server = descriptor.server
                            val textMethod = account.textMethod
                            val credentialExpanded = expandedCredentialFields[provider] == true

                            // The rows of this provider's group, so each takes its place in the
                            // connected shape whatever is shown.
                            val rows = buildList {
                                if (switch != null) add(AccountRow.Switch)
                                if (account.enabled) {
                                    add(AccountRow.Account)
                                    if (server != null) add(AccountRow.Server)
                                    if (server?.check != null) add(AccountRow.ServerCheck)
                                    if (textMethod != null) {
                                        add(AccountRow.CredentialExpander)
                                        if (credentialExpanded) add(AccountRow.Credential)
                                    }
                                }
                            }

                            SettingsSectionLabel(provider.displayName, palette)
                            if (switch != null) {
                                SettingsSwitch(
                                    checked = account.enabled,
                                    label = switch.label,
                                    accentPalette = palette,
                                    index = rows.indexOf(AccountRow.Switch),
                                    count = rows.size,
                                    supportingText = switch.supportingText,
                                ) { enabled -> viewModel.setEnabled(provider, enabled) }
                            }
                            if (account.enabled) {
                                AccountCard(
                                    loggedIn = account.signedIn,
                                    nickname = account.identity?.name,
                                    avatarUrl = account.identity?.avatarUrl,
                                    providerLabel = descriptor.accountLabel,
                                    signedOutHint = descriptor.signInUnavailableReason ?: descriptor.signedOutHint,
                                    accentPalette = palette,
                                    onLogin = if (account.registration.canSignIn) {
                                        { onOpenLogin(provider) }
                                    } else {
                                        null
                                    },
                                    onLogout = { logoutConfirmationFor = provider },
                                    index = rows.indexOf(AccountRow.Account),
                                    count = rows.size,
                                )
                                if (server != null) {
                                    SettingsTextField(
                                        value = serverDrafts[provider] ?: account.server.orEmpty(),
                                        label = server.label,
                                        accentPalette = palette,
                                        index = rows.indexOf(AccountRow.Server),
                                        count = rows.size,
                                        supportingText = strings.serverAddressHint(server.appliesWhen),
                                        onDraftChange = { serverDrafts[provider] = it },
                                        onCommit = { raw ->
                                            serverDrafts.remove(provider)
                                            viewModel.saveServer(provider, raw)
                                        },
                                    )
                                    if (server.check != null) {
                                        SettingsButton(
                                            label = if (account.checkingServer) strings.checking else strings.checkAddress,
                                            accentPalette = palette,
                                            index = rows.indexOf(AccountRow.ServerCheck),
                                            count = rows.size,
                                        ) {
                                            viewModel.checkServer(
                                                provider,
                                                serverDrafts[provider] ?: account.server.orEmpty(),
                                            )
                                        }
                                    }
                                }
                                if (textMethod != null) {
                                    SettingsExpanderRow(
                                        label = strings.enterFieldManually(textMethod.fieldLabel),
                                        supportingText = strings.advancedHint(textMethod.supportingText),
                                        expanded = credentialExpanded,
                                        accentPalette = palette,
                                        index = rows.indexOf(AccountRow.CredentialExpander),
                                        count = rows.size,
                                        onToggle = { expandedCredentialFields[provider] = !credentialExpanded },
                                    )
                                    if (credentialExpanded) {
                                        // Obscured, and never part of the settings backup.
                                        SettingsTextField(
                                            value = credentialDrafts[provider] ?: account.credential,
                                            label = textMethod.fieldLabel,
                                            obscureText = true,
                                            accentPalette = palette,
                                            index = rows.indexOf(AccountRow.Credential),
                                            count = rows.size,
                                            onDraftChange = { credentialDrafts[provider] = it },
                                            onCommit = { raw ->
                                                credentialDrafts.remove(provider)
                                                viewModel.saveCredential(provider, raw)
                                            },
                                        )
                                    }
                                }
                                account.serverCheck?.let { check ->
                                    ServerCheckLine(
                                        message = check.message,
                                        reachable = check.reachable,
                                        palette = palette,
                                    )
                                }
                            }
                        }

                        // Only worth choosing once there is more than one provider to show.
                        if (accounts.count { it.enabled } > 1) {
                            val merged = aggregationMode == LibraryAggregationMode.MERGED
                            SettingsSectionLabel(strings.multipleServices, palette)
                            SettingsSwitch(
                                checked = !merged,
                                label = strings.groupByService,
                                accentPalette = palette,
                                index = 0,
                                count = if (merged) 2 else 1,
                                supportingText = strings.groupByServiceHint,
                            ) { perProvider ->
                                viewModel.setAggregationMode(
                                    if (perProvider) {
                                        LibraryAggregationMode.PER_PROVIDER
                                    } else {
                                        LibraryAggregationMode.MERGED
                                    },
                                )
                            }
                            if (merged) {
                                SettingsSwitch(
                                    checked = mergeSameSongs,
                                    label = strings.mergeSameSongs,
                                    accentPalette = palette,
                                    index = 1,
                                    count = 2,
                                    supportingText = strings.mergeSameSongsHint,
                                ) { merge -> viewModel.setMergeSameSongs(merge) }
                            }
                        }

                        // The local account's group has the shape of a provider's: its switch, and
                        // the account itself only while it is on.
                        SettingsSectionLabel(strings.local, palette)
                        SettingsSwitch(
                            checked = localEnabled,
                            label = strings.enableLocalAccount,
                            accentPalette = palette,
                            index = 0,
                            count = if (localEnabled) 2 else 1,
                            supportingText = strings.enableLocalAccountHint,
                        ) { enabled -> viewModel.setLocalEnabled(enabled) }
                        if (localEnabled) {
                            LocalAccountCard(
                                name = localName,
                                accentPalette = palette,
                                index = 1,
                                count = 2,
                                onRename = { renamingLocal = true },
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = scaffoldPadding.calculateBottomPadding()),
            )
        }
    }

    logoutConfirmationFor?.let { provider ->
        val warning = accounts.firstOrNull { it.provider == provider }?.descriptor?.logoutWarning.orEmpty()
        AlertDialog(
            onDismissRequest = { logoutConfirmationFor = null },
            icon = { Icon(AppIcons.Logout, contentDescription = null) },
            title = { Text(strings.signOutOfProvider(provider.displayName)) },
            text = { Text(warning) },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutConfirmationFor = null
                        credentialDrafts.remove(provider)
                        viewModel.signOut(provider)
                    },
                ) { Text(strings.signOutConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { logoutConfirmationFor = null }) { Text(strings.cancel) }
            },
        )
    }

    if (renamingLocal) {
        var draft by remember(localName) { mutableStateOf(localName) }
        AlertDialog(
            onDismissRequest = { renamingLocal = false },
            title = { Text(strings.localAccountName) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(strings.name) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renamingLocal = false
                        viewModel.renameLocal(draft)
                    },
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renamingLocal = false }) { Text(strings.cancel) }
            },
        )
    }
}

/** The rows a provider's group can hold, in the order they appear. */
private enum class AccountRow { Switch, Account, Server, ServerCheck, CredentialExpander, Credential }

/** The outcome of the last address check, beneath the provider's group. */
@Composable
private fun ServerCheckLine(
    message: String,
    reachable: Boolean,
    palette: ContentAccentPalette,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (reachable) palette.container else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (reachable) palette.onContainer else MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** The device-local account: a name, what it is for, and a way to rename it. */
@Composable
private fun LocalAccountCard(
    name: String,
    accentPalette: ContentAccentPalette,
    index: Int,
    count: Int,
    onRename: () -> Unit,
) {
    Surface(
        shape = connectedListItemShape(index = index, count = count),
        color = accentPalette.quietContainer,
        contentColor = accentPalette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ExpressiveLayout.ConnectedItemGap),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accentPalette.container,
                contentColor = accentPalette.onContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Person, contentDescription = null)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = strings.localAccountNoSignInNeeded,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRename) { Text(strings.rename) }
        }
    }
}
