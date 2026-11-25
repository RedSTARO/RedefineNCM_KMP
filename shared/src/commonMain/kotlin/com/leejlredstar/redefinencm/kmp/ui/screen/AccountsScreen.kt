package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodException
import com.leejlredstar.redefinencm.kmp.data.provider.LibraryAggregationMode
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.AccountIdentityCard
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsExpanderRow
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsSectionLabel
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsSwitch
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsTextField
import com.leejlredstar.redefinencm.kmp.ui.component.UtilityFeedback
import com.leejlredstar.redefinencm.kmp.ui.component.UtilityReflow
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
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
    isActive: Boolean = true,
    viewModel: AccountsViewModel = koinInject(),
) {
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
    var renamingLocal by rememberSaveable { mutableStateOf(false) }
    var expandedCredentialFields by rememberSaveable { mutableStateOf(arrayListOf<String>()) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    ExpressivePage(contentWindowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top), maxContentWidth = 960.dp) {
        val gutter = LocalExpressivePageGutter.current
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { ExpressivePageBar(strings.accountsAndServices, onBack, scrollBehavior = scrollBehavior) },
            snackbarHost = {
                SnackbarHost(snackbarHostState, Modifier.padding(bottom = scaffoldPadding.calculateBottomPadding()))
            },
        ) { barPadding ->
            // Not a lazy list: a focused, unsubmitted credential/address editor must survive scroll
            // and a width change, with the same in-memory draft and focus target.
            Column(
                modifier = Modifier.fillMaxSize().padding(top = barPadding.calculateTopPadding())
                    .verticalScroll(rememberScrollState()).padding(horizontal = gutter)
                    .padding(top = 12.dp, bottom = scaffoldPadding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                ExpressivePageHeading(strings.accountsAndServices, supportingText = strings.accountsSubtitle)
                accounts.forEach { account ->
                    val provider = account.provider
                    key(provider.key) {
                        val credentialExpanded = provider.key in expandedCredentialFields
                        ProviderAccountGroup(
                            account = account,
                            credentialExpanded = credentialExpanded,
                            isActive = isActive,
                            viewModel = viewModel,
                            onOpenLogin = { onOpenLogin(provider) },
                            onConfirmLogout = { logoutConfirmationFor = provider },
                            onToggleCredential = {
                                expandedCredentialFields = ArrayList(
                                    if (credentialExpanded) expandedCredentialFields - provider.key
                                    else expandedCredentialFields + provider.key,
                                )
                            },
                        )
                    }
                }
                // Only worth choosing once there is more than one provider to show.
                if (accounts.count { it.enabled } > 1) {
                    val merged = aggregationMode == LibraryAggregationMode.MERGED
                    Column(Modifier.fillMaxWidth()) {
                        SettingsSectionLabel(strings.multipleServices)
                        SettingsSwitch(
                            checked = !merged,
                            label = strings.groupByService,
                            index = 0,
                            count = if (merged) 2 else 1,
                            supportingText = strings.groupByServiceHint,
                        ) { perProvider ->
                            viewModel.setAggregationMode(
                                if (perProvider) LibraryAggregationMode.PER_PROVIDER else LibraryAggregationMode.MERGED,
                            )
                        }
                        if (merged) {
                            SettingsSwitch(
                                checked = mergeSameSongs,
                                label = strings.mergeSameSongs,
                                index = 1,
                                count = 2,
                                supportingText = strings.mergeSameSongsHint,
                                onUpdate = viewModel::setMergeSameSongs,
                            )
                        }
                    }
                }
                // Like a provider, the local account keeps everything when switched off.
                Column(Modifier.fillMaxWidth()) {
                    SettingsSectionLabel(strings.local)
                    SettingsSwitch(
                        checked = localEnabled,
                        label = strings.enableLocalAccount,
                        index = 0,
                        count = if (localEnabled) 2 else 1,
                        supportingText = strings.enableLocalAccountHint,
                        onUpdate = viewModel::setLocalEnabled,
                    )
                    if (localEnabled) {
                        AccountIdentityCard(
                            title = localName,
                            supportingText = strings.localAccountNoSignInNeeded,
                            index = 1,
                            count = 2,
                        ) {
                            TextButton(onClick = { renamingLocal = true }) { Text(strings.rename) }
                        }
                    }
                }
            }
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
                        expandedCredentialFields = ArrayList(expandedCredentialFields - provider.key)
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
        TextEntryDialog(
            title = strings.localAccountName,
            label = strings.name,
            confirmLabel = strings.save,
            initialValue = localName,
            allowBlank = true,
            onDismiss = { renamingLocal = false },
            onConfirm = { name ->
                renamingLocal = false
                viewModel.renameLocal(name)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProviderAccountGroup(
    account: AccountsViewModel.ProviderAccount,
    credentialExpanded: Boolean,
    isActive: Boolean,
    viewModel: AccountsViewModel,
    onOpenLogin: () -> Unit,
    onConfirmLogout: () -> Unit,
    onToggleCredential: () -> Unit,
) {
    val provider = account.provider
    val descriptor = account.descriptor
    val switch = descriptor.enabledSetting
    val server = descriptor.server
    val textMethod = account.textMethod
    val configure = account.enabled && (server != null || textMethod != null)
    val renderIdentity: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            val count = if (switch != null && account.enabled) 2 else 1
            if (switch != null) {
                SettingsSwitch(
                    checked = account.enabled,
                    label = switch.label,
                    index = 0,
                    count = count,
                    supportingText = switch.supportingText,
                ) { enabled -> viewModel.setEnabled(provider, enabled) }
            }
            if (account.enabled) {
                val signedIn = account.signedIn
                val canSignIn = account.registration.canSignIn
                AccountIdentityCard(
                    title = if (signedIn) account.identity?.name?.takeIf(String::isNotBlank) ?: descriptor.accountLabel else strings.notSignedIn,
                    supportingText = when {
                        !signedIn -> descriptor.signInUnavailableReason ?: descriptor.signedOutHint
                        account.identity?.name.isNullOrBlank() -> provider.displayName
                        else -> descriptor.accountLabel
                    },
                    avatarUrl = account.identity?.avatarUrl.takeIf { signedIn },
                    signedIn = signedIn,
                    index = count - 1,
                    count = count,
                    actions = if (!signedIn && !canSignIn) null else {
                        {
                            if (canSignIn) {
                                FilledTonalButton(
                                    onClick = onOpenLogin,
                                    shapes = ButtonDefaults.shapes(shape = MaterialTheme.shapes.medium),
                                    contentPadding = ButtonDefaults.SmallContentPadding,
                                ) { Text(if (signedIn) strings.switchAccount else strings.signIn) }
                            }
                            if (signedIn) TextButton(onClick = onConfirmLogout) { Text(strings.signOut) }
                        }
                    },
                )
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        SettingsSectionLabel(provider.displayName)
        if (configure) {
            UtilityReflow(besideAt = 664.dp, leadingFraction = 0.42f, leading = renderIdentity) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (server != null) {
                        var addressEditor by remember { mutableStateOf<TextFieldState?>(null) }
                        ServiceEditorPanel {
                            SettingsTextField(
                                value = account.server.orEmpty(),
                                label = server.label,
                                supportingText = strings.serverAddressHint(server.appliesWhen),
                                isActive = isActive,
                                validate = { raw -> runCatching { server.normalize(raw) }.exceptionOrNull()?.let { strings.serverAddressInvalid } },
                                onCommit = { viewModel.saveServer(provider, it) },
                                onEditorReady = { addressEditor = it },
                            ) {
                                if (server.check != null) {
                                    TextButton(
                                        onClick = { viewModel.checkServer(provider, addressEditor?.text?.toString() ?: account.server.orEmpty()) },
                                        enabled = !account.checkingServer,
                                    ) { Text(if (account.checkingServer) strings.checking else strings.checkAddress) }
                                }
                            }
                            account.serverCheck?.let { check ->
                                UtilityFeedback(
                                    message = check.message,
                                    isError = !check.reachable,
                                    icon = if (check.reachable) AppIcons.Check else AppIcons.ErrorOutline,
                                )
                            }
                        }
                    }
                    if (textMethod != null) {
                        SettingsExpanderRow(
                            label = strings.enterFieldManually(textMethod.fieldLabel),
                            supportingText = strings.advancedHint(textMethod.supportingText),
                            expanded = credentialExpanded,
                            onToggle = onToggleCredential,
                        )
                        if (credentialExpanded) {
                            ServiceEditorPanel {
                                SettingsTextField(
                                    value = account.credential,
                                    label = textMethod.fieldLabel,
                                    obscureText = true,
                                    isActive = isActive,
                                    validate = { raw ->
                                        textMethod.normalize(raw).exceptionOrNull()?.let {
                                            (it as? LoginMethodException)?.message ?: strings.credentialNotRecognized
                                        }
                                    },
                                    onCommit = { viewModel.saveCredential(provider, it) },
                                )
                            }
                        }
                    }
                }
            }
        } else renderIdentity()
    }
}

@Composable
private fun ServiceEditorPanel(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}
