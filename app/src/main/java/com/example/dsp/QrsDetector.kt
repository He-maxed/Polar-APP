package com.example.dsp

import kotlin.math.abs

object QrsDetector {

    /**
     * Adaptive R-peak detector.
     * Recomputes dynamic detection threshold in 8-second sliding blocks from energy.
     * Adapts to signal amplitude variation and avoids threshold collapse over long recordings.
     */
    fun detectRPeaks(signal: FloatArray, fs: Float): IntArray {
        val n = signal.size
        if (n < (fs * 1.0f).toInt()) return IntArray(0)

        val energy = EcgFilter.computeQrsEnergy(signal, fs)
        val blockLen = (8f * fs).toInt().coerceAtLeast(1)
        val numBlocks = ((n + blockLen - 1) / blockLen).coerceAtLeast(1)
        val blockMax = FloatArray(numBlocks)

        for (b in 0 until numBlocks) {
            val s = b * blockLen
            val e = (s + blockLen).coerceAtMost(n)
            var mx = 0f
            for (i in s until e) {
                if (energy[i] > mx) mx = energy[i]
            }
            blockMax[b] = mx
        }

        val sortedMax = blockMax.clone().apply { sort() }
        val medianBlockMax = if (sortedMax.isNotEmpty()) sortedMax[sortedMax.size / 2] else 0f
        val floorThresh = 0.12f * medianBlockMax

        val refractorySamples = (0.26f * fs).toInt()
        val peaks = ArrayList<Int>()
        var lastPeak = -refractorySamples
        var prevBlockMax = if (blockMax.isNotEmpty()) blockMax[0] else 0f

        for (b in 0 until numBlocks) {
            val s = b * blockLen
            val e = (s + blockLen).coerceAtMost(n)
            val localThresh = (0.35f * prevBlockMax).coerceAtLeast(floorThresh)

            val startI = s.coerceAtLeast(1)
            val endI = (e - 1).coerceAtMost(n - 2)

            for (i in startI..endI) {
                if (energy[i] > localThresh && energy[i] >= energy[i - 1] && energy[i] >= energy[i + 1]) {
                    if (i - lastPeak >= refractorySamples) {
                        // Find local extreme in raw/filtered signal around peak
                        val win = (0.06f * fs).toInt()
                        var best = i
                        var bestAbsV = abs(signal[i])
                        val minK = (i - win).coerceAtLeast(0)
                        val maxK = (i + win).coerceAtMost(n - 1)
                        for (k in minK..maxK) {
                            val v = abs(signal[k])
                            if (v > bestAbsV) {
                                bestAbsV = v
                                best = k
                            }
                        }
                        peaks.add(best)
                        lastPeak = best
                    }
                }
            }
            if (blockMax[b] > 0f) {
                prevBlockMax = blockMax[b]
            }
        }

        return peaks.toIntArray()
    }

    /**
     * Estimates QRS complex width (ms) by tracking Q onset and S offset crossings at 25% amplitude.
     */
    fun estimateQrsWidthMs(signal: FloatArray, peakIdx: Int, fs: Float): Float {
        val n = signal.size
        if (peakIdx !in 0 until n) return 0f

        val winSamples = (0.12f * fs).toInt()
        val peakVal = signal[peakIdx]
        val thresh = abs(peakVal) * 0.25f

        var left = peakIdx
        var right = peakIdx
        val lo = (peakIdx - winSamples).coerceAtLeast(0)
        val hi = (peakIdx + winSamples).coerceAtMost(n - 1)

        while (left > lo && abs(signal[left]) > thresh) left--
        while (right < hi && abs(signal[right]) > thresh) right++

        return ((right - left) / fs) * 1000f
    }

    /**
     * Measures ST segment deviation (in millivolts) at J + 80ms relative to isoelectric baseline.
     */
    fun measureStSegmentMv(signal: FloatArray, peakIdx: Int, fs: Float): Float {
        val n = signal.size
        val baseOffset = (0.08f * fs).toInt() // 80ms before peak (PR segment)
        val stOffset = (0.08f * fs).toInt()   // 80ms after peak (ST80)
        val win = (0.015f * fs).toInt().coerceAtLeast(1)

        val bIdx = peakIdx - baseOffset
        val sIdx = peakIdx + stOffset

        if (bIdx - win < 0 || sIdx + win >= n) return 0f

        var bSum = 0f
        var bCount = 0
        for (k in (bIdx - win)..(bIdx + win)) {
            bSum += signal[k]
            bCount++
        }
        val baseline = if (bCount > 0) bSum / bCount else 0f

        var sSum = 0f
        var sCount = 0
        for (k in (sIdx - win)..(sIdx + win)) {
            sSum += signal[k]
            sCount++
        }
        val stVal = if (sCount > 0) sSum / sCount else 0f

        return stVal - baseline
    }
}
