/*
 * Derived from @applemusic-like-lyrics/core 0.5.2:
 * packages/core/src/utils/spring.ts and packages/core/src/utils/derivative.ts.
 * Kotlin Multiplatform translation and modifications dated 2026-07-26.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * This derivative file is licensed under the GNU Affero General Public License v3.0 only.
 * Upstream source: https://github.com/amll-dev/applemusic-like-lyrics
 */

package com.leejlredstar.redefinencm.kmp.ui.amll

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.E
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A Kotlin representation of TypeScript's `Partial<SpringParams>`.
 *
 * Null properties are deliberately resolved by [solveAmllSpringPosition] to the same defaults
 * used by AMLL core 0.5.2: mass 1, damping 10, stiffness 100, and soft false.
 */
internal data class AmllSpringParameters(
    val mass: Double? = null,
    val damping: Double? = null,
    val stiffness: Double? = null,
    val soft: Boolean? = null,
)

/**
 * Analytic spring used by AMLL. Time and update deltas are expressed in seconds.
 *
 * The implementation intentionally retains the exact 0.5.2 state transition order, including
 * its queued-parameter behavior and its signed velocity/acceleration arrival checks.
 */
internal class AmllSpring(currentPosition: Double = 0.0) {
    private var currentPositionValue = currentPosition
    private var targetPosition = currentPosition
    private var currentTimeSeconds = 0.0
    private var parameters = AmllSpringParameters()
    private var currentSolver: (Double) -> Double = { targetPosition }
    private var velocityAt: (Double) -> Double = { 0.0 }
    private var accelerationAt: (Double) -> Double = { 0.0 }
    private var queuedParameters: QueuedParameters? = null
    private var queuedPosition: QueuedPosition? = null
    private var isAtRest = true

    val currentPosition: Double
        get() = currentPositionValue

    private fun resetSolver() {
        val currentVelocity = velocityAt(currentTimeSeconds)
        currentTimeSeconds = 0.0
        currentSolver = solveAmllSpringPosition(
            from = currentPositionValue,
            velocity = currentVelocity,
            to = targetPosition,
            delaySeconds = 0.0,
            parameters = parameters,
        )
        velocityAt = amllDerivative(currentSolver)
        accelerationAt = amllDerivative(velocityAt)
        isAtRest = false
    }

    fun arrived(): Boolean =
        abs(targetPosition - currentPositionValue) < ARRIVAL_EPSILON &&
            velocityAt(currentTimeSeconds) < ARRIVAL_EPSILON &&
            accelerationAt(currentTimeSeconds) < ARRIVAL_EPSILON &&
            queuedParameters == null &&
            queuedPosition == null

    fun setPosition(position: Double) {
        targetPosition = position
        currentPositionValue = position
        currentSolver = { targetPosition }
        velocityAt = { 0.0 }
        accelerationAt = { 0.0 }
        isAtRest = true
    }

    fun update(deltaSeconds: Double = 0.0): Boolean {
        if (
            isAtRest &&
            queuedParameters == null &&
            queuedPosition == null
        ) {
            return false
        }
        val previousPosition = currentPositionValue
        currentTimeSeconds += deltaSeconds
        currentPositionValue = currentSolver(currentTimeSeconds)

        queuedParameters?.let { queued ->
            queued.remainingSeconds -= deltaSeconds
            if (queued.remainingSeconds <= 0.0) {
                /*
                 * AMLL 0.5.2 calls updateParams here without clearing queueParams. Retaining that
                 * detail is necessary for a literal engine port, even though it means a delayed
                 * parameter update is re-applied by subsequent frames.
                 */
                updateParameters(queued.parameters)
            }
        }

        queuedPosition?.let { queued ->
            queued.remainingSeconds -= deltaSeconds
            if (queued.remainingSeconds <= 0.0) {
                setTargetPosition(queued.position)
            }
        }

        if (arrived()) {
            setPosition(targetPosition)
        }
        return currentPositionValue != previousPosition
    }

    fun updateParameters(
        newParameters: AmllSpringParameters,
        delaySeconds: Double = 0.0,
    ) {
        if (delaySeconds > 0.0) {
            /*
             * spring.ts spreads queuePosition (not queueParams) before the supplied values.
             * queuePosition has no SpringParams keys, so the observable parameter result is the
             * supplied partial object with a fresh delay.
             */
            queuedParameters = QueuedParameters(
                parameters = newParameters,
                remainingSeconds = delaySeconds,
            )
        } else {
            // This is queuePosition in upstream 0.5.2, not queueParams.
            queuedPosition = null
            parameters = parameters.mergedWith(newParameters)
            resetSolver()
        }
    }

    fun setTargetPosition(
        position: Double,
        delaySeconds: Double = 0.0,
    ) {
        if (delaySeconds > 0.0) {
            queuedPosition = QueuedPosition(
                position = position,
                remainingSeconds = delaySeconds,
            )
        } else {
            queuedPosition = null
            targetPosition = position
            resetSolver()
        }
    }

    private data class QueuedParameters(
        val parameters: AmllSpringParameters,
        var remainingSeconds: Double,
    )

    private data class QueuedPosition(
        val position: Double,
        var remainingSeconds: Double,
    )

    private companion object {
        const val ARRIVAL_EPSILON = 0.01
    }
}

/**
 * Direct translation of AMLL's private `solveSpring`.
 */
internal fun solveAmllSpringPosition(
    from: Double,
    velocity: Double,
    to: Double,
    delaySeconds: Double = 0.0,
    parameters: AmllSpringParameters = AmllSpringParameters(),
): (Double) -> Double {
    val soft = parameters.soft ?: false
    val stiffness = parameters.stiffness ?: 100.0
    val damping = parameters.damping ?: 10.0
    val mass = parameters.mass ?: 1.0
    val delta = to - from

    if (soft || 1.0 <= damping / (2.0 * sqrt(stiffness * mass))) {
        val angularFrequency = -sqrt(stiffness / mass)
        val leftover = -angularFrequency * delta - velocity
        return { inputSeconds ->
            val timeSeconds = inputSeconds - delaySeconds
            if (timeSeconds < 0.0) {
                from
            } else {
                to -
                    (delta + timeSeconds * leftover) *
                    E.pow(timeSeconds * angularFrequency)
            }
        }
    }

    val dampingFrequency = sqrt(4.0 * mass * stiffness - damping * damping)
    val leftover = (damping * delta - 2.0 * mass * velocity) / dampingFrequency
    val dampedFrequencyPerMass = (0.5 * dampingFrequency) / mass
    val dampingPerMass = -(0.5 * damping) / mass

    return { inputSeconds ->
        val timeSeconds = inputSeconds - delaySeconds
        if (timeSeconds < 0.0) {
            from
        } else {
            to -
                (
                    cos(timeSeconds * dampedFrequencyPerMass) * delta +
                        sin(timeSeconds * dampedFrequencyPerMass) * leftover
                    ) *
                E.pow(timeSeconds * dampingPerMass)
        }
    }
}

private fun amllDerivative(function: (Double) -> Double): (Double) -> Double = { input ->
    (function(input + DERIVATIVE_STEP) - function(input - DERIVATIVE_STEP)) /
        (2.0 * DERIVATIVE_STEP)
}

private fun AmllSpringParameters.mergedWith(
    other: AmllSpringParameters,
): AmllSpringParameters = AmllSpringParameters(
    mass = other.mass ?: mass,
    damping = other.damping ?: damping,
    stiffness = other.stiffness ?: stiffness,
    soft = other.soft ?: soft,
)

private const val DERIVATIVE_STEP = 0.001
