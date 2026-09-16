package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ble.BleConnectionState
import com.example.ble.PolarBleSdkWrapper
import com.example.data.AppDatabase
import com.example.data.DetectionLogEntity
import com.example.data.EcgDataPointEntity
import com.example.data.EcgRepository
import com.example.data.RecordingSessionEntity
import com.example.model.EcgSample
import com.example.ui.components.EcgTimeScale
import com.example.ui.components.EcgVoltageScale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeaturesRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: EcgRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EcgRepository(db.sessionDao(), db.ecgDataPointDao(), db.detectionLogDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testRoomSessionMetadataStorage() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val session = RecordingSessionEntity(
            sessionId = sessionId,
            title = "Clinical Holter Test 01",
            startTimestampMs = System.currentTimeMillis(),
            durationSeconds = 300,
            totalBeats = 360,
            svebCount = 4,
            vebCount = 8,
            coupletCount = 2,
            meanHr = 72,
            minHr = 58,
            maxHr = 112,
            sdnnMs = 45.8f,
            longestPauseMs = 1200f,
            sampleRateHz = 130f,
            notes = "Polar H10 lead II recording"
        )

        repository.saveSession(session)

        val retrieved = repository.getSessionById(sessionId)
        assertNotNull(retrieved)
        assertEquals("Clinical Holter Test 01", retrieved?.title)
        assertEquals(360, retrieved?.totalBeats)
        assertEquals(4, retrieved?.svebCount)
        assertEquals(8, retrieved?.vebCount)
        assertEquals(2, retrieved?.coupletCount)
        assertEquals(72, retrieved?.meanHr)
        assertEquals(45.8f, retrieved?.sdnnMs ?: 0f, 0.01f)
    }

    @Test
    fun testRoomEcgDataPointsContinuousBatchPersistence() = runBlocking {
        val sessionId = "session_test_continuous_batch"
        val sampleRate = 130
        val durationSec = 3
        val totalSamples = sampleRate * durationSec

        val sampleBatch = ArrayList<EcgSample>(totalSamples)
        val now = System.currentTimeMillis()
        for (i in 0 until totalSamples) {
            val uV = (Math.sin(2.0 * Math.PI * 1.2 * (i.toDouble() / sampleRate)) * 1000.0).toFloat()
            sampleBatch.add(EcgSample(now + (i * 7), uV, uV / 1000f))
        }

        // Insert in 1-second chunks (130 samples each) mimicking live continuous streaming
        for (sec in 0 until durationSec) {
            val chunk = sampleBatch.subList(sec * sampleRate, (sec + 1) * sampleRate)
            repository.insertDataPointsBatch(sessionId, chunk, (sec * sampleRate).toLong())
        }

        val count = repository.getDataPointsCount(sessionId)
        assertEquals(totalSamples.toLong(), count)

        val retrievedList = repository.getDataPointsForSession(sessionId)
        assertEquals(totalSamples, retrievedList.size)
        // Verify sequence ordering and integrity
        assertEquals(0L, retrievedList[0].sequenceIndex)
        assertEquals((totalSamples - 1).toLong(), retrievedList.last().sequenceIndex)
    }

    @Test
    fun testRoomDetectionLogsPersistence() = runBlocking {
        val sessionId = "session_detection_logs_test"
        val now = System.currentTimeMillis()

        val logs = listOf(
            DetectionLogEntity(
                sessionId = sessionId,
                timestampMs = now + 1000,
                eventType = "SVEB",
                hrBpm = 78,
                rrIntervalMs = 769.2f,
                rAmplitudeMv = 1.25f,
                description = "Supraventricular Extrasystole (Premature Atrial Contraction)",
                severity = "INFO"
            ),
            DetectionLogEntity(
                sessionId = sessionId,
                timestampMs = now + 3500,
                eventType = "VEB",
                hrBpm = 82,
                rrIntervalMs = 731.7f,
                rAmplitudeMv = 1.82f,
                description = "Ventricular Extrasystole (PVC)",
                severity = "WARNING"
            ),
            DetectionLogEntity(
                sessionId = sessionId,
                timestampMs = now + 4200,
                eventType = "COUPLET",
                hrBpm = 95,
                rrIntervalMs = 631.5f,
                rAmplitudeMv = 1.79f,
                description = "Ventricular Couplet detected",
                severity = "ALERT"
            )
        )

        repository.logDetectionEvents(logs)

        val retrievedLogs = repository.getDetectionLogsList(sessionId)
        assertEquals(3, retrievedLogs.size)
        assertEquals("SVEB", retrievedLogs[0].eventType)
        assertEquals("VEB", retrievedLogs[1].eventType)
        assertEquals("COUPLET", retrievedLogs[2].eventType)
        assertEquals("ALERT", retrievedLogs[2].severity)
    }

    @Test
    fun testPolarBleSdkWrapperInitializationAndState() {
        val wrapper = PolarBleSdkWrapper(context)
        assertNotNull(wrapper.api)
        val state = wrapper.connectionState.value
        assertTrue(state is BleConnectionState.Disconnected || state is BleConnectionState.Error)
        assertEquals(0, wrapper.batteryLevel.value)
        assertEquals(0, wrapper.currentHeartRate.value)
        wrapper.cleanup()
    }

    @Test
    fun testEcgCanvasTimeScaleAndVoltageSettings() {
        // Verify 25 mm/s standard speed spans 5 seconds (650 points @ 130 Hz)
        val speed25 = EcgTimeScale.SPEED_25
        assertEquals(5.0f, speed25.windowSeconds, 0.001f)
        assertEquals(650, (speed25.windowSeconds * 130f).toInt())

        // Verify 50 mm/s stretched speed spans 2.5 seconds (325 points @ 130 Hz)
        val speed50 = EcgTimeScale.SPEED_50
        assertEquals(2.5f, speed50.windowSeconds, 0.001f)
        assertEquals(325, (speed50.windowSeconds * 130f).toInt())

        // Verify 12.5 mm/s compact speed spans 10 seconds (1300 points @ 130 Hz)
        val speed12 = EcgTimeScale.SPEED_12_5
        assertEquals(10.0f, speed12.windowSeconds, 0.001f)
        assertEquals(1300, (speed12.windowSeconds * 130f).toInt())

        // Verify voltage calibration scaling factors
        assertEquals(0.5f, EcgVoltageScale.GAIN_5.gainFactor, 0.001f)
        assertEquals(1.0f, EcgVoltageScale.GAIN_10.gainFactor, 0.001f)
        assertEquals(2.0f, EcgVoltageScale.GAIN_20.gainFactor, 0.001f)
    }

    @Test
    fun testTimeRangeEcgDataPointStreamingQuery() = runBlocking {
        val sessionId = "range_query_test_session"
        val baseTime = 1000000L
        val samples = (0 until 500).map { i ->
            EcgSample(
                timestampMs = baseTime + (i * 10L),
                microvolts = (i * 10).toFloat(),
                filteredMv = (i * 0.01f)
            )
        }
        repository.insertDataPointsBatch(sessionId, samples, 0L)

        // Query window between +1000ms and +3000ms (indices 100 to 300)
        val window = repository.getDataPointsRange(sessionId, baseTime + 1000L, baseTime + 3000L)
        assertEquals(201, window.size)
        assertEquals(baseTime + 1000L, window.first().timestampMs)
        assertEquals(baseTime + 3000L, window.last().timestampMs)
    }

    @Test
    fun testSnippetBookmarkPersistence() = runBlocking {
        val sessionId = "snippet_test_session"
        val snippetLog = DetectionLogEntity(
            sessionId = sessionId,
            timestampMs = System.currentTimeMillis(),
            eventType = "SNIPPET_BOOKMARK",
            hrBpm = 76,
            rrIntervalMs = 790f,
            rAmplitudeMv = 1.25f,
            description = "Patient tagged: Palpitations (10s snippet)",
            severity = "PATIENT_FLAGGED"
        )
        repository.logDetectionEvents(listOf(snippetLog))

        val logs = repository.getDetectionLogsList(sessionId)
        assertEquals(1, logs.size)
        assertEquals("SNIPPET_BOOKMARK", logs[0].eventType)
        assertEquals("PATIENT_FLAGGED", logs[0].severity)
        assertTrue(logs[0].description.contains("Palpitations"))
    }
}
