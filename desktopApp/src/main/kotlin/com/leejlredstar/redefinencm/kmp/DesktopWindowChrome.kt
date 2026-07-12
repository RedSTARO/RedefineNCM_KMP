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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import java.awt.Toolkit

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.Top,
    ) {
        WindowDraggableArea(
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
        ) {}
        Row(Modifier.fillMaxHeight()) {
            Win10CaptionButton(
                kind = CaptionButtonKind.Minimize,
                isMaximized = isMaximized,
                onClick = onMinimize,
            )
            Win10CaptionButton(
                kind = CaptionButtonKind.Maximize,
                isMaximized = isMaximized,
                onClick = onToggleMaximize,
            )
            Win10CaptionButton(
                kind = CaptionButtonKind.Close,
                isMaximized = isMaximized,
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun Win10CaptionButton(
    kind: CaptionButtonKind,
    isMaximized: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isClose = kind == CaptionButtonKind.Close
    val label = when (kind) {
        CaptionButtonKind.Minimize -> "最小化"
        CaptionButtonKind.Maximize -> if (isMaximized) "还原" else "最大化"
        CaptionButtonKind.Close -> "关闭"
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
            .width(46.dp)
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
