package com.leejlredstar.redefinencm.kmp

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.leejlredstar.redefinencm.kmp.i18n.strings
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import kotlin.math.roundToInt

private enum class CaptionButtonKind {
    Minimize,
    Maximize,
    Close,
}

/**
 * Frameless Windows 10-style chrome: an unobtrusive drag strip plus native-sized caption
 * controls. The app content owns the rest of the window, so no system title bar is rendered.
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun WindowScope.Win10WindowChrome(
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    var lastPrimaryPressMillis by remember { mutableStateOf(Long.MIN_VALUE) }
    var lastPrimaryPressPosition by remember { mutableStateOf<Offset?>(null) }
    val doubleClickIntervalMillis = remember {
        (Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int)
            ?.toLong()
            ?: 500L
    }
    val doubleClickSlopPx = with(LocalDensity.current) { 8.dp.toPx() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
    ) {
        val showMinimize = maxWidth >= 184.dp
        val showMaximize = maxWidth >= 112.dp
        val captionButtonWidth = when {
            showMinimize -> 46.dp
            showMaximize -> 40.dp
            else -> minOf(40.dp, maxWidth * 0.45f)
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) {
            WindowDragStrip(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onPointerEvent(PointerEventType.Press) { event ->
                        if (event.button != PointerButton.Primary) return@onPointerEvent
                        val change = event.changes.firstOrNull() ?: return@onPointerEvent
                        val pressMillis = change.uptimeMillis
                        val pressPosition = change.position
                        val elapsed = pressMillis - lastPrimaryPressMillis
                        val distance = lastPrimaryPressPosition
                            ?.let { previous -> (pressPosition - previous).getDistance() }
                            ?: Float.POSITIVE_INFINITY
                        if (
                            elapsed in 1L..doubleClickIntervalMillis &&
                            distance <= doubleClickSlopPx
                        ) {
                            lastPrimaryPressMillis = Long.MIN_VALUE
                            lastPrimaryPressPosition = null
                            onToggleMaximize()
                            event.changes.forEach { it.consume() }
                        } else {
                            lastPrimaryPressMillis = pressMillis
                            lastPrimaryPressPosition = pressPosition
                        }
                    },
            ) {
                // The window's name, where every desktop app shows one.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = "RedefineNCM",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Row(Modifier.fillMaxHeight()) {
                if (showMinimize) {
                    Win10CaptionButton(
                        kind = CaptionButtonKind.Minimize,
                        isMaximized = isMaximized,
                        width = captionButtonWidth,
                        onClick = onMinimize,
                    )
                }
                if (showMaximize) {
                    Win10CaptionButton(
                        kind = CaptionButtonKind.Maximize,
                        isMaximized = isMaximized,
                        width = captionButtonWidth,
                        onClick = onToggleMaximize,
                    )
                }
                Win10CaptionButton(
                    kind = CaptionButtonKind.Close,
                    isMaximized = isMaximized,
                    width = captionButtonWidth,
                    onClick = onClose,
                )
            }
        }
    }
}

/**
 * Moves the window by its strip, as a native title bar does. A maximized window is restored to
 * its floating size under the pointer first. Compose's draggable area moved the maximized frame
 * as it was: Windows dropped the maximized state but kept the screen's size, and that size was
 * then saved as the floating one, so the next launch opened as large as the screen.
 */
@Composable
private fun WindowScope.WindowDragStrip(modifier: Modifier, content: @Composable () -> Unit) {
    val windowDrag = remember(window) { WindowDrag(window) }
    Box(
        modifier = modifier.pointerInput(windowDrag) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type != PointerType.Mouse) return@awaitEachGesture
                windowDrag.press()
                val dragging = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    ?: return@awaitEachGesture
                windowDrag.start()
                drag(dragging.id) { change ->
                    windowDrag.move()
                    change.consume()
                }
            }
        },
    ) {
        content()
    }
}

private const val MaxRestoreWaitMoves = 10

/** One drag of a window, in screen coordinates, which do not move with the window. */
private class WindowDrag(private val window: Window) {
    private var pressPointer: Point? = null
    private var pressWindow: Point? = null

    /** Set while a window restored from maximized waits for its floating size. */
    private var restore: PendingRestore? = null

    private class PendingRestore(val fraction: Double, val offsetY: Int, val maximizedWidth: Int) {
        var movesWaited = 0
    }

    fun press() {
        pressPointer = MouseInfo.getPointerInfo()?.location
        pressWindow = window.location
        restore = null
    }

    fun start() {
        val frame = window as? Frame ?: return
        val pointer = pressPointer ?: return
        if (frame.extendedState and Frame.MAXIMIZED_BOTH != Frame.MAXIMIZED_BOTH) return
        val bounds = window.bounds
        restore = PendingRestore(
            fraction = ((pointer.x - bounds.x).toDouble() / bounds.width).coerceIn(0.0, 1.0),
            offsetY = pointer.y - bounds.y,
            maximizedWidth = bounds.width,
        )
        frame.extendedState = Frame.NORMAL
    }

    fun move() {
        val current = MouseInfo.getPointerInfo()?.location ?: return
        restore?.let { pending ->
            // The restore is applied by the system; until the window has its floating size the
            // pointer's place on it cannot be worked out. A floating size as wide as the screen
            // never shrinks, so the wait is bounded.
            if (window.width >= pending.maximizedWidth && pending.movesWaited++ < MaxRestoreWaitMoves) return
            restore = null
            pressPointer = current
            pressWindow = Point(
                current.x - (pending.fraction * window.width).roundToInt(),
                current.y - pending.offsetY,
            )
        }
        val start = pressPointer ?: return
        val origin = pressWindow ?: return
        window.setLocation(origin.x + current.x - start.x, origin.y + current.y - start.y)
    }
}

@Composable
private fun Win10CaptionButton(
    kind: CaptionButtonKind,
    isMaximized: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isClose = kind == CaptionButtonKind.Close
    val label = when (kind) {
        CaptionButtonKind.Minimize -> strings.minimize
        CaptionButtonKind.Maximize -> if (isMaximized) strings.restoreWindow else strings.maximize
        CaptionButtonKind.Close -> strings.close
    }
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isClose && isPressed -> Color(0xFFC50F1F)
            isClose && (isHovered || isFocused) -> Color(0xFFE81123)
            isPressed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            isHovered || isFocused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        label = "caption button background",
    )
    val iconColor = if (isClose && (isHovered || isFocused || isPressed)) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(backgroundColor, RectangleShape)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val strokeWidth = 1.dp.toPx()
            when (kind) {
                CaptionButtonKind.Minimize -> drawLine(
                    color = iconColor,
                    start = Offset(1.dp.toPx(), 8.dp.toPx()),
                    end = Offset(11.dp.toPx(), 8.dp.toPx()),
                    strokeWidth = strokeWidth,
                )
                CaptionButtonKind.Maximize -> if (isMaximized) {
                    drawLine(
                        color = iconColor,
                        start = Offset(3.dp.toPx(), 1.dp.toPx()),
                        end = Offset(11.dp.toPx(), 1.dp.toPx()),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(11.dp.toPx(), 1.dp.toPx()),
                        end = Offset(11.dp.toPx(), 9.dp.toPx()),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(3.dp.toPx(), 1.dp.toPx()),
                        end = Offset(3.dp.toPx(), 3.dp.toPx()),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(9.dp.toPx(), 9.dp.toPx()),
                        end = Offset(11.dp.toPx(), 9.dp.toPx()),
                        strokeWidth = strokeWidth,
                    )
                    drawRect(
                        color = iconColor,
                        topLeft = Offset(1.dp.toPx(), 3.dp.toPx()),
                        size = Size(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(strokeWidth),
                    )
                } else {
                    drawRect(
                        color = iconColor,
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(strokeWidth),
                    )
                }
                CaptionButtonKind.Close -> {
                    drawLine(
                        color = iconColor,
                        start = Offset(2.dp.toPx(), 2.dp.toPx()),
                        end = Offset(10.dp.toPx(), 10.dp.toPx()),
                        strokeWidth = strokeWidth,
                    )
                    drawLine(
                        color = iconColor,
                        start = Offset(10.dp.toPx(), 2.dp.toPx()),
                        end = Offset(2.dp.toPx(), 10.dp.toPx()),
                        strokeWidth = strokeWidth,
                    )
                }
            }
        }
    }
}
