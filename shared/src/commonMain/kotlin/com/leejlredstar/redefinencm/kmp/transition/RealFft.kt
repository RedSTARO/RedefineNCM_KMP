package com.leejlredstar.redefinencm.kmp.transition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Magnitude spectrum of a real frame, computed as a half-size complex FFT plus the standard
 * even/odd split, so a 1024-point frame costs a 512-point transform.
 *
 * One instance owns its scratch buffers and is not thread-safe; analysis creates one per pass.
 */
internal class RealFft(val size: Int) {
    init {
        require(size >= 4 && size and (size - 1) == 0) { "FFT size must be a power of two: $size" }
    }

    private val half = size / 2
    private val bitReverse = IntArray(half).also { table ->
        val bits = Int.SIZE_BITS - (half - 1).countLeadingZeroBits()
        for (i in 0 until half) {
            var reversed = 0
            var value = i
            repeat(bits) {
                reversed = (reversed shl 1) or (value and 1)
                value = value shr 1
            }
            table[i] = reversed
        }
    }
    private val halfCos = DoubleArray(half / 2) { cos(2.0 * PI * it / half) }
    private val halfSin = DoubleArray(half / 2) { -sin(2.0 * PI * it / half) }
    private val splitCos = DoubleArray(half + 1) { cos(2.0 * PI * it / size) }
    private val splitSin = DoubleArray(half + 1) { -sin(2.0 * PI * it / size) }
    private val re = DoubleArray(half)
    private val im = DoubleArray(half)

    /** Writes `|X[k]|` for `k in 0..size/2` into [out], scaled by [scale]. */
    fun magnitudes(frame: DoubleArray, out: DoubleArray, scale: Double = 1.0) {
        require(frame.size == size && out.size == half + 1)
        for (m in 0 until half) {
            val target = bitReverse[m]
            re[target] = frame[2 * m]
            im[target] = frame[2 * m + 1]
        }
        var length = 2
        while (length <= half) {
            val step = half / length
            val halfLength = length / 2
            var start = 0
            while (start < half) {
                for (j in 0 until halfLength) {
                    val wr = halfCos[j * step]
                    val wi = halfSin[j * step]
                    val a = start + j
                    val b = a + halfLength
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                start += length
            }
            length = length shl 1
        }
        for (k in 0..half) {
            val zr = re[k % half]
            val zi = im[k % half]
            val cr = re[(half - k) % half]
            val ci = -im[(half - k) % half]
            // Even part E = (Z[k] + conj(Z[N/2-k])) / 2, odd part O = (Z[k] - conj(Z[N/2-k])) / 2i.
            val er = (zr + cr) * 0.5
            val ei = (zi + ci) * 0.5
            val or = (zi - ci) * 0.5
            val oi = -(zr - cr) * 0.5
            val tr = or * splitCos[k] - oi * splitSin[k]
            val ti = or * splitSin[k] + oi * splitCos[k]
            val xr = er + tr
            val xi = ei + ti
            out[k] = sqrt(xr * xr + xi * xi) * scale
        }
    }
}
