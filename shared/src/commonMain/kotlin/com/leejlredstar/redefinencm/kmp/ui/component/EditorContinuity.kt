package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leejlredstar.redefinencm.kmp.getPlatform
import com.leejlredstar.redefinencm.kmp.ui.adaptive.LocalSoftKeyboardVisible

/** Composition-scoped memory that survives Activity recreation, but never writes to saved state. */
@Composable
internal inline fun <reified T : ViewModel> inMemoryViewModel(
    key: String? = null,
    crossinline create: () -> T,
): T {
    val owner = rememberViewModelStoreOwner(
        parent = LocalViewModelStoreOwner.current,
        savedStateRegistryOwner = null,
    )
    return viewModel(viewModelStoreOwner = owner, key = key) { create() }
}

/** Save only the visible text and selection, not Compose's undo history, and bound Bundle size. */
@Composable
internal fun rememberSaveableEditorText(initial: String = ""): TextFieldState =
    rememberSaveable(saver = EditorTextSaver) { TextFieldState(initial) }

internal val EditorTextSaver = listSaver<TextFieldState, Any>(
    save = { state ->
        val text = boundedSavedEditorText(state.text)
        listOf(text, state.selection.start.coerceIn(0, text.length), state.selection.end.coerceIn(0, text.length))
    },
    restore = { saved -> TextFieldState(saved[0] as String, TextRange(saved[1] as Int, saved[2] as Int)) },
)

internal fun boundedSavedEditorText(text: CharSequence): String {
    val end = if (text.length > MaxSavedEditorLength && text[MaxSavedEditorLength - 1].isHighSurrogate()) {
        MaxSavedEditorLength - 1
    } else {
        minOf(text.length, MaxSavedEditorLength)
    }
    return text.subSequence(0, end).toString()
}

internal const val MaxSavedEditorLength = 4_096

private class EditorFocusMemory : ViewModel() {
    var focused = false
    var imeVisible = false
}

internal class EditorFocus(
    val modifier: Modifier,
    val showKeyboardOnFocus: Boolean?,
)

/**
 * Restore a focused editor only after its window is foreground and the editor is attached.
 * The IME is separate from focus: Back can hide it without blurring the field, and a physical
 * keyboard can be used while it is hidden. Use the returned option with a state-based TextField.
 */
@Composable
internal fun rememberEditorFocus(
    isActive: Boolean = true,
    enabled: Boolean = true,
    openRequest: Int = 0,
    onOpenRequestHandled: () -> Unit = {},
    imeVisibility: Boolean = LocalSoftKeyboardVisible.current,
): EditorFocus {
    val onHandled by rememberUpdatedState(onOpenRequestHandled)
    val isAndroid = remember { getPlatform().isAndroid }
    val memory = inMemoryViewModel { EditorFocusMemory() }
    var restorePending by remember(memory) { mutableStateOf(isAndroid && memory.focused) }
    var suppressIme by remember(memory) { mutableStateOf(restorePending && !memory.imeVisible) }
    var hasFocus by remember { mutableStateOf(false) }
    var positioned by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val foreground = window.isWindowFocused && (!isAndroid || lifecycleState == Lifecycle.State.RESUMED)

    LaunchedEffect(isActive) {
        if (!isActive) {
            // An ordinary tab/route switch is not an Activity recreation.
            restorePending = false
            suppressIme = false
            memory.focused = false
            memory.imeVisible = false
        }
    }
    LaunchedEffect(isActive, foreground, hasFocus, imeVisibility, restorePending) {
        if (isActive && foreground && hasFocus && !restorePending) {
            memory.focused = true
            memory.imeVisible = imeVisibility
        }
    }
    LaunchedEffect(isActive, foreground, positioned, enabled, openRequest) {
        if (!isActive || !foreground || !positioned || !enabled) return@LaunchedEffect
        val explicitRequest = openRequest > 0
        if (!explicitRequest && !restorePending) return@LaunchedEffect
        val showIme = explicitRequest || memory.imeVisible
        if (explicitRequest) suppressIme = false
        if (requester.requestFocus()) {
            if (showIme) {
                // Give the newly focused editor a frame to start its input session.
                withFrameNanos { }
                keyboard?.show()
            }
            restorePending = false
            if (explicitRequest) onHandled()
        }
    }

    return EditorFocus(
        modifier = Modifier
            .focusProperties { if (!isActive) canFocus = false }
            .focusRequester(requester)
            .onFocusChanged { change ->
                val focused = change.isFocused
                if (focused == hasFocus) return@onFocusChanged
                val inWindow = window.isWindowFocused &&
                    (!isAndroid || lifecycle.currentState == Lifecycle.State.RESUMED)
                if (isActive && inWindow) {
                    memory.focused = focused
                    if (!focused) {
                        memory.imeVisible = false
                        suppressIme = false
                    }
                }
                hasFocus = focused
            }
            .onGloballyPositioned { positioned = it.isAttached },
        showKeyboardOnFocus = false.takeIf { suppressIme },
    )
}
