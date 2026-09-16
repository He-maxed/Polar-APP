package com.example

import com.example.dsp.BeatClassifier
import com.example.dsp.ClinicalDatasetGenerator
import com.example.dsp.EcgFilter
import com.example.dsp.HrvCalculator
import com.example.dsp.QrsDetector
import com.example.model.BeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testButterworthFilterRemovesDcOffset() {
        // Create 10 seconds of 130Hz signal with 2.0 mV constant DC offset
        val fs = 130f
        val n = (10 * fs).toInt()
        val noisySignal = FloatArray(n) { 2.0f + kotlin.math.sin(it * 0.1).toFloat() }

        val filtered = EcgFilter.butterworthHighpass(noisySignal, fs, 0.5f)

        // After settling, mean DC component should be suppressed close to 0.0 mV
        val tailMean = filtered.sliceArray(n / 2 until n).average()
        assertTrue("Filter must remove DC baseline wander", kotlin.math.abs(tailMean) < 0.1)
    }

    @Test
    fun testQrsDetectorFindsRPeaks() {
        val fs = 130f
        val waveform = ClinicalDatasetGenerator.generateEcgWaveform(
            durationSeconds = 15f,
            fs = fs,
            heartRateBpm = 75f,
            includeExtrasystoles = false
        )

        val peaks = QrsDetector.detectRPeaks(waveform, fs)
        // In 15 seconds at 75 bpm, expect approx 18-19 beats
        assertTrue("R-peak count should be between 16 and 22", peaks.size in 16..22)
    }

    @Test
    fun testBeatClassifierIdentifiesEctopy() {
        val fs = 130f
        val waveform = ClinicalDatasetGenerator.generateEcgWaveform(
            durationSeconds = 40f,
            fs = fs,
            heartRateBpm = 73f,
            includeExtrasystoles = true
        )

        val peaks = QrsDetector.detectRPeaks(waveform, fs)
        val classification = BeatClassifier.classify(peaks, waveform, fs, System.currentTimeMillis())

        val vebCount = classification.annotations.count { it.beatType == BeatType.VEB }
        assertTrue("Should detect ventricular premature beats", vebCount > 0)
    }

    @Test
    fun testHrvCalculatorFormulas() {
        // RR intervals in milliseconds
        val rr = floatArrayOf(800f, 850f, 820f, 790f, 810f, 860f, 780f, 830f)
        val times = longArrayOf(1000L, 1800L, 2650L, 3470L, 4260L, 5070L, 5930L, 6710L)
        val amps = floatArrayOf(1.1f, 1.2f, 1.15f, 1.05f, 1.25f, 1.1f, 1.0f, 1.2f)

        val hrv = HrvCalculator.calculate(rr, times, amps)

        assertNotNull(hrv)
        assertTrue("SDNN must be positive", hrv.sdnnMs > 0f)
        assertTrue("RMSSD must be positive", hrv.rmssdMs > 0f)
        assertTrue("Mean RR must match average", hrv.meanRrMs in 800f..830f)
        assertTrue("PNN50 percentage must be within valid range", hrv.pnn50Percent in 0f..100f)
    }
}
