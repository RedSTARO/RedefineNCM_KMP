@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.window.ComposeViewport
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.ui.theme.platformFontFamily
import com.leejlredstar.redefinencm.kmp.ui.theme.LocalPreloadedFontFamily
import kotlinx.coroutines.delay
import kotlin.JsFun

/** Browser entry point for the shared Compose application. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startAfterWebFontReady {
        initKoin()
        ComposeViewport(viewportContainerId = "redefineNcmApp") {
            WebAppAfterFontPreload()
        }
    }
}

@Composable
private fun WebAppAfterFontPreload() {
    val fontFamily = platformFontFamily()
    val fontFamilyResolver = LocalFontFamilyResolver.current
    var fontReady by remember { mutableStateOf(false) }
    LaunchedEffect(fontFamily, fontFamilyResolver) {
        runCatching { fontFamilyResolver.preload(fontFamily) }
        // Compose Resources first resolves the font bytes, then CanvasKit creates the typeface.
        // Keep the canvas hidden during that final decode so users never see missing-glyph boxes.
        delay(1_500L)
        fontReady = true
        showWebAppAndRemoveBootSurface()
    }
    if (fontReady) {
        CompositionLocalProvider(LocalPreloadedFontFamily provides fontFamily) {
            App()
        }
    }
}

@JsFun(
    """(start) => {
        Promise.resolve(globalThis.__redefineNcmFontReady)
            .catch(error => console.warn(error?.message || String(error)))
            .finally(() => {
                start();
            });
    }""",
)
private external fun startAfterWebFontReady(start: () -> Unit)

@JsFun(
    """() => {
        const app = document.getElementById("redefineNcmApp");
        if (app) app.style.visibility = "visible";
        document.getElementById("redefineNcmBoot")?.remove();
    }""",
)
private external fun showWebAppAndRemoveBootSurface()
