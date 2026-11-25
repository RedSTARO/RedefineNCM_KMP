package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.auth.LoginHost
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.PhoneCodeLoginFlow
import com.leejlredstar.redefinencm.kmp.data.auth.PhoneCodeLoginMethod
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.UtilityFeedback
import com.leejlredstar.redefinencm.kmp.ui.component.inMemoryViewModel
import com.leejlredstar.redefinencm.kmp.ui.component.rememberEditorFocus
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons

/**
 * Draws any [PhoneCodeLoginMethod]: a phone field with a send button that counts down after a
 * send, a code field, and a sign-in button. The state is a [PhoneCodeLoginFlow] per method.
 */
class PhoneCodeLoginPresenter : LoginMethodPresenter {
    override fun supports(method: LoginMethod): Boolean = method is PhoneCodeLoginMethod

    override fun sectionTitle(methods: List<LoginMethod>): String =
        methods.singleOrNull()?.displayName ?: strings.phoneVerificationCode

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun Content(method: LoginMethod, host: LoginHost, isActive: Boolean) {
        val phoneMethod = method as PhoneCodeLoginMethod
        val session = inMemoryViewModel(key = method.id) { SmsLoginSession(PhoneCodeLoginFlow(phoneMethod, host)) }
        val flow = session.flow
        val busy by flow.busy.collectAsState()
        val codeSent by flow.codeSent.collectAsState()
        val resendIn by flow.resendInSeconds.collectAsState()
        val message by flow.message.collectAsState()
        val error by flow.error.collectAsState()
        val success by flow.success.collectAsState()

        val phoneEnabled = !busy && !success
        val codeEnabled = codeSent && !busy && !success
        val phoneFocus = rememberEditorFocus(isActive, enabled = phoneEnabled)
        val codeFocus = rememberEditorFocus(isActive, enabled = codeEnabled)
        LaunchedEffect(session) {
            // The first snapshot can already contain a draft from the last Activity. Comparing
            // digits mirrors the flow's challenge invalidation without touching an intact code.
            snapshotFlow { session.phone.text.toString() }.collect { raw ->
                if (raw.filter(Char::isDigit) != flow.phone.value.filter(Char::isDigit)) {
                    session.code.setTextAndPlaceCursorAtEnd("")
                }
                if (raw != flow.phone.value) flow.setPhone(raw)
            }
        }
        LaunchedEffect(session) {
            snapshotFlow { session.code.text.toString() }.collect { raw ->
                if (raw.trim() != flow.code.value) flow.setCode(raw)
            }
        }

        fun sendCode() {
            flow.setPhone(session.phone.text.toString())
            flow.sendCode()
        }
        fun verify() {
            flow.setCode(session.code.text.toString())
            flow.verify()
        }

        val sendEnabled = !busy && resendIn == 0 && !success
        val sendLabel = when {
            resendIn > 0 -> strings.resendIn(resendIn)
            codeSent -> strings.resend
            else -> strings.sendVerificationCode
        }
        val phoneError = error.takeIf { !codeSent && it.isNotEmpty() }
        val codeError = error.takeIf { codeSent && it.isNotEmpty() }
        // The fields stay mounted through the SMS steps and window changes. Once a code was
        // sent, emphasis moves from Send to Sign in; Resend stays alongside the phone.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state = session.phone,
                labelPosition = TextFieldLabelPosition.Above(),
                label = { Text(strings.phoneNumber) },
                supportingText = { LoginSupportingText(phoneError ?: flow.phoneHint, isError = phoneError != null) },
                trailingIcon = if (phoneError != null) ({ Icon(AppIcons.ErrorOutline, contentDescription = null) }) else null,
                isError = phoneError != null,
                lineLimits = TextFieldLineLimits.SingleLine,
                enabled = phoneEnabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Go,
                    showKeyboardOnFocus = phoneFocus.showKeyboardOnFocus,
                ),
                onKeyboardAction = { if (sendEnabled) sendCode() },
                shape = MaterialTheme.shapes.small,
                modifier = phoneFocus.modifier.fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                if (!codeSent) {
                    LoginPrimaryButton(sendLabel, ::sendCode, enabled = sendEnabled)
                } else {
                    OutlinedButton(onClick = ::sendCode, enabled = sendEnabled, shape = MaterialTheme.shapes.medium) {
                        Text(sendLabel)
                    }
                }
            }
            if (message.isNotEmpty() && error.isEmpty()) {
                UtilityFeedback(message, icon = if (success || codeSent) AppIcons.Check else AppIcons.Info)
            }
            OutlinedSecureTextField(
                state = session.code,
                labelPosition = TextFieldLabelPosition.Above(),
                label = { Text(strings.verificationCode) },
                supportingText = codeError?.let { line -> { LoginSupportingText(line, isError = true) } },
                trailingIcon = if (codeError != null) ({ Icon(AppIcons.ErrorOutline, contentDescription = null) }) else null,
                isError = codeError != null,
                enabled = codeEnabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    showKeyboardOnFocus = codeFocus.showKeyboardOnFocus,
                ),
                onKeyboardAction = { verify() },
                shape = MaterialTheme.shapes.small,
                modifier = codeFocus.modifier.fillMaxWidth(),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                LoginPrimaryButton(
                    label = if (success) strings.signedIn else strings.signIn,
                    onClick = ::verify,
                    enabled = codeEnabled && session.code.text.isNotBlank(),
                )
            }
        }
    }
}

/** Phone number, SMS code and active challenge survive recreation, not process death. */
private class SmsLoginSession(val flow: PhoneCodeLoginFlow) : androidx.lifecycle.ViewModel() {
    val phone = TextFieldState()
    val code = TextFieldState()

    override fun onCleared() { flow.cancel() }
}
