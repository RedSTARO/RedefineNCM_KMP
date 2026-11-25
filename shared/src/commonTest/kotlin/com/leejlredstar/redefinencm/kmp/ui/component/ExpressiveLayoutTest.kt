package com.leejlredstar.redefinencm.kmp.ui.component

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpressiveLayoutTest {
    @Test
    fun guttersUseTheAvailablePaneAtTheMaterialBreakpoint() {
        assertEquals(16.dp, expressivePageGutter(320.dp))
        assertEquals(16.dp, expressivePageGutter(599.dp))
        assertEquals(24.dp, expressivePageGutter(600.dp))
        assertEquals(24.dp, expressivePageGutter(1200.dp))
    }

    @Test
    fun groupEndsAreRoundedOnTheOutsideAndJoinedOnTheInside() {
        val first = connectedListItemShape(index = 0, count = 3)
        val middle = connectedListItemShape(index = 1, count = 3)
        val last = connectedListItemShape(index = 2, count = 3)
        val single = connectedListItemShape(index = 0, count = 1)
        val pressedMiddle = connectedListItemShape(index = 1, count = 3, pressProgress = 1f)

        fun assertCorners(shape: androidx.compose.foundation.shape.RoundedCornerShape, top: Dp, bottom: Dp) {
            val size = Size(320f, 72f)
            val density = Density(1f)
            assertEquals(top.value, shape.topStart.toPx(size, density))
            assertEquals(top.value, shape.topEnd.toPx(size, density))
            assertEquals(bottom.value, shape.bottomStart.toPx(size, density))
            assertEquals(bottom.value, shape.bottomEnd.toPx(size, density))
        }

        assertCorners(first, top = 16.dp, bottom = 4.dp)
        assertCorners(middle, top = 4.dp, bottom = 4.dp)
        assertCorners(last, top = 4.dp, bottom = 16.dp)
        assertCorners(single, top = 16.dp, bottom = 16.dp)
        assertCorners(pressedMiddle, top = 16.dp, bottom = 16.dp)
        assertEquals(2.dp, ExpressiveLayout.ConnectedItemGap)
    }
}
