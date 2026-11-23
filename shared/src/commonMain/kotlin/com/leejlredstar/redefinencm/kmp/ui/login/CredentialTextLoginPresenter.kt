package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.auth.CredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.LoginHost
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethod
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.inMemoryViewModel
import com.leejlredstar.redefinencm.kmp.ui.component.rememberEditorFocus
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Draws any [CredentialTextLoginMethod]: one obscured field, prefilled with what is stored, and a
 * save that runs the method's validation before the host persists. Saving an empty field signs
 * out, which is why the field is prefilled: clearing it is a deliberate act.
 */
class CredentialTextLoginPresenter : LoginMethodPresenter {
    override fun supports(method: LoginMethod): Boolean = method is CredentialTextLoginMethod

    override fun sectionTitle(methods: List<LoginMethod>): String =
        methods.singleOrNull()?.displayName ?: strings.enterManually

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(method: LoginMethod, host: LoginHost, palette: ContentAccentPalette, isActive: Boolean) {
        val textMethod = method as CredentialTextLoginMethod
        val stored by host.storedCredential.collectAsState()
        val draft = inMemoryViewModel(key = method.id) { CredentialDraft(stored) }
        LaunchedEffect(stored) {
            // A background renewal updates an untouched field, but must not erase a user's edit.
            if (stored != draft.lastStored && draft.field.text.toString() == draft.lastStored) {
                draft.field.setTextAndPlaceCursorAtEnd(stored)
            }
            draft.lastStored = stored
        }
        // A configuration change keeps the text but always re-obscures the credential.
        var reveal by remember { mutableStateOf(false) }
        LaunchedEffect(draft) {
            snapshotFlow { draft.field.text.toString() }.drop(1).collect { draft.notice = null }
        }
        val editor = rememberEditorFocus(isActive)
        val focusManager = LocalFocusManager.current

        fun saveCredential() {
            draft.notice = null
            val fieldText = draft.field.text.toString()
            val candidate = if (fieldText == draft.lastStored) host.storedCredential.value else fieldText
            textMethod.normalize(candidate)
                .onSuccess { credential ->
                    focusManager.clearFocus()
                    host.scope.launch {
                        host.persist(credential)
                            .onSuccess {
                                draft.notice = (if (credential.isEmpty()) strings.credentialCleared else strings.credentialSavedInUse(textMethod.fieldLabel)) to false
                                if (credential.isNotEmpty()) host.onSignedIn()
                            }
                            .onFailure { draft.notice = strings.credentialSaveFailed to true }
                    }
                }
                .onFailure { failure -> draft.notice = (failure.message ?: strings.credentialNotRecognized) to true }
        }

        Column {
            OutlinedSecureTextField(
                state = draft.field,
                label = { Text(textMethod.fieldLabel) },
                supportingText = {
                    Text(textMethod.supportingText, color = palette.secondaryOnQuietContainer)
                },
                textObfuscationMode = if (reveal) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                trailingIcon = {
                    TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) strings.hide else strings.show) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                    showKeyboardOnFocus = editor.showKeyboardOnFocus,
                ),
                onKeyboardAction = { saveCredential() },
                shape = MaterialTheme.shapes.large,
                modifier = editor.modifier.fillMaxWidth(),
                colors = loginTextFieldColors(palette),
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = ::saveCredential,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.container,
                    contentColor = palette.onContainer,
                ),
            ) {
                Text(strings.save, style = MaterialTheme.typography.titleMedium)
            }
            draft.notice?.let { (text, isError) ->
                Spacer(Modifier.height(12.dp))
                LoginNotice(text, palette, isError = isError)
            }
        }
    }
}

private class CredentialDraft(initial: String) : ViewModel() {
    val field = TextFieldState(initial)
    var lastStored = initial
    var notice by mutableStateOf<Pair<String, Boolean>?>(null)
}
