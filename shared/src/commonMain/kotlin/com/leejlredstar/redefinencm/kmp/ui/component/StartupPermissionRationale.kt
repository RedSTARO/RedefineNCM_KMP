package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.ui.theme.RedefineNCMTheme

/**
 * Says what the runtime permissions are for before the system asks for them.
 *
 * Without it, the notification and audio-library prompts would fire at launch, back to back and
 * on top of the login page, with nothing saying why a music player wants either.
 */
@Composable
fun StartupPermissionRationale(
    needsNotifications: Boolean,
    needsAudioLibrary: Boolean,
    onContinue: () -> Unit,
    onLater: () -> Unit,
) {
    RedefineNCMTheme {
        AlertDialog(
            onDismissRequest = onLater,
            title = { Text("开启以下权限") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (needsNotifications) {
                        Text(
                            "通知：在通知栏和锁屏显示播放控制、歌词和下载进度。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (needsAudioLibrary) {
                        Text(
                            "音乐和音频：找到已经下载到本机的歌曲，没有网络时也能播放。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "之后可以在系统设置里随时修改。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onContinue) { Text("继续") } },
            dismissButton = { TextButton(onClick = onLater) { Text("暂不") } },
        )
    }
}
