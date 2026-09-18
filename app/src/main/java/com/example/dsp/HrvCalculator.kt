package com.example.dsp

import com.example.model.HrvResult
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

object HrvCalculator {

    /**
     * Computes all clinical HRV time-domain parameters from RR intervals.
     * Also extracts ECG-Derived Respiration (EDR) based on R-wave amplitude modulations.
     */
    fun calculate(
        rrIntervalsMs: FloatArray,
        timestampsMs: LongArray,
        rAmplitudes: FloatArray
    ): HrvResult {
        // Filter out extreme artifacts (valid sinus range 300ms - 2000ms)
        val validRr = ArrayList<Float>()
        val validTimes = ArrayList<Long>()
        val validAmps = ArrayList<Float>()

        val minInputLen = minOf(rrIntervalsMs.size, timestampsMs.size, rAmplitudes.size)
        for (i in 0 until minInputLen) {
            val r = rrIntervalsMs[i]
            if (r in 300f..2000f) {
                validRr.add(r)
                validTimes.add(timestampsMs[i])
                validAmps.add(rAmplitudes[i])
            }
        }

        if (validRr.size < 4) {
            return HrvResult(
                rmssdMs = 0f,
                sdnnMs = 0f,
                meanRrMs = 0f,
                averageHrBpm = 0f,
                pnn50Percent = 0f,
                lnRmssd = 0f,
                averageRespiratoryRateBpm = 0f,
                respirationTimeSeries = emptyList()
            )
        }

        // 1. Mean RR & Average Heart Rate
        val meanRr = validRr.average().toFloat()
        val avgHr = if (meanRr > 0) 60000f / meanRr else 0f

        // 2. SDNN (Standard Deviation of NN intervals)
        var varSum = 0.0
        for (r in validRr) {
            val diff = r - meanRr
            varSum += diff * diff
        }
        val sdnn = sqrt(varSum / (validRr.size - 1)).toFloat()

        // 3. RMSSD (Root Mean Square of Successive Differences)
        var sqDiffSum = 0.0
        var count50 = 0
        val nDiffs = validRr.size - 1

        for (i in 0 until nDiffs) {
            val diff = validRr[i + 1] - validRr[i]
            sqDiffSum += diff * diff
            if (abs(diff) > 50f) {
                count50++
            }
        }

        val rmssd = if (nDiffs > 0) sqrt(sqDiffSum / nDiffs).toFloat() else 0f
        val pnn50 = if (nDiffs > 0) (count50.toFloat() / nDiffs.toFloat()) * 100f else 0f
        val lnRmssd = if (rmssd > 0) ln(rmssd.toDouble()).toFloat() else 0f

        // 4. ECG-Derived Respiration (EDR)
        val respirationSeries = ArrayList<Pair<Long, Float>>()
        val windowSize = 15 // 15 beats rolling window
        var avgRespRate = 0f

        val minLenValid = minOf(validAmps.size, validTimes.size, validRr.size)
        if (minLenValid >= windowSize) {
            val respRates = ArrayList<Float>()
            var i = 0
            while (i <= minLenValid - windowSize) {
                var zeroCrossings = 0
                val sub = validAmps.subList(i, i + windowSize)
                val subMean = sub.average().toFloat()
                for (k in 1 until sub.size) {
                    val prev = sub[k - 1] - subMean
                    val curr = sub[k] - subMean
                    if ((prev < 0 && curr >= 0) || (prev > 0 && curr <= 0)) {
                        zeroCrossings++
                    }
                }
                val winDurSec = (validRr.subList(i, i + windowSize).sum()) / 1000f
                val rateBpm = if (winDurSec > 0) {
                    ((zeroCrossings / 2f) / winDurSec) * 60f
                } else 0f

                val clampedRate = rateBpm.coerceIn(0f, 40f)
                if (clampedRate > 0) {
                    respRates.add(clampedRate)
                    val t = if (i < validTimes.size) validTimes[i] else System.currentTimeMillis()
                    respirationSeries.add(Pair(t, clampedRate))
                }
                i += 5
            }
            if (respRates.isNotEmpty()) {
                avgRespRate = respRates.average().toFloat()
            }
        }

        return HrvResult(
            rmssdMs = rmssd,
            sdnnMs = sdnn,
            meanRrMs = meanRr,
            averageHrBpm = avgHr,
            pnn50Percent = pnn50,
            lnRmssd = lnRmssd,
            averageRespiratoryRateBpm = avgRespRate,
            respirationTimeSeries = respirationSeries
        )
    }
}
