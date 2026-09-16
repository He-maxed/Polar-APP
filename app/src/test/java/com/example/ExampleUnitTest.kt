package com.example

import com.example.dsp.BeatClassifier
import com.example.dsp.EcgFilter
import com.example.dsp.HrvCalculator
import com.example.dsp.QrsDetector
import com.example.model.BeatType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testButterworthFilterRemovesDcOffset() {
        val fs = 130f
        val n = (10 * fs).toInt()
        val noisySignal = FloatArray(n) { 2.0f + kotlin.math.sin(it * 0.1).toFloat() }

        val filtered = EcgFilter.butterworthHighpass(noisySignal, fs, 0.5f)

        val tailMean = filtered.sliceArray(n / 2 until n).average()
        assertTrue("Filter must remove DC baseline wander", kotlin.math.abs(tailMean) < 0.1)
    }

    @Test
    fun testQrsDetectorFindsSharpImpulses() {
        val fs = 130f
        val n = (10 * fs).toInt()
        val signal = FloatArray(n) { 0f }
        // Insert periodic sharp R peaks every 104 samples (approx 75 bpm)
        val period = 104
        for (i in 100 until n - 50 step period) {
            signal[i] = 1.8f
            signal[i - 1] = 0.5f
            signal[i + 1] = 0.5f
        }

        val peaks = QrsDetector.detectRPeaks(signal, fs)
        assertTrue("R-peak count should be detected for sharp impulses", peaks.isNotEmpty())
    }

    @Test
    fun testHrvCalculatorFormulas() {
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
