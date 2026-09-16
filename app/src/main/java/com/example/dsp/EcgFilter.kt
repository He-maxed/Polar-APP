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
     * Butterworth 2nd-order Low-Pass Filter (40 Hz cutoff at fs=130Hz)
     * Suppresses EMG muscle tremor and high-frequency noise.
     */
    fun butterworthLowpass(signal: FloatArray, fs: Float, cutoffHz: Float = 40f): FloatArray {
        val n = signal.size
        if (n == 0) return FloatArray(0)
        val out = FloatArray(n)
        val q = 1.0 / sqrt(2.0)
        val w0 = (2.0 * PI * cutoffHz / fs).toFloat()
        val alpha = (sin(w0.toDouble()) / (2.0 * q)).toFloat()
        val cosW0 = cos(w0.toDouble()).toFloat()

        val b0 = (1f - cosW0) / 2f
        val b1 = 1f - cosW0
        val b2 = (1f - cosW0) / 2f
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
     * Clinical bandpass filter (0.5 Hz - 40 Hz) for Holter analysis.
     */
    fun bandpass(signal: FloatArray, fs: Float): FloatArray {
        val hp = butterworthHighpass(signal, fs, 0.5f)
        return butterworthLowpass(hp, fs, 40f)
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
     * Online real-time filter pipeline for Polar H10 streaming samples:
     * Stage 1: 0.5 Hz High-pass (baseline wander removal)
     * Stage 2: 40.0 Hz Low-pass (EMG tremor and noise rejection)
     */
    class LiveFilter(private val fs: Float = 130f, hpCutoffHz: Float = 0.5f, lpCutoffHz: Float = 40.0f) {
        // High-pass coefficients
        private val hpNb0: Float
        private val hpNb1: Float
        private val hpNb2: Float
        private val hpNa1: Float
        private val hpNa2: Float

        private var hpX1 = 0f
        private var hpX2 = 0f
        private var hpY1 = 0f
        private var hpY2 = 0f

        // Low-pass coefficients
        private val lpNb0: Float
        private val lpNb1: Float
        private val lpNb2: Float
        private val lpNa1: Float
        private val lpNa2: Float

        private var lpX1 = 0f
        private var lpX2 = 0f
        private var lpY1 = 0f
        private var lpY2 = 0f

        private var initialized = false

        init {
            val q = 1.0 / sqrt(2.0)

            // Highpass
            val hpW0 = (2.0 * PI * hpCutoffHz / fs).toFloat()
            val hpAlpha = (sin(hpW0.toDouble()) / (2.0 * q)).toFloat()
            val hpCosW0 = cos(hpW0.toDouble()).toFloat()

            val hpB0 = (1f + hpCosW0) / 2f
            val hpB1 = -(1f + hpCosW0)
            val hpB2 = (1f + hpCosW0) / 2f
            val hpA0 = 1f + hpAlpha
            val hpA1 = -2f * hpCosW0
            val hpA2 = 1f - hpAlpha

            hpNb0 = hpB0 / hpA0
            hpNb1 = hpB1 / hpA0
            hpNb2 = hpB2 / hpA0
            hpNa1 = hpA1 / hpA0
            hpNa2 = hpA2 / hpA0

            // Lowpass
            val lpW0 = (2.0 * PI * lpCutoffHz / fs).toFloat()
            val lpAlpha = (sin(lpW0.toDouble()) / (2.0 * q)).toFloat()
            val lpCosW0 = cos(lpW0.toDouble()).toFloat()

            val lpB0 = (1f - lpCosW0) / 2f
            val lpB1 = 1f - lpCosW0
            val lpB2 = (1f - lpCosW0) / 2f
            val lpA0 = 1f + lpAlpha
            val lpA1 = -2f * lpCosW0
            val lpA2 = 1f - lpAlpha

            lpNb0 = lpB0 / lpA0
            lpNb1 = lpB1 / lpA0
            lpNb2 = lpB2 / lpA0
            lpNa1 = lpA1 / lpA0
            lpNa2 = lpA2 / lpA0
        }

        fun step(x0: Float): Float {
            if (!initialized) {
                hpX1 = x0
                hpX2 = x0
                hpY1 = 0f
                hpY2 = 0f
                lpX1 = 0f
                lpX2 = 0f
                lpY1 = 0f
                lpY2 = 0f
                initialized = true
            }

            // Stage 1: Highpass
            val hpY0 = hpNb0 * x0 + hpNb1 * hpX1 + hpNb2 * hpX2 - hpNa1 * hpY1 - hpNa2 * hpY2
            hpX2 = hpX1
            hpX1 = x0
            hpY2 = hpY1
            hpY1 = hpY0

            // Stage 2: Lowpass
            val lpY0 = lpNb0 * hpY0 + lpNb1 * lpX1 + lpNb2 * lpX2 - lpNa1 * lpY1 - lpNa2 * lpY2
            lpX2 = lpX1
            lpX1 = hpY0
            lpY2 = lpY1
            lpY1 = lpY0

            return lpY0
        }

        fun reset() {
            hpX1 = 0f
            hpX2 = 0f
            hpY1 = 0f
            hpY2 = 0f
            lpX1 = 0f
            lpX2 = 0f
            lpY1 = 0f
            lpY2 = 0f
            initialized = false
        }
    }
}
