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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.auth.CredentialStore
import com.leejlredstar.redefinencm.kmp.data.auth.LocalAccount
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodRegistry
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderLoginDescriptorRegistry
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Every account the app can hold: one card per provider, then the device-local account.
 *
 * The provider cards come from the registered login descriptors, so a new provider appears here
 * by registration. Signing in opens the provider's login page; signing out goes through its
 * credential slot, which applies that provider's change rules; and where a provider has a
 * pasted-credential method, the raw credential is folded away beneath its card as an advanced
 * option. The local account has nothing to sign in to — it names whatever stays on this device.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountsScreen(
    scaffoldPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenLogin: (MusicProviderId) -> Unit,
    mainViewModel: MainViewModel = koinInject(),
    loginMethods: LoginMethodRegistry = koinInject(),
    loginDescriptors: ProviderLoginDescriptorRegistry = koinInject(),
    credentials: CredentialStore = koinInject(),
    localAccount: LocalAccount = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val palette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)
    val userDetail by mainViewModel.userDetail.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        message = null
    }

    // What is stored for each provider, re-read whenever something on this page changes it.
    var reloadGeneration by remember { mutableIntStateOf(0) }
    val storedCredentials = remember { mutableStateMapOf<MusicProviderId, String>() }
    var localName by remember { mutableStateOf(LocalAccount.DefaultName) }
    LaunchedEffect(reloadGeneration) {
        loginDescriptors.all.forEach { descriptor ->
            storedCredentials[descriptor.provider] = credentials[descriptor.provider]?.read().orEmpty()
        }
        localName = localAccount.name()
    }

    var logoutConfirmationFor by remember { mutableStateOf<MusicProviderId?>(null) }
    var renamingLocal by remember { mutableStateOf(false) }
    val expandedCredentialFields = remember { mutableStateMapOf<MusicProviderId, Boolean>() }
    val credentialDrafts = remember { mutableStateMapOf<MusicProviderId, String>() }

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
                        title = { Text("账号") },
                        subtitle = { Text("每个平台各一个账号；本地账号保存在此设备") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(AppIcons.ArrowBack, contentDescription = "返回")
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
                        loginDescriptors.all.forEach { descriptor ->
                            val provider = descriptor.provider
                            val stored = storedCredentials[provider].orEmpty()
                            val textMethod = remember(loginMethods, provider) { loginMethods.textMethod(provider) }
                            // NetEase's identity comes from the account the app resolves at
                            // startup — the legacy account system, with a nickname and avatar the
                            // cookie does not carry. Every other provider's comes from what its
                            // credential says about itself.
                            val isNetease = provider == MusicProviderId.NETEASE

                            SettingsSectionLabel(provider.displayName, palette)
                            AccountCard(
                                loggedIn = stored.isNotBlank(),
                                nickname = if (isNetease) userDetail?.profile?.nickname else descriptor.accountName(stored),
                                avatarUrl = if (isNetease) userDetail?.profile?.avatarUrl else null,
                                providerLabel = descriptor.accountLabel,
                                signedOutHint = descriptor.signedOutHint,
                                accentPalette = palette,
                                onLogin = { onOpenLogin(provider) },
                                onLogout = { logoutConfirmationFor = provider },
                                shapeCount = if (textMethod != null) 2 else 1,
                            )
                            if (textMethod != null) {
                                val expanded = expandedCredentialFields[provider] == true
                                SettingsExpanderRow(
                                    label = "手动填写${textMethod.fieldLabel}",
                                    supportingText = "高级：${textMethod.supportingText}",
                                    expanded = expanded,
                                    accentPalette = palette,
                                    onToggle = { expandedCredentialFields[provider] = !expanded },
                                )
                                if (expanded) {
                                    // Obscured, and never part of the settings backup.
                                    SettingsTextField(
                                        value = credentialDrafts[provider] ?: stored,
                                        label = textMethod.fieldLabel,
                                        obscureText = true,
                                        accentPalette = palette,
                                        index = 1,
                                        count = 2,
                                        onDraftChange = { credentialDrafts[provider] = it },
                                        onCommit = { raw ->
                                            textMethod.normalize(raw)
                                                .onSuccess { normalized ->
                                                    scope.launch {
                                                        credentials.require(provider).write(normalized)
                                                            .onSuccess {
                                                                credentialDrafts.remove(provider)
                                                                reloadGeneration += 1
                                                                message = if (normalized.isEmpty()) {
                                                                    "已清除${provider.displayName}的凭证"
                                                                } else {
                                                                    "已保存${provider.displayName}的凭证"
                                                                }
                                                            }
                                                            .onFailure { failure ->
                                                                message = failure.message ?: "凭证保存失败"
                                                            }
                                                    }
                                                }
                                                .onFailure { failure ->
                                                    message = failure.message ?: "凭证无法识别"
                                                }
                                        },
                                    )
                                }
                            }
                        }

                        SettingsSectionLabel("本地", palette)
                        LocalAccountCard(
                            name = localName,
                            accentPalette = palette,
                            onRename = { renamingLocal = true },
                        )
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
        AlertDialog(
            onDismissRequest = { logoutConfirmationFor = null },
            icon = { Icon(AppIcons.Logout, contentDescription = null) },
            title = { Text("退出${provider.displayName}登录？") },
            text = { Text(loginDescriptors.require(provider).logoutWarning) },
            confirmButton = {
                TextButton(
                    onClick = {
                        logoutConfirmationFor = null
                        scope.launch {
                            credentials.require(provider).clear()
                                .onSuccess {
                                    credentialDrafts.remove(provider)
                                    reloadGeneration += 1
                                    message = "已退出${provider.displayName}"
                                }
                                .onFailure { failure ->
                                    reloadGeneration += 1
                                    message = "退出失败：${failure.message ?: "未知错误"}"
                                }
                        }
                    },
                ) { Text("退出登录") }
            },
            dismissButton = {
                TextButton(onClick = { logoutConfirmationFor = null }) { Text("取消") }
            },
        )
    }

    if (renamingLocal) {
        var draft by remember(localName) { mutableStateOf(localName) }
        AlertDialog(
            onDismissRequest = { renamingLocal = false },
            title = { Text("本地账号名称") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renamingLocal = false
                        scope.launch {
                            localAccount.rename(draft)
                                .onSuccess {
                                    reloadGeneration += 1
                                    message = "已重命名本地账号"
                                }
                                .onFailure { failure ->
                                    message = "保存失败：${failure.message ?: "未知错误"}"
                                }
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renamingLocal = false }) { Text("取消") }
            },
        )
    }
}

/** The device-local account: a name, what it is for, and a way to rename it. */
@Composable
private fun LocalAccountCard(
    name: String,
    accentPalette: ContentAccentPalette,
    onRename: () -> Unit,
) {
    Surface(
        shape = connectedListItemShape(index = 0, count = 1),
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
                    text = "无需登录；本地歌单等只保存在此设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = accentPalette.secondaryOnQuietContainer,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRename) { Text("重命名") }
        }
    }
}
