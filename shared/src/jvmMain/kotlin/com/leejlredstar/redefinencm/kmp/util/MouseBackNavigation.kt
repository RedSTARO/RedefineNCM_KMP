package com.leejlredstar.redefinencm.kmp.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * Feeds the mouse's back button into the window's back dispatcher, beside the Esc key.
 *
 * Going through the dispatcher rather than calling the app's own pop means the innermost enabled
 * [BackHandler] answers: a search overlay or a sheet closes before the page behind it is popped.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.mouseBackNavigation(): Modifier {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val input = remember { DirectNavigationEventInput() }
    DisposableEffect(dispatcher, input) {
        dispatcher?.addInput(input)
        onDispose { dispatcher?.removeInput(input) }
    }
    return onPointerEvent(PointerEventType.Press) { event ->
        if (event.button == PointerButton.Back) input.backCompleted()
    }
}
