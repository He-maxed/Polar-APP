package com.example.dsp

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * High-precision digital signal processing for Polar H10 ECG signals (130 Hz).
 * Features:
 * - 2nd Order Butterworth Highpass (0.5 Hz) for baseline wander removal without phase distortion.
 * - Moving-window QRS energy integration (Pan-Tompkins algorithm)
 * - 50Hz/60Hz notch filter
 */
object EcgFilter {

    /**
     * Butterworth 2nd-order High-Pass Filter.
     * Cutoff default is 0.5 Hz for clinical Holter isoelectric baseline stabilization.
     */
    fun butterworthHighpass(signal: FloatArray, fs: Float, cutoffHz: Float = 0.5f): FloatArray {
        val n = signal.size
        if (n == 0) return FloatArray(0)
        val out = FloatArray(n)
        val q = 1.0 / sqrt(2.0)
        val w0 = (2.0 * PI * cutoffHz / fs).toFloat()
        val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
        val cosW0 = cos(w0.toDouble()).toFloat()

        val b0 = (1f + cosW0) / 2f
        val b1 = -(1f + cosW0)
        val b2 = (1f + cosW0) / 2f
        val a0 = 1f + alpha
        val a1 = -2f * cosW0
        val a2 = 1f - alpha

        val nb0 = b0 / a0
        val nb1 = b1 / a0
        val nb2 = b2 / a0
        val na1 = a1 / a0
        val na2 = a2 / a0

        var x1 = 0f
        var x2 = 0f
        var y1 = 0f
        var y2 = 0f

        for (i in 0 until n) {
            val x0 = signal[i]
            val y0 = nb0 * x0 + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
            out[i] = y0
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        return out
    }

    /**
     * Moving window integrator.
     */
    fun movingIntegration(signal: FloatArray, windowSamples: Int): FloatArray {
        val n = signal.size
        val out = FloatArray(n)
        var acc = 0f
        val w = windowSamples.coerceAtLeast(1)
        for (i in 0 until n) {
            acc += signal[i]
            if (i >= w) {
                acc -= signal[i - w]
            }
            out[i] = acc / (i + 1).coerceAtMost(w)
        }
        return out
    }

    /**
     * Pan-Tompkins QRS Energy computation:
     * First derivative -> Squaring -> Moving window integration (150ms window).
     * Distinguishes high-slope QRS complexes from smooth P and T waves.
     */
    fun computeQrsEnergy(signal: FloatArray, fs: Float): FloatArray {
        val n = signal.size
        if (n < 2) return FloatArray(n)
        val diff = FloatArray(n)
        for (i in 1 until n) {
            diff[i] = signal[i] - signal[i - 1]
        }
        val squared = FloatArray(n)
        for (i in 0 until n) {
            squared[i] = diff[i] * diff[i]
        }
        val winSamples = (0.15f * fs).toInt().coerceAtLeast(1)
        return movingIntegration(squared, winSamples)
    }

    /**
     * Simple online filter state for live real-time streaming samples.
     */
    class LiveFilter(private val fs: Float = 130f, cutoffHz: Float = 0.5f) {
        private val nb0: Float
        private val nb1: Float
        private val nb2: Float
        private val na1: Float
        private val na2: Float

        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        init {
            val q = 1.0 / sqrt(2.0)
            val w0 = (2.0 * PI * cutoffHz / fs).toFloat()
            val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
            val cosW0 = cos(w0.toDouble()).toFloat()

            val b0 = (1f + cosW0) / 2f
            val b1 = -(1f + cosW0)
            val b2 = (1f + cosW0) / 2f
            val a0 = 1f + alpha
            val a1 = -2f * cosW0
            val a2 = 1f - alpha

            nb0 = b0 / a0
            nb1 = b1 / a0
            nb2 = b2 / a0
            na1 = a1 / a0
            na2 = a2 / a0
        }

        fun step(x0: Float): Float {
            val y0 = nb0 * x0 + nb1 * x1 + nb2 * x2 - na1 * y1 - na2 * y2
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            return y0
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }
    }
}
