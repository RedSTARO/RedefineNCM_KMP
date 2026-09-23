package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.auth.CredentialTextLoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.LoginHost
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethod
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import kotlinx.coroutines.launch

/**
 * Draws any [CredentialTextLoginMethod]: one obscured field, prefilled with what is stored, and a
 * save that runs the method's validation before the host persists. Saving an empty field signs
 * out, which is why the field is prefilled: clearing it is a deliberate act.
 */
class CredentialTextLoginPresenter : LoginMethodPresenter {
    override fun supports(method: LoginMethod): Boolean = method is CredentialTextLoginMethod

    override fun sectionTitle(methods: List<LoginMethod>): String =
        methods.singleOrNull()?.displayName ?: "手动输入"

    @Composable
    override fun Content(method: LoginMethod, host: LoginHost, palette: ContentAccentPalette) {
        val textMethod = method as CredentialTextLoginMethod
        val stored by host.storedCredential.collectAsState()
        var field by remember(stored) { mutableStateOf(stored) }
        var reveal by remember { mutableStateOf(false) }
        var notice by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

        Column {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it; notice = null },
                label = { Text(textMethod.fieldLabel) },
                supportingText = {
                    Text(textMethod.supportingText, color = palette.secondaryOnQuietContainer)
                },
                minLines = 3,
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "隐藏" else "显示") }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                colors = loginTextFieldColors(palette),
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = {
                    notice = null
                    textMethod.normalize(field)
                        .onSuccess { credential ->
                            host.scope.launch {
                                host.persist(credential)
                                    .onSuccess {
                                        notice = (if (credential.isEmpty()) "已清除凭证" else "已保存；${textMethod.fieldLabel}已生效") to false
                                        if (credential.isNotEmpty()) host.onSignedIn()
                                    }
                                    .onFailure { failure -> notice = (failure.message ?: "凭证保存失败") to true }
                            }
                        }
                        .onFailure { failure -> notice = (failure.message ?: "凭证无法识别") to true }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.container,
                    contentColor = palette.onContainer,
                ),
            ) {
                Text("保存", style = MaterialTheme.typography.titleMedium)
            }
            notice?.let { (text, isError) ->
                Spacer(Modifier.height(12.dp))
                LoginNotice(text, palette, isError = isError)
            }
        }
    }
}
