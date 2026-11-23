package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.leejlredstar.redefinencm.kmp.ui.component.inMemoryViewModel
import com.leejlredstar.redefinencm.kmp.ui.component.rememberEditorFocus
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette

/**
 * Draws any [PhoneCodeLoginMethod]: a phone field with a send button that counts down after a
 * send, a code field, and a sign-in button. The state is a [PhoneCodeLoginFlow] per method.
 */
class PhoneCodeLoginPresenter : LoginMethodPresenter {
    override fun supports(method: LoginMethod): Boolean = method is PhoneCodeLoginMethod

    override fun sectionTitle(methods: List<LoginMethod>): String =
        methods.singleOrNull()?.displayName ?: strings.phoneVerificationCode

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content(method: LoginMethod, host: LoginHost, palette: ContentAccentPalette, isActive: Boolean) {
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

        // Keep the field at one composition position: switching a Row into a Column would
        // destroy its focus target and reconnect the IME during a width-class change.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                state = session.phone,
                label = { Text(strings.phoneNumber) },
                supportingText = { Text(flow.phoneHint, color = palette.secondaryOnQuietContainer) },
                lineLimits = TextFieldLineLimits.SingleLine,
                enabled = phoneEnabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Go,
                    showKeyboardOnFocus = phoneFocus.showKeyboardOnFocus,
                ),
                onKeyboardAction = { sendCode() },
                shape = MaterialTheme.shapes.large,
                modifier = phoneFocus.modifier.fillMaxWidth(),
                colors = loginTextFieldColors(palette),
            )
            OutlinedButton(
                onClick = ::sendCode,
                enabled = !busy && resendIn == 0 && !success,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
            ) {
                Text(
                    when {
                        resendIn > 0 -> strings.resendIn(resendIn)
                        codeSent -> strings.resend
                        else -> strings.sendVerificationCode
                    },
                )
            }
            OutlinedSecureTextField(
                state = session.code,
                label = { Text(strings.verificationCode) },
                enabled = codeEnabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                    showKeyboardOnFocus = codeFocus.showKeyboardOnFocus,
                ),
                onKeyboardAction = { verify() },
                shape = MaterialTheme.shapes.large,
                modifier = codeFocus.modifier.fillMaxWidth(),
                colors = loginTextFieldColors(palette),
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = ::verify,
                enabled = codeEnabled && session.code.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.container,
                    contentColor = palette.onContainer,
                ),
            ) {
                Text(if (success) strings.signedIn else strings.signIn, style = MaterialTheme.typography.titleMedium)
            }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LoginNotice(message, palette)
            }
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LoginNotice(error, palette, isError = true)
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
