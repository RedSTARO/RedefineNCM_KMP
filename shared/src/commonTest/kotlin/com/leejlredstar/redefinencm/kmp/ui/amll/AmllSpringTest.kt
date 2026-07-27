/*
 * Numerical regression tests for the Kotlin translation of
 * @applemusic-like-lyrics/core 0.5.2 spring.ts and derivative.ts.
 * Test adaptation dated 2026-07-26.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.leejlredstar.redefinencm.kmp.ui.amll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmllSpringTest {

    @Test
    fun underdampedAnalyticSolutionMatchesCore052GoldenValues() {
        val solver = solveAmllSpringPosition(
            from = 0.0,
            velocity = 0.0,
            to = 100.0,
        )

        assertClose(0.0, solver(0.0))
        assertClose(10.440547345507952, solver(0.05))
        assertClose(34.02998466082984, solver(0.1))
        assertClose(84.94256348541126, solver(0.2))
        assertClose(107.45905665950333, solver(0.5))
        assertClose(100.21701167393262, solver(1.0))
    }

    @Test
    fun criticalBranchAndDelayMatchCore052GoldenValues() {
        val solver = solveAmllSpringPosition(
            from = 10.0,
            velocity = 5.0,
            to = 50.0,
            delaySeconds = 0.2,
            parameters = AmllSpringParameters(
                mass = 1.0,
                damping = 20.0,
                stiffness = 100.0,
            ),
        )

        assertClose(10.0, solver(0.1))
        assertClose(10.0, solver(0.2))
        assertClose(20.753584426870326, solver(0.3))
        assertClose(42.108749663693565, solver(0.5))
    }

    @Test
    fun softFlagSelectsTheSameNonOscillatingBranch() {
        val soft = solveAmllSpringPosition(
            from = 0.0,
            velocity = 0.0,
            to = 100.0,
            parameters = AmllSpringParameters(
                damping = 1.0,
                stiffness = 100.0,
                soft = true,
            ),
        )
        val criticalShape = solveAmllSpringPosition(
            from = 0.0,
            velocity = 0.0,
            to = 100.0,
            parameters = AmllSpringParameters(
                damping = 20.0,
                stiffness = 100.0,
            ),
        )

        assertClose(criticalShape(0.1), soft(0.1))
        assertClose(criticalShape(0.5), soft(0.5))
    }

    @Test
    fun springUpdateUsesSecondsAndRetargetsFromCurrentPosition() {
        val spring = AmllSpring(0.0)
        spring.setTargetPosition(100.0)

        spring.update(0.1)
        assertClose(34.02998466082984, spring.currentPosition)
        assertFalse(spring.arrived())

        spring.setTargetPosition(50.0)
        val positionAtRetarget = spring.currentPosition
        spring.update(0.0)
        assertClose(positionAtRetarget, spring.currentPosition, tolerance = 1e-9)

        repeat(200) {
            spring.update(0.05)
        }
        assertClose(50.0, spring.currentPosition, tolerance = 0.01)
        assertTrue(spring.arrived())
    }

    @Test
    fun delayedTargetIsAppliedAfterTheSourceOrderedFrameEvaluation() {
        val spring = AmllSpring(0.0)
        spring.setTargetPosition(position = 100.0, delaySeconds = 0.1)

        spring.update(0.05)
        assertEquals(0.0, spring.currentPosition)
        spring.update(0.05)
        assertEquals(0.0, spring.currentPosition)
        spring.update(0.1)
        assertClose(34.02998466082984, spring.currentPosition)
    }

    @Test
    fun setPositionSnapsWithoutRemovingAQueuedTarget() {
        val spring = AmllSpring(0.0)
        spring.setTargetPosition(position = 100.0, delaySeconds = 0.1)
        spring.setPosition(25.0)

        spring.update(0.1)
        assertEquals(25.0, spring.currentPosition)
        spring.update(0.1)
        assertTrue(spring.currentPosition > 25.0)
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-12,
    ) {
        assertEquals(
            expected = expected,
            actual = actual,
            absoluteTolerance = tolerance,
        )
    }
}
