package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.util.decodePngToImageBitmap
import com.leejlredstar.redefinencm.kmp.viewmodel.LoginViewModel
import org.koin.compose.koinInject

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    viewModel: LoginViewModel = koinInject(),
) {
    val server by viewModel.server.collectAsState()
    val cookie by viewModel.cookie.collectAsState()
    val qrDataUri by viewModel.qrDataUri.collectAsState()
    val qrBitmapBytes by viewModel.qrBitmapBytes.collectAsState()
    
    val qrScanStatus by viewModel.qrScanStatus.collectAsState()
    val qrLoading by viewModel.qrLoading.collectAsState()
    val qrError by viewModel.qrError.collectAsState()
    val qrSuccess by viewModel.qrSuccess.collectAsState()

    var serverField by remember(server) { mutableStateOf(server) }
    var cookieField by remember(cookie) { mutableStateOf(cookie) }
    var saved by remember { mutableStateOf(false) }

    // Auto-close after successful QR login
    LaunchedEffect(qrSuccess) {
        if (qrSuccess) {
            kotlinx.coroutines.delay(1200)
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "RedefineNCM",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }

        // QR Login section
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "扫码登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))

                // QR code image or placeholder
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrLoading) {
                        CircularProgressIndicator()
                    } else if (qrBitmapBytes != null && qrBitmapBytes!!.isNotEmpty()) {
                        val bmp = remember(qrBitmapBytes) { decodePngToImageBitmap(qrBitmapBytes!!) }
                        if (bmp != null) {
                            Image(
                                painter = BitmapPainter(bmp),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    } else {
                        Text(
                            "二维码\n将在此显示",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Status text
                Text(
                    qrScanStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        qrSuccess -> MaterialTheme.colorScheme.primary
                        qrError.isNotEmpty() -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )

                // Error message
                if (qrError.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        qrError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Buttons
                if (qrDataUri.isEmpty() && !qrLoading) {
                    Button(
                        onClick = { viewModel.startQrLogin() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                    ) {
                        Text("生成二维码")
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.cancelQrLogin() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                    ) {
                        Text("取消")
                    }
                }
            }
        }

        // Divider
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // Manual cookie/server input
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "手动输入",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = serverField,
                    onValueChange = { serverField = it; saved = false },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cookieField,
                    onValueChange = { cookieField = it; saved = false },
                    label = { Text("Cookie") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = {
                        viewModel.updateServer(serverField.trim())
                        viewModel.updateCookie(cookieField.trim())
                        saved = true
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = CircleShape,
                ) {
                    Text("保存", style = MaterialTheme.typography.titleMedium)
                }
                if (saved) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "已保存，重启应用后生效。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
