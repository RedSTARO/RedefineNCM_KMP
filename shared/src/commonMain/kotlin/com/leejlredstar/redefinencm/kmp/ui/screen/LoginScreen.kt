package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderServerSetting
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveFeatureIcon
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageBar
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePageHeading
import com.leejlredstar.redefinencm.kmp.ui.component.LocalExpressivePageGutter
import com.leejlredstar.redefinencm.kmp.ui.component.SettingsSectionLabel
import com.leejlredstar.redefinencm.kmp.ui.component.UtilityFeedback
import com.leejlredstar.redefinencm.kmp.ui.component.UtilityReflow
import com.leejlredstar.redefinencm.kmp.ui.component.inMemoryViewModel
import com.leejlredstar.redefinencm.kmp.ui.component.rememberEditorFocus
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.login.LoginPresenterRegistry
import com.leejlredstar.redefinencm.kmp.ui.login.LoginPrimaryButton
import com.leejlredstar.redefinencm.kmp.ui.login.LoginSection
import com.leejlredstar.redefinencm.kmp.ui.login.LoginSupportingText
import com.leejlredstar.redefinencm.kmp.viewmodel.LoginViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.ServerNotice
import kotlinx.coroutines.delay
import org.koin.compose.currentKoinScope
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Signs in to one provider with whatever login sources are registered for it.
 * The host knows no login shape; the descriptor owns the introduction and address, and each
 * presenter's state and persistence still run through LoginHost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    provider: MusicProviderId = MusicProviderId.NETEASE,
    isActive: Boolean = true,
    presenters: LoginPresenterRegistry = koinInject(),
) {
    val scope = currentKoinScope()
    val viewModel: LoginViewModel = inMemoryViewModel(key = provider.key) {
        scope.get(parameters = { parametersOf(provider) })
    }
    val signedIn by viewModel.signedIn.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val server by viewModel.server.collectAsState()
    val serverNotice by viewModel.serverMessage.collectAsState()
    val sections = remember(viewModel, presenters) { presenters.sections(viewModel.methods) }
    val descriptor = viewModel.descriptor
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(finished, isActive) {
        if (finished && isActive) {
            delay(1200)
            onBack()
        }
    }

    ExpressivePage(
        maxContentWidth = 1000.dp,
        contentWindowInsets = WindowInsets.statusBars.only(WindowInsetsSides.Top),
    ) {
        val gutter = LocalExpressivePageGutter.current
        Scaffold(
            modifier = Modifier.navigationBarsPadding().nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                ExpressivePageBar(strings.signIn, onBack, scrollBehavior = scrollBehavior) {
                    // The page opens automatically for a fresh install; signing in is optional.
                    if (!signedIn) TextButton(onClick = onBack) { Text(strings.signInLater) }
                }
            },
        ) { barPadding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(top = barPadding.calculateTopPadding())
                    .verticalScroll(rememberScrollState()).padding(horizontal = gutter)
                    .padding(top = 12.dp, bottom = 24.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                UtilityReflow(
                    besideAt = 680.dp,
                    leadingFraction = 0.36f,
                    leading = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExpressiveFeatureIcon(AppIcons.Person)
                            ExpressivePageHeading(
                                title = strings.signInToProvider(provider.displayName),
                                supportingText = descriptor.introduction,
                            )
                            descriptor.signInUnavailableReason?.let { UtilityFeedback(it) }
                        }
                    },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        sections.forEachIndexed { index, section ->
                            key(section.methods.first().id) {
                                LoginSectionCard(section, viewModel, isActive, emphasized = index == 0)
                            }
                        }
                        descriptor.server?.let { setting ->
                            BackendAddressCard(setting, server, serverNotice, viewModel::saveServer, isActive)
                        }
                    }
                }
            }
        }
    }
}

/** A chooser uses presenter-defined sections and keeps the selected method's composition keyed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoginSectionCard(
    section: LoginSection,
    viewModel: LoginViewModel,
    isActive: Boolean,
    emphasized: Boolean,
) {
    var selectedId by rememberSaveable { mutableStateOf(section.methods.first().id) }
    val method = section.methods.firstOrNull { it.id == selectedId } ?: section.methods.first()
    LoginPanel(
        title = section.title,
        color = if (emphasized) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (section.methods.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                section.methods.forEach { candidate ->
                    val selected = candidate.id == method.id
                    FilterChip(
                        selected = selected,
                        onClick = { selectedId = candidate.id },
                        label = { Text(candidate.displayName) },
                        leadingIcon = if (selected) ({ Icon(AppIcons.Check, contentDescription = null) }) else null,
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
        }
        key(method.id) { section.presenter.Content(method, viewModel, isActive) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackendAddressCard(
    setting: ProviderServerSetting,
    server: String,
    message: ServerNotice?,
    onSave: (String) -> Unit,
    isActive: Boolean,
) {
    val draft = inMemoryViewModel { BackendDraft(server) }
    LaunchedEffect(server) {
        if (server != draft.lastStored && draft.field.text.toString() == draft.lastStored) {
            draft.field.setTextAndPlaceCursorAtEnd(server)
        }
        draft.lastStored = server
    }
    val editor = rememberEditorFocus(isActive)
    val focusManager = LocalFocusManager.current
    val current = draft.field.text.toString()
    var submitted by remember { mutableStateOf<String?>(null) }
    val matchesSubmit = submitted == current || current == draft.lastStored
    val error = message?.takeIf { it.isError && matchesSubmit }?.text?.text
    val success = message?.takeIf { !it.isError && matchesSubmit }
    fun saveAddress() {
        val candidate = draft.field.text.toString()
        submitted = candidate
        onSave(candidate)
        if (runCatching { setting.normalize(candidate) }.isSuccess) focusManager.clearFocus()
    }
    // A settings-store exception can contain an address with user-info. Only display the known
    // validation text or a generic save failure at the inline field.
    val safeError = error?.let { if (it == strings.serverAddressInvalid) it else strings.settingSaveFailed }
    LoginPanel(strings.serverAddress, MaterialTheme.colorScheme.surfaceContainerLow) {
        OutlinedTextField(
            state = draft.field,
            labelPosition = TextFieldLabelPosition.Above(),
            label = { Text(setting.label) },
            supportingText = { LoginSupportingText(safeError ?: strings.serverAddressResetHint, isError = safeError != null) },
            trailingIcon = if (safeError != null) ({ Icon(AppIcons.ErrorOutline, contentDescription = null) }) else null,
            isError = safeError != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
                showKeyboardOnFocus = editor.showKeyboardOnFocus,
            ),
            onKeyboardAction = { saveAddress() },
            lineLimits = TextFieldLineLimits.SingleLine,
            shape = MaterialTheme.shapes.small,
            modifier = editor.modifier.fillMaxWidth(),
        )
        if (success != null) UtilityFeedback(success.text.text, icon = AppIcons.Check)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            LoginPrimaryButton(strings.saveAddress, ::saveAddress, primary = false)
        }
    }
}

@Composable
private fun LoginPanel(title: String, color: Color, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = color, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsSectionLabel(title)
            content()
        }
    }
}

/** Only memory: an address can contain credential-bearing user-info. */
private class BackendDraft(initial: String) : ViewModel() {
    val field = TextFieldState(initial)
    var lastStored = initial
}
