package com.leejlredstar.redefinencm.kmp

import kotlin.test.Test
import kotlin.test.assertEquals

class AppNavigationStackTest {
    @Test
    fun notificationDestinationIsAddedWithoutClearingCurrentStack() {
        val stack = mutableListOf("playlist", "details")

        stack.focusOrPush("downloads")

        assertEquals(listOf("playlist", "details", "downloads"), stack)
    }

    @Test
    fun existingNotificationDestinationIsFocusedWithoutDuplication() {
        val stack = mutableListOf("playlist", "downloads", "details")

        stack.focusOrPush("downloads")

        assertEquals(listOf("playlist", "downloads"), stack)
    }
}
