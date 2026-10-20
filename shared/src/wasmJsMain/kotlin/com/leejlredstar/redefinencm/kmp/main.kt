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
import com.leejlredstar.redefinencm.kmp.data.db.BrowserStorageSqlDriver
import com.leejlredstar.redefinencm.kmp.data.local.LocalLibraryStore
import com.leejlredstar.redefinencm.kmp.di.initKoin
import com.leejlredstar.redefinencm.kmp.i18n.AppLanguage
import com.leejlredstar.redefinencm.kmp.i18n.I18n
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.image.configureWebArtworkImageLoader
import com.leejlredstar.amll.compose.LocalAmllPlatformEventBridge
import com.leejlredstar.amll.compose.WebAmllPlatformEventBridge
import com.leejlredstar.redefinencm.kmp.ui.theme.webBundledFontFamily
import com.leejlredstar.redefinencm.kmp.ui.theme.LocalPreloadedFontFamily
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import kotlin.JsFun

/** Browser entry point for the shared Compose application. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startAfterWebFontReady {
        configureWebArtworkImageLoader()
        initKoin()
        onSystemLanguageChange { I18n.refreshSystemLanguage() }
        // Built before the viewport: the player pulls in the settings, the database and the HTTP
        // client, and a failure there must stop the start before a viewport exists to show.
        val player = KoinPlatform.getKoin().get<PlatformPlayer>()
        ComposeViewport(viewportContainerId = "redefineNcmApp") {
            WebAppAfterFontPreload()
        }
        // After the viewport, so Compose sees each key first and can claim it.
        installWebKeyboardShortcuts(player)
        // Another tab changed the local library: the pages here show it, and this tab's next
        // change starts from it.
        val libraryScope = MainScope()
        onStoredValueChanged(BrowserStorageSqlDriver.LOCAL_LIBRARY_KEY) {
            libraryScope.launch { KoinPlatform.getKoin().get<LocalLibraryStore>().reload() }
        }
    }
}

@Composable
private fun WebAppAfterFontPreload() {
    // The page's lang follows the app's, so screen readers and the in-page lyric element read
    // the text in the right language.
    val language = I18n.language
    LaunchedEffect(language) {
        setDocumentLanguage(if (language == AppLanguage.ZH) "zh-CN" else language.tag)
    }
    val fontFamily = webBundledFontFamily()
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
        CompositionLocalProvider(
            LocalPreloadedFontFamily provides fontFamily,
            // Web is the only target with a DOM wheel deltaMode and page-visibility events to
            // forward into AMLL; the shared default is a no-op modifier.
            LocalAmllPlatformEventBridge provides WebAmllPlatformEventBridge,
        ) {
            App()
        }
    }
}

/**
 * Runs [start] once the boot page's font warm-up has settled. A failure thrown by [start] is shown
 * on the boot page with a reload button, instead of leaving its spinner up for good.
 */
@JsFun(
    """(start) => {
        const boot = globalThis.__redefineNcmBoot;
        boot?.started?.();
        Promise.resolve(globalThis.__redefineNcmFontReady)
            // A page cached from an older release rejected this promise when the font failed.
            .catch(error => console.warn(error?.message || String(error)))
            .then(() => start())
            .catch(error => {
                console.error(error);
                if (boot?.startFailed) boot.startFailed(error);
                else boot?.fail?.(error);
            });
    }""",
)
internal external fun startAfterWebFontReady(start: () -> Unit)

/** Another tab of this origin stored [key]; browsers tell every other tab, not the writer. */
@JsFun("(key, onChange) => { window.addEventListener('storage', event => { if (event.key === key) onChange(); }); }")
private external fun onStoredValueChanged(key: String, onChange: () -> Unit)

/** The browser's languages changed; only matters while the app follows them. */
@JsFun("(onChange) => { window.addEventListener('languagechange', () => onChange()); }")
private external fun onSystemLanguageChange(onChange: () -> Unit)

@JsFun("(tag) => { document.documentElement.lang = tag; }")
private external fun setDocumentLanguage(tag: String)

@JsFun(
    """() => {
        const boot = globalThis.__redefineNcmBoot;
        if (boot) {
            boot.ready();
            return;
        }
        // A page cached from an older release has no boot controller.
        const app = document.getElementById("redefineNcmApp");
        if (app) app.style.visibility = "visible";
        document.getElementById("redefineNcmBoot")?.remove();
    }""",
)
internal external fun showWebAppAndRemoveBootSurface()
