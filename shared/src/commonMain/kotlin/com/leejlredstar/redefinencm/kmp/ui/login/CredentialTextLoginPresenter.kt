package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.leejlredstar.redefinencm.kmp.data.auth.CredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.LoginHost
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethodException
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.UtilityFeedback
import com.leejlredstar.redefinencm.kmp.ui.component.inMemoryViewModel
import com.leejlredstar.redefinencm.kmp.ui.component.rememberEditorFocus
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** One secure, prefilled credential field. Deliberately saving an empty value clears it. */
class CredentialTextLoginPresenter : LoginMethodPresenter {
    override fun supports(method: LoginMethod): Boolean = method is CredentialTextLoginMethod

    override fun sectionTitle(methods: List<LoginMethod>): String =
        methods.singleOrNull()?.displayName ?: strings.enterManually

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content(method: LoginMethod, host: LoginHost, isActive: Boolean) {
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
                    reveal = false
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
                .onFailure { failure ->
                    // Only method-owned validation copy; never include pasted values from exceptions.
                    draft.notice = ((failure as? LoginMethodException)?.message ?: strings.credentialNotRecognized) to true
                }
        }

        val error = draft.notice?.takeIf { it.second }?.first
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedSecureTextField(
                state = draft.field,
                labelPosition = TextFieldLabelPosition.Above(),
                label = { Text(textMethod.fieldLabel) },
                supportingText = { LoginSupportingText(error ?: textMethod.supportingText, isError = error != null) },
                trailingIcon = if (error != null) ({ Icon(AppIcons.ErrorOutline, contentDescription = null) }) else null,
                isError = error != null,
                textObfuscationMode = if (reveal) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                    showKeyboardOnFocus = editor.showKeyboardOnFocus,
                ),
                onKeyboardAction = { saveCredential() },
                shape = MaterialTheme.shapes.small,
                modifier = editor.modifier.fillMaxWidth(),
            )
            draft.notice?.takeUnless { it.second }?.let { (text, _) -> UtilityFeedback(text, icon = AppIcons.Check) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) strings.hide else strings.show) }
                LoginPrimaryButton(strings.save, ::saveCredential)
            }
        }
    }
}

private class CredentialDraft(initial: String) : ViewModel() {
    val field = TextFieldState(initial)
    var lastStored = initial
    var notice by mutableStateOf<Pair<String, Boolean>?>(null)
}
