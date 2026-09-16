package com.example.dsp

import com.example.model.BeatAnnotation
import com.example.model.BeatType
import com.example.model.RhythmEvent
import com.example.model.RhythmEventType
import kotlin.math.abs
import kotlin.math.sqrt

object BeatClassifier {

    private const val PREMATURITY_RATIO = 0.82f
    private const val COMPENSATORY_RATIO = 1.15f
    private const val WIDE_QRS_MS = 120f
    private const val AFIB_IRREG_THRESH = 0.20f
    private const val AFIB_MIN_MS = 30000L
    private const val PAUSE_MS = 2500f
    private const val ST_DEP_THRESH_MV = -0.10f // -1.0 mm at 10mm/mV
    private const val ST_ELEV_THRESH_MV = 0.10f // +1.0 mm

    data class ClassificationResult(
        val annotations: List<BeatAnnotation>,
        val events: List<RhythmEvent>,
        val svebCount: Int,
        val vebCount: Int,
        val coupletCount: Int,
        val tripletCount: Int,
        val pauseCount: Int,
        val afibCount: Int
    )

    fun classify(
        peaks: IntArray,
        signal: FloatArray,
        fs: Float,
        startTimestampMs: Long
    ): ClassificationResult {
        val n = peaks.size
        if (n == 0) {
            return ClassificationResult(emptyList(), emptyList(), 0, 0, 0, 0, 0, 0)
        }

        val rr = FloatArray(n)
        val qrsWidth = FloatArray(n)
        val stDev = FloatArray(n)
        val rAmp = FloatArray(n)
        val labels = Array(n) { BeatType.NORMAL }

        for (i in 0 until n) {
            val p = peaks[i]
            qrsWidth[i] = QrsDetector.estimateQrsWidthMs(signal, p, fs)
            stDev[i] = QrsDetector.measureStSegmentMv(signal, p, fs)
            rAmp[i] = abs(signal[p])
            rr[i] = if (i > 0) ((p - peaks[i - 1]) / fs) * 1000f else 0f
        }

        // Two-pass classification:
        // Pass 1: Tag premature beats and distinguish VEB vs SVEB based on width and compensatory pause
        for (i in 1 until n) {
            // Compute rolling 8-beat local baseline RR
            var sum = 0f
            var cnt = 0
            val startK = (i - 8).coerceAtLeast(1)
            for (k in startK until i) {
                if (rr[k] in 250f..2500f) {
                    sum += rr[k]
                    cnt++
                }
            }
            if (cnt < 2) continue

            val meanRR = sum / cnt
            val curRR = rr[i]

            if (curRR < meanRR * PREMATURITY_RATIO && curRR > 200f) {
                val nextRR = if (i + 1 < n) rr[i + 1] else null
                val isCompensatory = nextRR != null && nextRR > meanRR * COMPENSATORY_RATIO
                val isWide = qrsWidth[i] >= WIDE_QRS_MS

                if (isWide && (isCompensatory || nextRR == null)) {
                    labels[i] = BeatType.VEB
                } else if (!isWide) {
                    labels[i] = BeatType.SVEB
                } else {
                    labels[i] = BeatType.VEB
                }
            }
        }

        // Pass 2: Tag couplets, triplets, bigeminy, trigeminy, and rhythm events
        val isCouplet = BooleanArray(n)
        val isTriplet = BooleanArray(n)
        val isBigeminy = BooleanArray(n)
        val isTrigeminy = BooleanArray(n)

        val events = ArrayList<RhythmEvent>()
        var couplets = 0
        var triplets = 0

        // Couplets and runs
        var i = 0
        while (i < n) {
            if (labels[i] == BeatType.VEB) {
                var j = i
                while (j < n && labels[j] == BeatType.VEB) {
                    j++
                }
                val runLen = j - i
                if (runLen == 2) {
                    couplets++
                    isCouplet[i] = true
                    isCouplet[i + 1] = true
                    events.add(
                        RhythmEvent(
                            id = "ev_couplet_$i",
                            type = RhythmEventType.VEB_COUPLET,
                            startTimestampMs = startTimestampMs + ((peaks[i] / fs) * 1000).toLong(),
                            endTimestampMs = startTimestampMs + ((peaks[j - 1] / fs) * 1000).toLong(),
                            durationSeconds = ((peaks[j - 1] - peaks[i]) / fs),
                            details = "VEB Couplet (2 consecutive ventricular extrasystoles)",
                            sampleStartIndex = peaks[i],
                            sampleEndIndex = peaks[j - 1]
                        )
                    )
                } else if (runLen == 3) {
                    triplets++
                    isTriplet[i] = true
                    isTriplet[i + 1] = true
                    isTriplet[i + 2] = true
                    events.add(
                        RhythmEvent(
                            id = "ev_triplet_$i",
                            type = RhythmEventType.VEB_TRIPLET,
                            startTimestampMs = startTimestampMs + ((peaks[i] / fs) * 1000).toLong(),
                            endTimestampMs = startTimestampMs + ((peaks[j - 1] / fs) * 1000).toLong(),
                            durationSeconds = ((peaks[j - 1] - peaks[i]) / fs),
                            details = "VEB Triplet (3 consecutive ventricular extrasystoles)",
                            sampleStartIndex = peaks[i],
                            sampleEndIndex = peaks[j - 1]
                        )
                    )
                } else if (runLen >= 4) {
                    events.add(
                        RhythmEvent(
                            id = "ev_vtrun_$i",
                            type = RhythmEventType.VEB_RUN,
                            startTimestampMs = startTimestampMs + ((peaks[i] / fs) * 1000).toLong(),
                            endTimestampMs = startTimestampMs + ((peaks[j - 1] / fs) * 1000).toLong(),
                            durationSeconds = ((peaks[j - 1] - peaks[i]) / fs),
                            details = "Possible Ventricular Tachycardia (Run of $runLen wide VEB beats)",
                            sampleStartIndex = peaks[i],
                            sampleEndIndex = peaks[j - 1]
                        )
                    )
                }
                i = j
            } else {
                i++
            }
        }

        // Check Bigeminy pattern: N-V-N-V-N-V
        for (idx in 0 until n - 5) {
            if (labels[idx] == BeatType.NORMAL && labels[idx + 1] == BeatType.VEB &&
                labels[idx + 2] == BeatType.NORMAL && labels[idx + 3] == BeatType.VEB &&
                labels[idx + 4] == BeatType.NORMAL && labels[idx + 5] == BeatType.VEB
            ) {
                for (k in idx..idx + 5) isBigeminy[k] = true
            }
        }

        // Pauses
        var pauseCount = 0
        for (idx in 1 until n) {
            if (rr[idx] >= PAUSE_MS) {
                pauseCount++
                val pauseStart = startTimestampMs + ((peaks[idx - 1] / fs) * 1000).toLong()
                val pauseEnd = startTimestampMs + ((peaks[idx] / fs) * 1000).toLong()
                events.add(
                    RhythmEvent(
                        id = "ev_pause_$idx",
                        type = RhythmEventType.PAUSE,
                        startTimestampMs = pauseStart,
                        endTimestampMs = pauseEnd,
                        durationSeconds = rr[idx] / 1000f,
                        details = "Sinus pause / arrest of ${(rr[idx] / 1000f)} seconds",
                        sampleStartIndex = peaks[idx - 1],
                        sampleEndIndex = peaks[idx]
                    )
                )
            }
        }

        // Scan AFib: rolling RR irregularity
        var afibCount = 0
        var inAfib = false
        var afibStartIdx = 0
        for (idx in 6 until n) {
            var sumR = 0f
            for (k in (idx - 5)..idx) sumR += rr[k]
            val meanR = sumR / 6f

            var sq = 0f
            for (k in (idx - 4)..idx) {
                val d = rr[k] - rr[k - 1]
                sq += d * d
            }
            val rmssd = sqrt(sq / 5f)
            val irregIndex = if (meanR > 0) rmssd / meanR else 0f

            val isIrregular = irregIndex > AFIB_IRREG_THRESH
            if (isIrregular && !inAfib) {
                inAfib = true
                afibStartIdx = idx - 5
            } else if (!isIrregular && inAfib) {
                inAfib = false
                val durMs = ((peaks[idx] - peaks[afibStartIdx]) / fs * 1000).toLong()
                if (durMs >= AFIB_MIN_MS) {
                    afibCount++
                    events.add(
                        RhythmEvent(
                            id = "ev_afib_$afibStartIdx",
                            type = RhythmEventType.AFIB_SUSPECT,
                            startTimestampMs = startTimestampMs + ((peaks[afibStartIdx] / fs) * 1000).toLong(),
                            endTimestampMs = startTimestampMs + ((peaks[idx] / fs) * 1000).toLong(),
                            durationSeconds = durMs / 1000f,
                            details = "Sustained irregular RR pattern suggestive of Atrial Fibrillation (${durMs / 1000}s)",
                            sampleStartIndex = peaks[afibStartIdx],
                            sampleEndIndex = peaks[idx]
                        )
                    )
                }
            }
        }

        val annotations = ArrayList<BeatAnnotation>()
        var svebCount = 0
        var vebCount = 0

        for (idx in 0 until n) {
            if (labels[idx] == BeatType.SVEB) svebCount++
            if (labels[idx] == BeatType.VEB) vebCount++

            annotations.add(
                BeatAnnotation(
                    sampleIndex = peaks[idx],
                    timestampMs = startTimestampMs + ((peaks[idx] / fs) * 1000).toLong(),
                    beatType = labels[idx],
                    rrIntervalMs = rr[idx],
                    qrsWidthMs = qrsWidth[idx],
                    stElevationMv = stDev[idx],
                    rAmplitudeMv = rAmp[idx],
                    isCouplet = isCouplet[idx],
                    isTriplet = isTriplet[idx],
                    isBigeminy = isBigeminy[idx],
                    isTrigeminy = isTrigeminy[idx]
                )
            )
        }

        return ClassificationResult(
            annotations = annotations,
            events = events.sortedBy { it.startTimestampMs },
            svebCount = svebCount,
            vebCount = vebCount,
            coupletCount = couplets,
            tripletCount = triplets,
            pauseCount = pauseCount,
            afibCount = afibCount
        )
    }
}
