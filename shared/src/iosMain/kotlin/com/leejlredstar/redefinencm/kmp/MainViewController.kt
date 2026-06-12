package com.leejlredstar.redefinencm.kmp

import androidx.compose.ui.window.ComposeUIViewController
import com.leejlredstar.redefinencm.kmp.di.initKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initKoin() // idempotent
    return ComposeUIViewController { App() }
}
