@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs the boot script and markup of the real index.html, which karma.config.d/web-boot.js loads
 * into the test browser, against fetches and page events the test controls.
 */
class WebBootPageTest {
    @Test
    fun theStoredLanguageChoosesTheBootCopy() = withBootPage {
        createBoot(FontStub.PENDING, language = "ja")
        assertEquals("Webアプリを読み込んでいます…", bootLabel())
        assertEquals("再読み込み", retryText())
        assertEquals("ja", documentLanguage())
        assertFalse(retryVisible())
    }

    @Test
    fun theExplicitThemeAppliesBeforeTheAppStarts() {
        withBootPage {
            setStoredTheme("dark")
            createBoot(FontStub.PENDING, language = "en")
            assertEquals("dark", documentBootTheme())
        }
        withBootPage {
            setStoredTheme("system")
            createBoot(FontStub.PENDING, language = "en")
            assertEquals("", documentBootTheme())
        }
    }

    @Test
    fun aWarmedFontLeadsToTheAppBeingShown() = runTest {
        withBootPage {
            createBoot(FontStub.OK, language = "en")
            assertTrue(fontSettles(), "the font warm-up must resolve")
            assertEquals("Starting…", bootLabel())
            assertFalse(retryVisible())

            markReady()
            assertFalse(bootPresent())
            assertEquals("visible", appVisibility())
        }
    }

    @Test
    fun aFontThatCannotBeFetchedDoesNotStopTheStart() = runTest {
        for (stub in listOf(FontStub.HTTP_404, FontStub.NETWORK_ERROR, FontStub.STALLED_HEADERS, FontStub.STALLED_BODY)) {
            withBootPage {
                createBoot(stub, language = "en")
                assertTrue(fontSettles(), "$stub: the app loads the font itself, so the warm-up resolves")
                assertEquals("Starting…", bootLabel(), stub.name)
                assertFalse(retryVisible(), stub.name)
            }
        }
    }

    @Test
    fun aBundleThatFailsToLoadShowsAReloadButton() = withBootPage {
        var reloads = 0
        createBoot(FontStub.PENDING, language = "zh", onReload = { reloads += 1 })
        // A <script> that failed to load: an error event with no error object.
        dispatchResourceError()

        assertEquals("无法启动应用", bootLabel())
        assertTrue(hintText().isNotBlank())
        assertTrue(retryVisible())
        assertEquals("alert", bootRole())
        assertTrue(retryFocused())
        clickRetry()
        assertEquals(1, reloads)
    }

    @Test
    fun aModuleTheBrowserCannotCompileSaysSo() = withBootPage {
        createBoot(FontStub.PENDING, language = "en")
        dispatchRejection(compileError = true)
        assertEquals("This browser can't run the app", bootLabel())
        assertTrue(retryVisible())
    }

    @Test
    fun aRejectedModuleFetchShowsTheGeneralFailure() = withBootPage {
        createBoot(FontStub.PENDING, language = "en")
        dispatchRejection(compileError = false)
        assertEquals("Couldn't start the app", bootLabel())
    }

    @Test
    fun onceTheAppRunsOnlyAnUncaughtExceptionCounts() = withBootPage {
        createBoot(FontStub.PENDING, language = "en")
        markStarted()
        dispatchResourceError()
        dispatchRejection(compileError = false)
        dispatchErrorWithoutObject()
        assertFalse(retryVisible(), "resource errors, rejections and notices are not start failures")

        dispatchUncaughtException()
        assertTrue(retryVisible())
    }

    @Test
    fun theAppIsShownEvenAfterAFailureWasReported() = withBootPage {
        createBoot(FontStub.PENDING, language = "en")
        reportFailure()
        assertTrue(retryVisible())

        markReady()
        assertFalse(bootPresent())
        assertEquals("visible", appVisibility())
    }

    @Test
    fun anExceptionFromTheAppStartIsShownOnTheBootPage() = runTest {
        withBootPage {
            createBoot(FontStub.OK, language = "en", installAsPageBoot = true)
            val entered = CompletableDeferred<Unit>()
            startAfterWebFontReady {
                entered.complete(Unit)
                throw IllegalStateException("start failed")
            }
            entered.await()
            afterPendingTasks()
            assertEquals("Couldn't start the app", bootLabel())
            assertTrue(retryVisible())

            // A viewport created before the failure may still report ready; the broken app must
            // stay hidden behind the failure.
            markReady()
            assertTrue(bootPresent())
            assertEquals("", appVisibility())
        }
    }

    @Test
    fun aPageFromAnOlderReleaseStillStartsAndShowsTheApp() = runTest {
        withBootPage {
            installOlderReleaseGlobals()
            val started = CompletableDeferred<Unit>()
            startAfterWebFontReady { started.complete(Unit) }
            started.await()

            showWebAppAndRemoveBootSurface()
            assertFalse(bootPresent())
            assertEquals("visible", appVisibility())
        }
    }

    @Test
    fun theIndexPageCreatesItsBootControllerOnLoad() = runTest {
        withBootPage {
            assertTrue(runIndexPageScript(), "index.html must expose __redefineNcmBoot and its font promise")
            val settled = CompletableDeferred<Boolean>()
            whenPageFontSettles { settled.complete(it) }
            assertTrue(settled.await())
        }
    }
}

private enum class FontStub { OK, HTTP_404, NETWORK_ERROR, STALLED_HEADERS, STALLED_BODY, PENDING }

private inline fun <T> withBootPage(block: () -> T): T {
    mountBootPage()
    try {
        return block()
    } finally {
        unmountBootPage()
    }
}

private fun createBoot(
    stub: FontStub,
    language: String,
    onReload: () -> Unit = {},
    installAsPageBoot: Boolean = false,
) = createBootUnderTest(stub.name, language, onReload, installAsPageBoot)

private suspend fun fontSettles(): Boolean {
    val settled = CompletableDeferred<Boolean>()
    whenFontSettles { settled.complete(it) }
    return settled.await()
}

private suspend fun afterPendingTasks() {
    val done = CompletableDeferred<Unit>()
    afterTasks { done.complete(Unit) }
    done.await()
}

@JsFun(
    """() => {
        if (typeof globalThis.__initializeBootUnderTest !== "function") {
            throw new Error("karma.config.d/web-boot.js did not load the boot page");
        }
        if (typeof globalThis.createRedefineNcmBoot !== "function") globalThis.__initializeBootUnderTest();
        globalThis.__bootTestSavedLanguage = document.documentElement.lang;
        globalThis.__bootTestSavedTheme = localStorage.getItem("themeMode");
        globalThis.__bootTestSavedBootTheme = document.documentElement.getAttribute("data-boot-theme");
        const host = document.createElement("div");
        host.id = "__redefineNcmBootTestHost";
        host.innerHTML = globalThis.__redefineNcmBootMarkup;
        document.body.appendChild(host);
    }""",
)
private external fun mountBootPage()

@JsFun(
    """() => {
        globalThis.__bootUnderTest?.dispose();
        globalThis.__redefineNcmBoot?.dispose?.();
        for (const key of ["__bootUnderTest", "__bootTestEvents", "__redefineNcmBoot", "__redefineNcmFontReady"]) {
            delete globalThis[key];
        }
        document.getElementById("__redefineNcmBootTestHost")?.remove();
        document.documentElement.lang = globalThis.__bootTestSavedLanguage ?? "";
        const theme = globalThis.__bootTestSavedTheme;
        if (theme == null) localStorage.removeItem("themeMode");
        else localStorage.setItem("themeMode", theme);
        const bootTheme = globalThis.__bootTestSavedBootTheme;
        if (bootTheme == null) document.documentElement.removeAttribute("data-boot-theme");
        else document.documentElement.setAttribute("data-boot-theme", bootTheme);
    }""",
)
private external fun unmountBootPage()

@JsFun(
    """(stub, language, onReload, installAsPageBoot) => {
        const aborted = signal => new DOMException("Aborted", "AbortError");
        const fetches = {
            OK: () => Promise.resolve(new Response(new Uint8Array(1024), {
                status: 200, headers: { "Content-Length": "1024" },
            })),
            HTTP_404: () => Promise.resolve(new Response("", { status: 404 })),
            NETWORK_ERROR: () => Promise.reject(new TypeError("Failed to fetch")),
            STALLED_HEADERS: (url, init) => new Promise((resolve, reject) => {
                init.signal.addEventListener("abort", () => reject(aborted()), { once: true });
            }),
            STALLED_BODY: (url, init) => Promise.resolve(new Response(new ReadableStream({
                start(controller) {
                    controller.enqueue(new Uint8Array(8));
                    init.signal.addEventListener("abort", () => controller.error(aborted()), { once: true });
                },
            }), { status: 200 })),
            PENDING: () => new Promise(() => {}),
        };
        const events = new EventTarget();
        globalThis.__bootTestEvents = events;
        const boot = globalThis.createRedefineNcmBoot({
            readLanguage: () => language,
            reload: () => onReload(),
            stallTimeoutMs: 30,
            events,
            fetch: fetches[stub],
        });
        globalThis.__bootUnderTest = boot;
        if (installAsPageBoot) {
            globalThis.__redefineNcmBoot = boot;
            globalThis.__redefineNcmFontReady = boot.fontReady;
        }
    }""",
)
private external fun createBootUnderTest(stub: String, language: String, onReload: () -> Unit, installAsPageBoot: Boolean)

@JsFun(
    """() => {
        delete globalThis.__redefineNcmBoot;
        const font = Promise.reject(new Error("Couldn't load the font: HTTP 404"));
        font.catch(() => {});
        globalThis.__redefineNcmFontReady = font;
    }""",
)
private external fun installOlderReleaseGlobals()

@JsFun(
    """() => {
        globalThis.__initializeBootUnderTest();
        return typeof globalThis.__redefineNcmBoot?.ready === "function"
            && globalThis.__redefineNcmFontReady === globalThis.__redefineNcmBoot.fontReady;
    }""",
)
private external fun runIndexPageScript(): Boolean

@JsFun("(done) => { globalThis.__redefineNcmFontReady.then(() => done(true), () => done(false)); }")
private external fun whenPageFontSettles(done: (Boolean) -> Unit)

@JsFun("(done) => { globalThis.__bootUnderTest.fontReady.then(() => done(true), () => done(false)); }")
private external fun whenFontSettles(done: (Boolean) -> Unit)

@JsFun("(done) => { setTimeout(() => done(), 0); }")
private external fun afterTasks(done: () -> Unit)

@JsFun("() => { globalThis.__bootTestEvents.dispatchEvent(new Event('error')); }")
private external fun dispatchResourceError()

@JsFun(
    """() => {
        globalThis.__bootTestEvents.dispatchEvent(new ErrorEvent("error", {
            message: "ResizeObserver loop completed with undelivered notifications.",
        }));
    }""",
)
private external fun dispatchErrorWithoutObject()

@JsFun(
    """() => {
        globalThis.__bootTestEvents.dispatchEvent(new ErrorEvent("error", {
            message: "boom", error: new Error("boom"),
        }));
    }""",
)
private external fun dispatchUncaughtException()

@JsFun(
    """(compileError) => {
        const reason = compileError
            ? new WebAssembly.CompileError("unsupported module")
            : new TypeError("Incorrect response MIME type. Expected 'application/wasm'.");
        const promise = Promise.reject(reason);
        promise.catch(() => {});
        globalThis.__bootTestEvents.dispatchEvent(new PromiseRejectionEvent("unhandledrejection", { promise, reason }));
    }""",
)
private external fun dispatchRejection(compileError: Boolean)

@JsFun("() => globalThis.__bootUnderTest.started()")
private external fun markStarted()

@JsFun("() => globalThis.__bootUnderTest.ready()")
private external fun markReady()

@JsFun("() => globalThis.__bootUnderTest.fail(new Error('reported by the test'))")
private external fun reportFailure()

@JsFun("() => document.getElementById('redefineNcmBootLabel')?.textContent ?? ''")
private external fun bootLabel(): String

@JsFun("() => document.getElementById('redefineNcmBootHint')?.textContent ?? ''")
private external fun hintText(): String

@JsFun("() => document.getElementById('redefineNcmBootRetry')?.textContent ?? ''")
private external fun retryText(): String

@JsFun("() => { const retry = document.getElementById('redefineNcmBootRetry'); return !!retry && !retry.hidden; }")
private external fun retryVisible(): Boolean

@JsFun("() => document.activeElement === document.getElementById('redefineNcmBootRetry')")
private external fun retryFocused(): Boolean

@JsFun("() => document.getElementById('redefineNcmBootRetry').click()")
private external fun clickRetry()

@JsFun("() => document.getElementById('redefineNcmBoot')?.getAttribute('role') ?? ''")
private external fun bootRole(): String

@JsFun("() => !!document.getElementById('redefineNcmBoot')")
private external fun bootPresent(): Boolean

@JsFun("() => document.getElementById('redefineNcmApp')?.style.visibility ?? ''")
private external fun appVisibility(): String

@JsFun("() => document.documentElement.lang")
private external fun documentLanguage(): String

@JsFun("(theme) => { localStorage.setItem('themeMode', theme); }")
private external fun setStoredTheme(theme: String)

@JsFun("() => document.documentElement.dataset.bootTheme ?? ''")
private external fun documentBootTheme(): String
