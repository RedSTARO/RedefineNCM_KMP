package com.leejlredstar.redefinencm.kmp.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.leejlredstar.redefinencm.kmp.i18n.strings
import com.leejlredstar.redefinencm.kmp.ui.component.UtilityFeedback
import com.leejlredstar.redefinencm.kmp.ui.component.inMemoryViewModel
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import com.leejlredstar.redefinencm.kmp.util.decodePngToImageBitmap

/** Draws any QR method, with its session retained through Activity recreation, not process death. */
class QrLoginPresenter : LoginMethodPresenter {
    override fun supports(method: LoginMethod): Boolean = method is QrLoginMethod

    override fun sectionTitle(methods: List<LoginMethod>): String =
        if (methods.size == 1) methods.single().displayName else strings.signInWithQrCode

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    override fun Content(method: LoginMethod, host: LoginHost, isActive: Boolean) {
        val qrMethod = method as QrLoginMethod
        val flow = inMemoryViewModel(key = method.id) { QrLoginSession(QrLoginFlow(qrMethod, host)) }.flow
        val imagePng by flow.imagePng.collectAsState()
        val status by flow.status.collectAsState()
        val loading by flow.loading.collectAsState()
        val error by flow.error.collectAsState()
        val success by flow.success.collectAsState()
        val expired by flow.expired.collectAsState()
        val bitmap = remember(imagePng) { imagePng?.takeIf { it.isNotEmpty() }?.let(::decodePngToImageBitmap) }
        val decodeFailed = imagePng != null && bitmap == null
        val showCode = !loading && !expired && !success && bitmap != null
        val scheme = MaterialTheme.colorScheme

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showCode || loading) {
                // The white scan field and quiet zone are intrinsic to QR contrast, not a theme accent.
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (showCode) Color.White else scheme.surfaceContainerHighest,
                    modifier = Modifier.widthIn(max = 208.dp).fillMaxWidth().aspectRatio(1f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (loading) {
                            LoadingIndicator(color = scheme.primary)
                        } else if (bitmap != null) {
                            Image(
                                painter = BitmapPainter(bitmap),
                                contentDescription = strings.providerSignInQrCode(host.provider.displayName),
                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
            val displayError = if (decodeFailed && !loading) strings.qrCodeDecodeFailed else error
            if (status.isNotBlank() && status != displayError && !success) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (displayError.isNotEmpty()) UtilityFeedback(displayError, isError = true)

            when {
                success -> UtilityFeedback(status, icon = AppIcons.Check)
                loading || showCode -> TextButton(onClick = { flow.cancel() }) { Text(strings.cancel) }
                else -> LoginPrimaryButton(
                    label = if (decodeFailed || expired) strings.regenerateQrCode else strings.generateQrCode,
                    onClick = { flow.start() },
                )
            }
        }
    }
}

private class QrLoginSession(val flow: QrLoginFlow) : androidx.lifecycle.ViewModel() {
    init { flow.start() }

    override fun onCleared() { flow.cancel() }
}
