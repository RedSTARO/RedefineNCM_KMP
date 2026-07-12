package com.leejlredstar.redefinencm.kmp.recognition

import androidx.compose.runtime.Composable

/** 返回一个应在用户点击听歌识曲入口后调用的麦克风权限请求函数。 */
@Composable
expect fun rememberMicrophonePermissionRequester(
    onResult: (Boolean) -> Unit,
): () -> Unit
