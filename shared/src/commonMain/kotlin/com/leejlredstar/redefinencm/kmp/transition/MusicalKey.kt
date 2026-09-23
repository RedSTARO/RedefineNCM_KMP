package com.leejlredstar.redefinencm.kmp.transition

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A section's most likely key, from its pitch-class energy correlated against the
 * Krumhansl-Kessler major and minor profiles.
 *
 * This is the classic estimator, right on roughly two songs in three for pop; [strength] (the
 * winning correlation) lets the planner trust it only when it is clear. It decides one thing:
 * how long two tracks may sound together. Compatible keys may overlap for bars; clashing ones
 * hand over quickly so the clash is short.
 */
@Serializable
data class MusicalKey(
    /** 0 = C … 11 = B. */
    val tonic: Int,
    val minor: Boolean,
    val strength: Double,
) {
    /** Position on the Camelot wheel, 1..12; relative major and minor share a number. */
    val camelotNumber: Int
        get() {
            // Camelot 8B is C major and 8A is A minor; each step clockwise is a fifth up.
            val majorTonic = if (minor) (tonic + 3) % 12 else tonic
            val fifthsFromC = (majorTonic * 7) % 12
            return (fifthsFromC + 7) % 12 + 1
        }

    override fun toString(): String {
        val names = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        return names[tonic] + if (minor) "m" else ""
    }
}

/**
 * Harmonic distance on the Camelot wheel: 0 for the same key or its relative, 1 for a
 * neighbouring fifth (with or without the mode switch counted as free, as DJs treat it), more
 * for keys that clash.
 */
fun camelotDistance(a: MusicalKey, b: MusicalKey): Int {
    val d = abs(a.camelotNumber - b.camelotNumber)
    val around = minOf(d, 12 - d)
    return if (a.minor == b.minor || around == 0) around else around + 1
}

fun estimateKey(chroma: DoubleArray): MusicalKey? {
    require(chroma.size == 12)
    val total = chroma.sum()
    if (total <= 0.0) return null
    var best: MusicalKey? = null
    for (tonic in 0 until 12) {
        for (minor in listOf(false, true)) {
            val profile = if (minor) MINOR_PROFILE else MAJOR_PROFILE
            val rotated = DoubleArray(12) { profile[(it - tonic + 12) % 12] }
            val r = pearson(chroma, rotated)
            if (best == null || r > best.strength) best = MusicalKey(tonic, minor, r)
        }
    }
    return best
}

private fun pearson(x: DoubleArray, y: DoubleArray): Double {
    val mx = x.average()
    val my = y.average()
    var sxy = 0.0
    var sxx = 0.0
    var syy = 0.0
    for (i in x.indices) {
        val dx = x[i] - mx
        val dy = y[i] - my
        sxy += dx * dy
        sxx += dx * dx
        syy += dy * dy
    }
    return if (sxx <= 0.0 || syy <= 0.0) 0.0 else sxy / sqrt(sxx * syy)
}

private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
