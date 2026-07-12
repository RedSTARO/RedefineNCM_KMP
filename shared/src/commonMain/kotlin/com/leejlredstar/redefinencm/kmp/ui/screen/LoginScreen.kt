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
import androidx.compose.foundation.verticalScroll
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressiveLayout
import com.leejlredstar.redefinencm.kmp.ui.component.ExpressivePage
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
    val cookiePersistError by viewModel.cookiePersistError.collectAsState()

    var serverField by remember(server) { mutableStateOf(server) }
    var cookieField by remember(cookie) { mutableStateOf(cookie) }
    var saved by remember { mutableStateOf(false) }
    var revealCookie by remember { mutableStateOf(false) }
    val loginPalette = contentAccentPalette(MaterialTheme.colorScheme.primaryContainer)
    val qrBitmap = remember(qrBitmapBytes) {
        qrBitmapBytes?.takeIf { it.isNotEmpty() }?.let(::decodePngToImageBitmap)
    }
    val qrDecodeFailed = qrBitmapBytes?.isNotEmpty() == true && qrBitmap == null

    // 原版 QrLogin：进入登录页即自动生成二维码
    LaunchedEffect(Unit) {
        if (qrDataUri.isEmpty() && !qrLoading) viewModel.startQrLogin()
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.onCleared() }
    }

    // Auto-close after successful QR login
    LaunchedEffect(qrSuccess) {
        if (qrSuccess) {
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
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            loginPalette.pageStart,
                            loginPalette.container,
                            Color.Transparent,
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
                Surface(
                    shape = CircleShape,
                    color = loginPalette.quietContainer.copy(alpha = 0.78f),
                    contentColor = loginPalette.onQuietContainer,
                ) {
                    Icon(
                        AppIcons.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            Text(
                text = "RedefineNCM",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = loginPalette.onPageStart,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }

        // QR Login section
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = loginPalette.quietContainer,
            contentColor = loginPalette.onQuietContainer,
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
                        .clip(MaterialTheme.shapes.large)
                        .background(loginPalette.onQuietContainer.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrLoading) {
                        LoadingIndicator(color = loginPalette.accent)
                    } else if (qrBitmap != null) {
                        Image(
                            painter = BitmapPainter(qrBitmap),
                            contentDescription = "网易云音乐登录二维码",
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else if (qrDecodeFailed) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = AppIcons.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "二维码解析失败",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Text(
                            "二维码\n将在此显示",
                            style = MaterialTheme.typography.bodyMedium,
                                color = loginPalette.secondaryOnQuietContainer,
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
                        qrSuccess -> loginPalette.onQuietContainer
                        else -> loginPalette.secondaryOnQuietContainer
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )

                // Error message
                if (qrError.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            qrError,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Buttons
                if ((qrDataUri.isEmpty() || qrDecodeFailed) && !qrLoading) {
                    Button(
                        onClick = { viewModel.startQrLogin() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = loginPalette.accent,
                            contentColor = loginPalette.onAccent,
                        ),
                    ) {
                        Text(if (qrDecodeFailed) "重新生成二维码" else "生成二维码")
                    }
                } else {

                    OutlinedButton(
                        onClick = { viewModel.cancelQrLogin() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = loginPalette.accent,
                        ),
                    ) {
                        Text("取消")
                    }
                }
            }
        }

        // Divider
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            color = loginPalette.onQuietContainer.copy(alpha = 0.16f),
        )

        // Manual cookie/server input
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = loginPalette.quietContainer,
            contentColor = loginPalette.onQuietContainer,
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
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                    colors = loginTextFieldColors(loginPalette),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cookieField,
                    onValueChange = { cookieField = it; saved = false },
                    label = { Text("Cookie") },
                    minLines = 3,
                    visualTransformation = if (revealCookie) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { revealCookie = !revealCookie }) {
                            Text(if (revealCookie) "隐藏" else "显示")
                        }
                    },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                    colors = loginTextFieldColors(loginPalette),
                )
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = {
                        saved = false
                        viewModel.updateCredentials(
                            newServer = serverField.trim(),
                            newCookie = cookieField.trim(),
                        ) {
                            saved = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = loginPalette.container,
                        contentColor = loginPalette.onContainer,
                    ),
                ) {
                    Text("保存", style = MaterialTheme.typography.titleMedium)
                }
                if (saved) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = loginPalette.container,
                        contentColor = loginPalette.onContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            "已保存；Cookie 已生效，服务器地址重启后生效。",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                cookiePersistError?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun loginTextFieldColors(
    loginPalette: com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette,
) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = loginPalette.quietContainer,
    unfocusedContainerColor = loginPalette.quietContainer,
    focusedTextColor = loginPalette.onQuietContainer,
    unfocusedTextColor = loginPalette.onQuietContainer,
    focusedLabelColor = loginPalette.accent,
            unfocusedLabelColor = loginPalette.secondaryOnQuietContainer,
    focusedBorderColor = loginPalette.accent,
    unfocusedBorderColor = loginPalette.onQuietContainer.copy(alpha = 0.18f),
    cursorColor = loginPalette.accent,
)
