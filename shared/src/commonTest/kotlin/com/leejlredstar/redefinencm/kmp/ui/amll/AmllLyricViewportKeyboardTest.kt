package com.leejlredstar.redefinencm.kmp.ui.amll

import kotlin.test.Test
import kotlin.test.assertEquals

class AmllLyricViewportKeyboardTest {
    @Test
    fun rovingFocusFollowsTheActiveLineBeforeTheUserNavigates() {
        assertEquals(
            expected = 7,
            actual = resolveAmllRovingKeyboardIndex(
                groupCount = 12,
                currentIndex = 0,
                activeIndex = 7,
                retainCurrentFocus = false,
            ),
        )
    }

    @Test
    fun playbackDoesNotMoveAKeyboardFocusOwnedByTheUser() {
        assertEquals(
            expected = 3,
            actual = resolveAmllRovingKeyboardIndex(
                groupCount = 12,
                currentIndex = 3,
                activeIndex = 8,
                retainCurrentFocus = true,
            ),
        )
    }

    @Test
    fun pendingRealFocusMoveTemporarilyWinsOverThePreviouslyFocusedLine() {
        assertEquals(
            expected = 4,
            actual = resolveAmllRovingFocusTargetIndex(
                groupCount = 12,
                keyboardIndex = 4,
                focusedIndex = 3,
                pendingFocusIndex = 4,
                activeIndex = 8,
            ),
        )
        assertEquals(
            expected = 3,
            actual = resolveAmllRovingFocusTargetIndex(
                groupCount = 12,
                keyboardIndex = 3,
                focusedIndex = 3,
                pendingFocusIndex = null,
                activeIndex = 8,
            ),
        )
    }

    @Test
    fun focusOutlineFollowsFocusVisibleRatherThanPointerFocus() {
        assertEquals(
            expected = true,
            actual = shouldShowAmllFocusOutline(
                renderedIndex = 3,
                focusedIndex = 3,
                focusVisible = true,
            ),
        )
        assertEquals(
            expected = false,
            actual = shouldShowAmllFocusOutline(
                renderedIndex = 3,
                focusedIndex = 3,
                focusVisible = false,
            ),
        )
        assertEquals(
            expected = false,
            actual = shouldShowAmllFocusOutline(
                renderedIndex = 4,
                focusedIndex = 3,
                focusVisible = true,
            ),
        )
    }

    @Test
    fun accessibilityLabelUsesTheRenderedDomLineOrder() {
        val main = AmllLyricLine(
            id = "main",
            startTimeMs = 1_000,
            endTimeMs = 2_000,
            mainText = "主",
            translatedText = "译",
            romanText = "yin",
            words = listOf(
                AmllLyricWord(
                    id = "main-word",
                    text = "主",
                    startTimeMs = 1_000,
                    endTimeMs = 2_000,
                ),
            ),
        )
        val background = AmllLyricLine(
            id = "background",
            startTimeMs = 900,
            endTimeMs = 2_000,
            mainText = "和",
            words = listOf(
                AmllLyricWord(
                    id = "background-word",
                    text = "和",
                    startTimeMs = 900,
                    endTimeMs = 2_000,
                ),
            ),
            isBackground = true,
        )

        assertEquals(
            expected = "跳转到歌词：和主译yin",
            actual = amllLyricGroupAccessibilityLabel(
                group = AmllLyricGroup(
                    id = "group",
                    mainLine = main,
                    backgroundLine = background,
                ),
                isNonDynamic = true,
            ),
        )
    }

    @Test
    fun directionKeyStartsFromTheActiveLine() {
        assertEquals(
            expected = 8,
            actual = moveAmllRovingKeyboardIndex(
                groupCount = 12,
                currentIndex = 0,
                activeIndex = 7,
                retainCurrentFocus = false,
                delta = 1,
            ),
        )
        assertEquals(
            expected = 6,
            actual = moveAmllRovingKeyboardIndex(
                groupCount = 12,
                currentIndex = 0,
                activeIndex = 7,
                retainCurrentFocus = false,
                delta = -1,
            ),
        )
    }

    @Test
    fun noActiveLineUsesTheDomFallbackAndAllIndexesStayBounded() {
        assertEquals(
            expected = 0,
            actual = resolveAmllRovingKeyboardIndex(
                groupCount = 5,
                currentIndex = 4,
                activeIndex = null,
                retainCurrentFocus = false,
            ),
        )
        assertEquals(
            expected = 4,
            actual = resolveAmllRovingKeyboardIndex(
                groupCount = 5,
                currentIndex = 99,
                activeIndex = 2,
                retainCurrentFocus = true,
            ),
        )
        assertEquals(
            expected = 0,
            actual = moveAmllRovingKeyboardIndex(
                groupCount = 5,
                currentIndex = 0,
                activeIndex = 0,
                retainCurrentFocus = false,
                delta = -1,
            ),
        )
    }
}
