package com.example.dsp

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object ClinicalDatasetGenerator {

    /**
     * Generates a realistic ECG waveform snippet (mV) at 130 Hz.
     * Modeled with physiological Gaussian-derivative components for P, Q, R, S, T.
     */
    fun generateEcgWaveform(
        durationSeconds: Float,
        fs: Float = 130f,
        heartRateBpm: Float = 73f,
        includeExtrasystoles: Boolean = true
    ): FloatArray {
        val nSamples = (durationSeconds * fs).toInt()
        val signal = FloatArray(nSamples)

        val beatIntervalSamples = (fs * 60f / heartRateBpm).toInt()
        var nextBeatIdx = (0.2f * fs).toInt()
        var beatCounter = 0

        while (nextBeatIdx < nSamples - (0.5f * fs).toInt()) {
            beatCounter++
            val isCoupletStart = includeExtrasystoles && (beatCounter % 14 == 0)
            val isSveb = includeExtrasystoles && (beatCounter % 19 == 0)

            if (isCoupletStart && nextBeatIdx + beatIntervalSamples < nSamples) {
                // Generate VEB Couplet (2 consecutive ventricular premature beats)
                renderVebComplex(signal, nextBeatIdx, fs, isTall = true)
                val coupletGap = (0.34f * fs).toInt() // short coupling interval
                val secondVebIdx = nextBeatIdx + coupletGap
                renderVebComplex(signal, secondVebIdx, fs, isTall = true)

                // Full compensatory pause after couplet
                val compPause = (1.4f * beatIntervalSamples).toInt()
                nextBeatIdx = secondVebIdx + compPause
            } else if (isSveb) {
                // SVEB: Premature timing, normal narrow QRS, non-compensatory pause
                renderNormalSinusComplex(signal, nextBeatIdx, fs)
                val shortGap = (0.65f * beatIntervalSamples).toInt()
                nextBeatIdx += shortGap
            } else {
                // Normal Sinus Rhythm complex
                renderNormalSinusComplex(signal, nextBeatIdx, fs)
                nextBeatIdx += beatIntervalSamples
            }
        }

        // Add subtle physiological baseline wander and 50Hz noise
        for (i in 0 until nSamples) {
            val t = i / fs
            val respirationBaseline = 0.04f * sin(2 * PI * 0.25 * t).toFloat()
            signal[i] += respirationBaseline
        }

        return signal
    }

    private fun renderNormalSinusComplex(signal: FloatArray, rPeakIdx: Int, fs: Float) {
        val n = signal.size

        // P Wave: R - 160ms, duration 60ms, amp +0.15 mV
        addGaussianWave(signal, rPeakIdx - (0.16f * fs).toInt(), widthSamples = (0.04f * fs).toInt(), amplitude = 0.15f)

        // Q Wave: R - 35ms, amp -0.15 mV
        addGaussianWave(signal, rPeakIdx - (0.035f * fs).toInt(), widthSamples = (0.018f * fs).toInt(), amplitude = -0.18f)

        // R Peak: R, amp +0.85 mV
        addGaussianWave(signal, rPeakIdx, widthSamples = (0.022f * fs).toInt(), amplitude = 0.95f)

        // S Wave: R + 35ms, amp -0.35 mV
        addGaussianWave(signal, rPeakIdx + (0.035f * fs).toInt(), widthSamples = (0.022f * fs).toInt(), amplitude = -0.42f)

        // T Wave: R + 240ms, amp +0.28 mV
        addGaussianWave(signal, rPeakIdx + (0.24f * fs).toInt(), widthSamples = (0.08f * fs).toInt(), amplitude = 0.28f)
    }

    private fun renderVebComplex(signal: FloatArray, rPeakIdx: Int, fs: Float, isTall: Boolean) {
        // VEB (PVC): Absent P wave, wide bizarre QRS (>140ms), tall peak (1.6 - 1.9 mV as in screenshot), inverted T wave
        val rAmp = if (isTall) 1.82f else 1.45f
        val qrsWidth = (0.075f * fs).toInt() // broad QRS

        // Broad initial slurring / notched wave
        addGaussianWave(signal, rPeakIdx - (0.04f * fs).toInt(), widthSamples = (0.035f * fs).toInt(), amplitude = -0.65f)

        // High voltage ectopic R peak
        addGaussianWave(signal, rPeakIdx, widthSamples = qrsWidth, amplitude = rAmp)

        // Deep broad S wave
        addGaussianWave(signal, rPeakIdx + (0.06f * fs).toInt(), widthSamples = (0.045f * fs).toInt(), amplitude = -0.75f)

        // Discordant inverted T wave
        addGaussianWave(signal, rPeakIdx + (0.22f * fs).toInt(), widthSamples = (0.09f * fs).toInt(), amplitude = -0.32f)
    }

    private fun addGaussianWave(signal: FloatArray, centerIdx: Int, widthSamples: Int, amplitude: Float) {
        val n = signal.size
        val w = widthSamples.coerceAtLeast(1)
        val start = (centerIdx - w * 3).coerceAtLeast(0)
        val end = (centerIdx + w * 3).coerceAtMost(n - 1)

        for (i in start..end) {
            val dist = (i - centerIdx).toFloat() / w.toFloat()
            val g = exp((-0.5 * dist * dist).toDouble()).toFloat()
            signal[i] += amplitude * g
        }
    }
}
