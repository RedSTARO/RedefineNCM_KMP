package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.auth.ProviderServerSetting
import com.leejlredstar.redefinencm.kmp.data.provider.MusicProviderId
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.i18n.text
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.login.LoginNotice
import com.leejlredstar.redefinencm.kmp.ui.login.LoginPresenterRegistry
import com.leejlredstar.redefinencm.kmp.ui.login.LoginSection
import com.leejlredstar.redefinencm.kmp.ui.login.loginTextFieldColors
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.viewmodel.LoginViewModel
import com.leejlredstar.redefinencm.kmp.viewmodel.ServerNotice
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * Signs in to one provider with whatever login sources are registered for it.
 *
 * The page is a host and knows no login shape. It groups the provider's methods into sections by
 * the presenter that draws them, offers a chooser where a section holds more than one method, and
 * lets each presenter draw its method against the view model's
 * [com.leejlredstar.redefinencm.kmp.data.auth.LoginHost]. The only provider-specific things here,
 * the introduction and the backend address field, come from the provider's registered descriptor.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    provider: MusicProviderId = MusicProviderId.NETEASE,
    viewModel: LoginViewModel = koinInject(parameters = { parametersOf(provider) }),
    presenters: LoginPresenterRegistry = koinInject(),
) {
    val signedIn by viewModel.signedIn.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val sections = remember(viewModel, presenters) { presenters.sections(viewModel.methods) }
    val loginPalette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)

    DisposableEffect(viewModel) {
        onDispose { viewModel.onCleared() }
    }

    // Auto-close after a method signs in
    LaunchedEffect(finished) {
        if (finished) {
            kotlinx.coroutines.delay(1200)
            onBack()
        }
    }

    ExpressivePage(
        accentPalette = loginPalette,
        maxContentWidth = ExpressiveLayout.ReadingContentMaxWidth,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Header. No gradient of its own: ExpressivePage already paints one across the whole
            // window, and a second one bounded to the content column would end in a hard vertical
            // edge on wide windows.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(204.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = loginPalette.quietContainer.copy(alpha = 0.78f),
                        contentColor = loginPalette.onQuietContainer,
                    ) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = strings.back,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                // Say what this is and what signing in is for, and that it can wait: the page
                // opens by itself on the first launch. Someone already signed in came here to
                // switch accounts, and for them the back arrow says it.
                if (!signedIn) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(8.dp),
                    ) {
                        Text(strings.signInLater)
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = strings.signInToProvider(provider.displayName),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = loginPalette.onPageStart,
                    )
                    Text(
                        text = viewModel.descriptor.introduction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = loginPalette.secondaryOnPageStart,
                    )
                }
            }

            sections.forEachIndexed { index, section ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        color = loginPalette.onQuietContainer.copy(alpha = 0.16f),
                    )
                }
                LoginSectionCard(section, viewModel, loginPalette)
            }

            viewModel.descriptor.server?.let { setting ->
                val server by viewModel.server.collectAsState()
                val serverMessage by viewModel.serverMessage.collectAsState()
                BackendAddressCard(
                    setting = setting,
                    server = server,
                    message = serverMessage,
                    palette = loginPalette,
                    onSave = viewModel::saveServer,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** One presenter's card: its title, a chooser when it draws several methods, and the method. */
@Composable
private fun LoginSectionCard(
    section: LoginSection,
    viewModel: LoginViewModel,
    palette: ContentAccentPalette,
) {
    var selectedId by rememberSaveable(section.title) { mutableStateOf(section.methods.first().id) }
    val method = section.methods.firstOrNull { it.id == selectedId } ?: section.methods.first()

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = palette.quietContainer,
        contentColor = palette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                section.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            // More than one method of this shape for the provider; the chips pick which, and
            // switching disposes the previous method's state along with its content.
            if (section.methods.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    section.methods.forEach { candidate ->
                        val selected = candidate.id == method.id
                        FilterChip(
                            selected = selected,
                            onClick = { selectedId = candidate.id },
                            label = { Text(candidate.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                labelColor = palette.onQuietContainer,
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = palette.onAccent,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = palette.onQuietContainer.copy(alpha = 0.18f),
                                selectedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            key(method.id) {
                section.presenter.Content(method, viewModel, palette)
            }
        }
    }
}

/** The provider's self-hosted backend address; a provider concern, not a login method's. */
@Composable
private fun BackendAddressCard(
    setting: ProviderServerSetting,
    server: String,
    message: ServerNotice?,
    palette: ContentAccentPalette,
    onSave: (String) -> Unit,
) {
    var field by remember(server) { mutableStateOf(server) }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = palette.quietContainer,
        contentColor = palette.onQuietContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                strings.serverAddress,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text(setting.label) },
                supportingText = {
                    Text(strings.serverAddressResetHint, color = palette.secondaryOnQuietContainer)
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                colors = loginTextFieldColors(palette),
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = { onSave(field) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.container,
                    contentColor = palette.onContainer,
                ),
            ) {
                Text(strings.saveAddress)
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                LoginNotice(it.text.text, palette, isError = it.isError)
            }
        }
    }
}
