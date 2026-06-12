package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.viewmodel.LoginViewModel
import org.koin.compose.koinInject

/**
 * Login / 登录 (M3 Expressive) — cookie + server entry. Saves to PlatformSettings; because the
 * HttpClient is built once at startup, a restart applies the new server/cookie (noted in-screen).
 * QR login flow exists in LoginViewModel and can be added later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    viewModel: LoginViewModel = koinInject(),
) {
    val server by viewModel.server.collectAsState()
    val cookie by viewModel.cookie.collectAsState()

    var serverField by remember(server) { mutableStateOf(server) }
    var cookieField by remember(cookie) { mutableStateOf(cookie) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "服务器与 Cookie",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = serverField,
                onValueChange = { serverField = it; saved = false },
                label = { Text("服务器地址 (NeteaseCloudMusicApi)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = cookieField,
                onValueChange = { cookieField = it; saved = false },
                label = { Text("Cookie") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    viewModel.updateServer(serverField.trim())
                    viewModel.updateCookie(cookieField.trim())
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存")
            }

            if (saved) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "已保存。HTTP 客户端在启动时构建，重启应用后生效。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
