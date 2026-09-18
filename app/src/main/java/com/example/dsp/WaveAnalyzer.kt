package com.example.dsp

import com.example.model.WaveAnalysisItem
import com.example.model.WaveAnalysisResult
import kotlin.math.abs
import kotlin.math.sqrt

object WaveAnalyzer {

    fun analyze(
        signal: FloatArray,
        peaks: IntArray,
        fs: Float
    ): WaveAnalysisResult {
        val nBeats = peaks.size
        if (nBeats < 3) {
            return WaveAnalysisResult(
                qrsDurationMs = 0f,
                prIntervalMs = 0f,
                qtIntervalMs = 0f,
                qtcBazettMs = 0f,
                stLevelMicrovolts = 0f,
                st80Microvolts = 0f,
                rAmplitudeMicrovolts = 0f,
                pWaveDurationMs = 0f,
                items = emptyList(),
                averageMorphology = FloatArray(0),
                sampleBeats = emptyList()
            )
        }

        val qrsList = ArrayList<Float>()
        val prList = ArrayList<Float>()
        val qtList = ArrayList<Float>()
        val stList = ArrayList<Float>()
        val st80List = ArrayList<Float>()
        val rAmpList = ArrayList<Float>()
        val pWaveList = ArrayList<Float>()

        val beatSnippets = ArrayList<FloatArray>()
        val halfWin = (0.4f * fs).toInt() // 400ms before and after R-peak

        // Delineate P, Q, R, S, T for each beat
        for (idx in 1 until nBeats - 1) {
            val r = peaks[idx]
            if (r - halfWin < 0 || r + halfWin >= signal.size) continue

            // Extract beat snippet for superimposed plot
            val snippet = FloatArray(halfWin * 2)
            for (k in 0 until halfWin * 2) {
                snippet[k] = signal[r - halfWin + k]
            }
            if (beatSnippets.size < 30) {
                beatSnippets.add(snippet)
            }

            // Fiducial identification
            // 1. Q wave: local minimum between R-70ms and R
            val qRangeStart = (r - (0.07f * fs).toInt()).coerceAtLeast(0)
            var qMinIdx = r
            var qMinVal = signal[r]
            for (k in qRangeStart until r) {
                if (signal[k] < qMinVal) {
                    qMinVal = signal[k]
                    qMinIdx = k
                }
            }

            // 2. S wave: local minimum between R and R+80ms
            val sRangeEnd = (r + (0.08f * fs).toInt()).coerceAtMost(signal.size - 1)
            var sMinIdx = r
            var sMinVal = signal[r]
            for (k in r..sRangeEnd) {
                if (signal[k] < sMinVal) {
                    sMinVal = signal[k]
                    sMinIdx = k
                }
            }

            // QRS Duration
            val qrsMs = ((sMinIdx - qMinIdx).coerceAtLeast(1) / fs) * 1000f
            qrsList.add(qrsMs)

            // 3. P wave: local maximum in PR window (R-240ms to R-80ms)
            val pStart = (r - (0.24f * fs).toInt()).coerceAtLeast(0)
            val pEnd = (r - (0.08f * fs).toInt()).coerceAtLeast(pStart + 1)
            var pMaxIdx = pStart
            var pMaxVal = -Float.MAX_VALUE
            for (k in pStart until pEnd) {
                if (signal[k] > pMaxVal) {
                    pMaxVal = signal[k]
                    pMaxIdx = k
                }
            }

            // Delineate real P-wave onset and offset baseline crossings
            val pIsoIndex = (r - (0.26f * fs).toInt()).coerceAtLeast(0)
            val pBaseline = signal[pIsoIndex]
            val pThresh = pBaseline + (pMaxVal - pBaseline) * 0.20f

            var pOnsetIdx = pMaxIdx
            var pOffsetIdx = pMaxIdx
            while (pOnsetIdx > pStart && signal[pOnsetIdx] > pThresh) pOnsetIdx--
            while (pOffsetIdx < pEnd && signal[pOffsetIdx] > pThresh) pOffsetIdx++

            val pDurMs = ((pOffsetIdx - pOnsetIdx).coerceAtLeast(1) / fs) * 1000f
            pWaveList.add(pDurMs)

            // PR Interval: P peak to Q peak
            val prMs = ((qMinIdx - pMaxIdx).coerceAtLeast(1) / fs) * 1000f
            prList.add(prMs.coerceIn(80f, 300f))

            // 4. T wave: local maximum in ST-T window (R+120ms to R+380ms)
            val tStart = (r + (0.12f * fs).toInt()).coerceAtMost(signal.size - 1)
            val tEnd = (r + (0.38f * fs).toInt()).coerceAtMost(signal.size - 1)
            var tMaxIdx = tStart
            var tMaxVal = -Float.MAX_VALUE
            for (k in tStart until tEnd) {
                if (signal[k] > tMaxVal) {
                    tMaxVal = signal[k]
                    tMaxIdx = k
                }
            }

            // QT Interval: Q to T offset
            val qtMs = ((tMaxIdx - qMinIdx + (0.06f * fs).toInt()) / fs) * 1000f
            qtList.add(qtMs.coerceIn(200f, 600f))

            // ST and ST80 deviation in microvolts
            val stMv = QrsDetector.measureStSegmentMv(signal, r, fs)
            stList.add(stMv * 1000f) // convert mV to µV

            val st80Idx = (r + (0.08f * fs).toInt()).coerceAtMost(signal.size - 1)
            val isoelectricIdx = (r - (0.08f * fs).toInt()).coerceAtLeast(0)
            val st80Uv = (signal[st80Idx] - signal[isoelectricIdx]) * 1000f
            st80List.add(st80Uv)

            // R Amplitude in µV
            rAmpList.add(abs(signal[r]) * 1000f)
        }

        if (qrsList.isEmpty()) {
            return WaveAnalysisResult(
                qrsDurationMs = 0f,
                prIntervalMs = 0f,
                qtIntervalMs = 0f,
                qtcBazettMs = 0f,
                stLevelMicrovolts = 0f,
                st80Microvolts = 0f,
                rAmplitudeMicrovolts = 0f,
                pWaveDurationMs = 0f,
                items = emptyList(),
                averageMorphology = FloatArray(0),
                sampleBeats = emptyList()
            )
        }

        val meanQrs = qrsList.average().toFloat()
        val meanPr = prList.average().toFloat()
        val meanQt = qtList.average().toFloat()
        val meanSt = stList.average().toFloat()
        val meanSt80 = st80List.average().toFloat()
        val meanR = rAmpList.average().toFloat()
        val meanP = pWaveList.average().toFloat()

        // Corrected QT (Bazett formula: QTc = QT / sqrt(RR))
        val avgRrSec = if (peaks.size > 1) ((peaks.last() - peaks.first()) / fs) / (peaks.size - 1) else 0.8f
        val qtcBazett = meanQt / sqrt(avgRrSec.coerceAtLeast(0.3f))

        // Normal range evaluation percentages
        val qrsInNormal = ((qrsList.count { it < 120f } * 100f) / qrsList.size).toInt()
        val prInNormal = ((prList.count { it in 120f..220f } * 100f) / prList.size).toInt()
        val qtInNormal = ((qtList.count { it < 450f } * 100f) / qtList.size).toInt()
        val stInNormal = ((stList.count { abs(it) <= 100f } * 100f) / stList.size).toInt()
        val st80InNormal = ((st80List.count { abs(it) <= 100f } * 100f) / st80List.size).toInt()
        val rInNormal = ((rAmpList.count { it < 2000f } * 100f) / rAmpList.size).toInt()
        val pInNormal = ((pWaveList.count { it < 120f } * 100f) / pWaveList.size).toInt()

        val items = listOf(
            WaveAnalysisItem(
                name = "QRS(ms)",
                normalRange = "<120ms",
                meanValue = "${meanQrs.toInt()}",
                percentInNormal = qrsInNormal,
                isWarning = meanQrs >= 120f || qrsInNormal < 80,
                description = "Ventricular depolarization duration. Prolonged QRS (>120ms) reflects bundle branch block, ventricular pacing, or ventricular ectopic origin."
            ),
            WaveAnalysisItem(
                name = "PR int.(ms)",
                normalRange = "[120-220]ms",
                meanValue = "${meanPr.toInt()}",
                percentInNormal = prInNormal,
                isWarning = meanPr < 120f || meanPr > 220f,
                description = "Atrioventricular conduction time from atrial depolarization onset to ventricular depolarization onset."
            ),
            WaveAnalysisItem(
                name = "QT int.(ms)",
                normalRange = "<450ms",
                meanValue = "${meanQt.toInt()}",
                percentInNormal = qtInNormal,
                isWarning = meanQt >= 450f,
                description = "Total ventricular electrical systole (depolarization and repolarization). Prolonged QT risks Torsades de Pointes."
            ),
            WaveAnalysisItem(
                name = "ST (µV)",
                normalRange = "± 0.1mV",
                meanValue = "${meanSt.toInt()}",
                percentInNormal = stInNormal,
                isWarning = abs(meanSt) > 100f,
                description = "ST segment deviation measured at J-point + 60ms. Elevation suggests transmural ischemia or pericarditis; depression reflects subendocardial ischemia."
            ),
            WaveAnalysisItem(
                name = "ST80 (µV)",
                normalRange = "± 0.1mV",
                meanValue = "${meanSt80.toInt()}",
                percentInNormal = st80InNormal,
                isWarning = abs(meanSt80) > 100f,
                description = "ST level measured strictly at J-point + 80ms relative to PR isoelectric baseline for clinical ischemic evaluation."
            ),
            WaveAnalysisItem(
                name = "R ampl(µV)",
                normalRange = "<2mV",
                meanValue = "${meanR.toInt()}",
                percentInNormal = rInNormal,
                isWarning = meanR >= 2000f,
                description = "Peak voltage of the primary ventricular depolarization vector. High voltage may indicate left ventricular hypertrophy (LVH)."
            ),
            WaveAnalysisItem(
                name = "P wave(ms)",
                normalRange = "<120ms",
                meanValue = "${meanP.toInt()}",
                percentInNormal = pInNormal,
                isWarning = meanP >= 120f,
                description = "Atrial depolarization duration. Prolongation indicates left or right atrial enlargement (P-mitrale / P-pulmonale)."
            )
        )

        // Compute average morphology waveform across beats
        val winLen = halfWin * 2
        val avgMorph = FloatArray(winLen)
        if (beatSnippets.isNotEmpty()) {
            for (i in 0 until winLen) {
                var s = 0f
                for (b in beatSnippets) {
                    s += b[i]
                }
                avgMorph[i] = s / beatSnippets.size
            }
        }

        return WaveAnalysisResult(
            qrsDurationMs = meanQrs,
            prIntervalMs = meanPr,
            qtIntervalMs = meanQt,
            qtcBazettMs = qtcBazett,
            stLevelMicrovolts = meanSt,
            st80Microvolts = meanSt80,
            rAmplitudeMicrovolts = meanR,
            pWaveDurationMs = meanP,
            items = items,
            averageMorphology = avgMorph,
            sampleBeats = beatSnippets
        )
    }
}
