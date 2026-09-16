package com.example.dsp

import com.example.model.PhysicalActivityData

object ActivityProcessor {

    /**
     * Processes Polar ACC accelerometer samples (X, Y, Z in mg/g) to compute step count,
     * distance, velocity (km/h), cadence (/min), and extrasystole zone distribution.
     */
    fun process(
        accelMag: FloatArray,
        timestampsMs: LongArray,
        svebCount: Int,
        vebCount: Int
    ): PhysicalActivityData {
        if (accelMag.size < 4 || timestampsMs.isEmpty()) {
            return PhysicalActivityData(
                steps = 0,
                distanceMeters = 0,
                currentVelocityKmh = 0f,
                averageCadenceRpm = 0,
                velocityTimeSeries = emptyList(),
                cadenceTimeSeries = emptyList(),
                extrasystoleRestPercent = 0,
                extrasystoleRecoveryPercent = 0
            )
        }

        // 1. Peak detection in acceleration magnitude for steps
        var steps = 0
        val thresh = 1.15f // in g
        for (i in 1 until accelMag.size - 1) {
            if (accelMag[i] > thresh && accelMag[i] > accelMag[i - 1] && accelMag[i] > accelMag[i + 1]) {
                steps++
            }
        }

        val totalDurationHours = if (timestampsMs.size > 1) {
            (timestampsMs.last() - timestampsMs.first()) / 3600000f
        } else 0f

        val strideLengthMeters = 0.75f
        val distanceMeters = (steps * strideLengthMeters).toInt()
        val velocityKmh = if (totalDurationHours > 0) (distanceMeters / 1000f) / totalDurationHours else 0f
        val cadence = if (totalDurationHours > 0) (steps / (totalDurationHours * 60f)).toInt() else 0

        // Velocity & Cadence time series for chart
        val velSeries = ArrayList<Pair<Long, Float>>()
        val cadSeries = ArrayList<Pair<Long, Float>>()
        val chunkSize = (accelMag.size / 30).coerceAtLeast(10)

        var idx = 0
        while (idx < accelMag.size - chunkSize) {
            var chunkSteps = 0
            val startK = idx.coerceAtLeast(1)
            val endK = (idx + chunkSize).coerceAtMost(accelMag.size - 2)
            for (k in startK..endK) {
                if (accelMag[k] > thresh && accelMag[k] > accelMag[k - 1] && accelMag[k] > accelMag[k + 1]) {
                    chunkSteps++
                }
            }

            val t = if (timestampsMs.isNotEmpty()) {
                val tIdx = ((idx.toFloat() / accelMag.size.toFloat()) * (timestampsMs.size - 1))
                    .toInt()
                    .coerceIn(0, timestampsMs.size - 1)
                timestampsMs[tIdx]
            } else {
                System.currentTimeMillis()
            }

            val chunkMinutes = 2.5f
            val localCadence = (chunkSteps / chunkMinutes) * 60f
            val localVel = (chunkSteps * strideLengthMeters / 1000f) / (chunkMinutes / 60f)
            velSeries.add(Pair(t, localVel.coerceIn(0f, 15f)))
            cadSeries.add(Pair(t, localCadence.coerceIn(0f, 160f)))
            idx += chunkSize
        }

        val totalExtrasystoles = svebCount + vebCount
        val restPercent = if (totalExtrasystoles > 0) ((vebCount.toFloat() / totalExtrasystoles.toFloat()) * 100).toInt() else 0
        val recoveryPercent = if (totalExtrasystoles > 0) 100 - restPercent else 0

        return PhysicalActivityData(
            steps = steps,
            distanceMeters = distanceMeters,
            currentVelocityKmh = velocityKmh,
            averageCadenceRpm = cadence,
            velocityTimeSeries = velSeries,
            cadenceTimeSeries = cadSeries,
            extrasystoleRestPercent = restPercent,
            extrasystoleRecoveryPercent = recoveryPercent
        )
    }
}
