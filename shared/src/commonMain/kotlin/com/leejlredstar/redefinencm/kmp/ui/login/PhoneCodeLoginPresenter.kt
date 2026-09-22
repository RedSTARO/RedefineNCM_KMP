package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.auth.LoginHost
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.PhoneCodeLoginFlow
import com.leejlredstar.redefinencm.kmp.data.auth.PhoneCodeLoginMethod
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette

/**
 * Draws any [PhoneCodeLoginMethod]: a phone field with a send button that counts down after a
 * send, a code field, and a sign-in button. The state is a [PhoneCodeLoginFlow] per method.
 */
class PhoneCodeLoginPresenter : LoginMethodPresenter {
    override fun supports(method: LoginMethod): Boolean = method is PhoneCodeLoginMethod

    override fun sectionTitle(methods: List<LoginMethod>): String =
        methods.singleOrNull()?.displayName ?: "手机验证码"

    @Composable
    override fun Content(method: LoginMethod, host: LoginHost, palette: ContentAccentPalette) {
        val phoneMethod = method as PhoneCodeLoginMethod
        val flow = remember(phoneMethod, host) { PhoneCodeLoginFlow(phoneMethod, host) }
        DisposableEffect(flow) { onDispose { flow.cancel() } }

        val phone by flow.phone.collectAsState()
        val code by flow.code.collectAsState()
        val busy by flow.busy.collectAsState()
        val codeSent by flow.codeSent.collectAsState()
        val resendIn by flow.resendInSeconds.collectAsState()
        val message by flow.message.collectAsState()
        val error by flow.error.collectAsState()
        val success by flow.success.collectAsState()

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = flow::setPhone,
                    label = { Text("手机号") },
                    supportingText = { Text(flow.phoneHint, color = palette.secondaryOnQuietContainer) },
                    singleLine = true,
                    enabled = !success,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                    colors = loginTextFieldColors(palette),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = flow::sendCode,
                    enabled = !busy && resendIn == 0 && !success,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
                ) {
                    Text(
                        when {
                            resendIn > 0 -> "$resendIn 秒后可重发"
                            codeSent -> "重新发送"
                            else -> "发送验证码"
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = flow::setCode,
                label = { Text("验证码") },
                singleLine = true,
                enabled = codeSent && !success,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                colors = loginTextFieldColors(palette),
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = flow::verify,
                enabled = codeSent && code.isNotBlank() && !busy && !success,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = palette.container,
                    contentColor = palette.onContainer,
                ),
            ) {
                Text(if (success) "已登录" else "登录", style = MaterialTheme.typography.titleMedium)
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
