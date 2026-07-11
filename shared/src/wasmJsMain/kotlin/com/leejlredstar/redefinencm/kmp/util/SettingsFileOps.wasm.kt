@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberImportFileLauncher(onImported: (String) -> Unit): () -> Unit {
    val currentOnImported = rememberUpdatedState(onImported)
    return remember {
        {
            launchWebSettingsImport { json -> currentOnImported.value(json) }
        }
    }
}

@Composable
actual fun rememberExportFileLauncher(): (String) -> Unit = remember {
    { json -> launchWebSettingsExport(json) }
}

private fun launchWebSettingsImport(onImported: (String) -> Unit): Unit = js(
    """{
        const input = document.createElement("input");
        input.type = "file";
        input.accept = "application/json,.json";
        input.style.display = "none";
        const remove = () => input.remove();
        input.addEventListener("change", async () => {
            try {
                const file = input.files?.[0];
                if (file) onImported(await file.text());
            } finally {
                remove();
            }
        }, { once: true });
        input.addEventListener("cancel", remove, { once: true });
        document.body.appendChild(input);
        input.click();
    }""",
)

private fun launchWebSettingsExport(json: String): Unit = js(
    """{
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
    }""",
)
