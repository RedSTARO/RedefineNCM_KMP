package com.leejlredstar.redefinencm.kmp.ui.login

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.data.auth.LoginHost
import com.leejlredstar.redefinencm.kmp.data.auth.LoginMethod
import com.leejlredstar.redefinencm.kmp.data.auth.QrLoginFlow
import com.leejlredstar.redefinencm.kmp.data.auth.QrLoginMethod
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.ui.theme.ContentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.decodePngToImageBitmap

/**
 * Draws any [QrLoginMethod]: the code, what to do with it, and a way to get a fresh one. The
 * state is a [QrLoginFlow] per method, started when the method is shown and cancelled when it
 * leaves the screen, so switching between two scan apps replaces the code.
 */
class QrLoginPresenter : LoginMethodPresenter {
    override fun supports(method: LoginMethod): Boolean = method is QrLoginMethod

    override fun sectionTitle(methods: List<LoginMethod>): String =
        if (methods.size == 1) methods.single().displayName else "扫码登录"

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun Content(method: LoginMethod, host: LoginHost, palette: ContentAccentPalette) {
        val qrMethod = method as QrLoginMethod
        val flow = remember(qrMethod, host) { QrLoginFlow(qrMethod, host) }
        // 原版 QrLogin：进入登录页即自动生成二维码
        DisposableEffect(flow) {
            flow.start()
            onDispose { flow.cancel() }
        }

        val imagePng by flow.imagePng.collectAsState()
        val status by flow.status.collectAsState()
        val loading by flow.loading.collectAsState()
        val error by flow.error.collectAsState()
        val success by flow.success.collectAsState()
        val expired by flow.expired.collectAsState()
        val bitmap = remember(imagePng) {
            imagePng?.takeIf { it.isNotEmpty() }?.let(::decodePngToImageBitmap)
        }
        val decodeFailed = imagePng?.isNotEmpty() == true && bitmap == null

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(palette.onQuietContainer.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    LoadingIndicator(color = palette.accent)
                } else if (bitmap != null) {
                    Image(
                        painter = BitmapPainter(bitmap),
                        contentDescription = "${host.provider.displayName}登录二维码",
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        contentScale = ContentScale.Fit,
                    )
                    if (expired) {
                        // A dead code is covered and becomes the refresh button itself.
                        Surface(
                            onClick = { flow.start() },
                            color = palette.quietContainer.copy(alpha = 0.92f),
                            contentColor = palette.onQuietContainer,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(AppIcons.Refresh, contentDescription = null)
                                Spacer(Modifier.height(8.dp))
                                Text("二维码已失效", style = MaterialTheme.typography.titleSmall)
                                Text("点按刷新", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (decodeFailed) {
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
                        color = palette.secondaryOnQuietContainer,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (success) palette.onQuietContainer else palette.secondaryOnQuietContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            if (error.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                LoginNotice(error, palette, isError = true, centered = true)
            }

            Spacer(Modifier.height(12.dp))

            if ((imagePng == null || decodeFailed || expired) && !loading) {
                Button(
                    onClick = { flow.start() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = palette.onAccent,
                    ),
                ) {
                    Text(if (decodeFailed || expired) "重新生成二维码" else "生成二维码")
                }
            } else {
                OutlinedButton(
                    onClick = { flow.cancel() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
                ) {
                    Text("取消")
                }
            }
        }
    }
}
