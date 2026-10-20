@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberImportFileLauncher(onFailed: () -> Unit, onImported: (String) -> Unit): () -> Unit {
    val currentOnImported = rememberUpdatedState(onImported)
    val currentOnFailed = rememberUpdatedState(onFailed)
    return remember {
        {
            launchWebSettingsImport(
                onImported = { json -> currentOnImported.value(json) },
                onFailed = { currentOnFailed.value() },
            )
        }
    }
}

@Composable
actual fun rememberExportFileLauncher(onFailed: () -> Unit): (String) -> Unit {
    val currentOnFailed = rememberUpdatedState(onFailed)
    return remember {
        { json -> launchWebSettingsExport(json, onFailed = { currentOnFailed.value() }) }
    }
}

private fun launchWebSettingsImport(onImported: (String) -> Unit, onFailed: () -> Unit): Unit = js(
    """{
        const input = document.createElement("input");
        input.type = "file";
        input.accept = "application/json,.json";
        input.style.display = "none";
        const remove = () => input.remove();
        input.addEventListener("change", async () => {
            try {
                const file = input.files?.[0];
                if (!file) return;
                let text;
                try {
                    text = await file.text();
                } catch (error) {
                    onFailed();
                    return;
                }
                onImported(text);
            } finally {
                remove();
            }
        }, { once: true });
        input.addEventListener("cancel", remove, { once: true });
        document.body.appendChild(input);
        input.click();
    }""",
)

private fun launchWebSettingsExport(json: String, onFailed: () -> Unit): Unit = js(
    """{
        try {
            const blob = new Blob([json], { type: "application/json;charset=utf-8" });
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = "RedefineNCM_KMP_settings.json";
            anchor.style.display = "none";
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            setTimeout(() => URL.revokeObjectURL(url), 0);
        } catch (error) {
            onFailed();
        }
    }""",
)
