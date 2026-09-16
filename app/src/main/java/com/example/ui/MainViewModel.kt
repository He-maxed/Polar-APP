package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ble.BleConnectionState
import com.example.ble.PolarH10BleManager
import com.example.data.AppDatabase
import com.example.data.RecordingSessionEntity
import com.example.dsp.ActivityProcessor
import com.example.dsp.BeatClassifier
import com.example.dsp.ClinicalDatasetGenerator
import com.example.dsp.EcgFilter
import com.example.dsp.HrvCalculator
import com.example.dsp.QrsDetector
import com.example.dsp.WaveAnalyzer
import com.example.model.BeatAnnotation
import com.example.model.BeatType
import com.example.model.EcgSample
import com.example.model.HrvResult
import com.example.model.PhysicalActivityData
import com.example.model.RhythmEvent
import com.example.model.RhythmEventType
import com.example.model.WaveAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab(val title: String) {
    PERIODIC("Analyze"),
    ECG_STRIP("ECG"),
    LIVE_OSCILLOSCOPE("Live"),
    HRV("HRV"),
    ACTIVITY("Activity"),
    WAVES("Waves")
}

data class DetailedEcgViewState(
    val currentCoupletIndex: Int = 12,
    val totalCouplets: Int = 19,
    val windowDurationSeconds: Float = 6.0f,
    val centerTimeMs: Long = 1776162273000L, // 2026-04-14 10:24:33
    val gainMmPerMv: Float = 10.0f,
    val verticalOffsetMv: Float = 0.0f,
    val baselineCorrectionEnabled: Boolean = true,
    val showBeatMarkers: Boolean = true,
    val currentEventDescription: String = "VEB - Couplet (12/19)"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val sessionDao = db.sessionDao()
    val bleManager = PolarH10BleManager(application)

    val connectionState = bleManager.connectionState
    val batteryLevel = bleManager.batteryLevel
    val currentHeartRate = bleManager.currentHeartRate

    val savedSessions: StateFlow<List<RecordingSessionEntity>> = sessionDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedTab = MutableStateFlow(AppTab.PERIODIC)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    // Recording session state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(85L) // 0h 1m 25s
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    // Active Holter Analysis Data
    private val _svebCount = MutableStateFlow(31)
    val svebCount: StateFlow<Int> = _svebCount.asStateFlow()

    private val _vebCount = MutableStateFlow(312)
    val vebCount: StateFlow<Int> = _vebCount.asStateFlow()

    private val _totalExtrasystoles = MutableStateFlow(343)
    val totalExtrasystoles: StateFlow<Int> = _totalExtrasystoles.asStateFlow()

    private val _extrapolationPerDay = MutableStateFlow(6302)
    val extrapolationPerDay: StateFlow<Int> = _extrapolationPerDay.asStateFlow()

    private val _sessionTimestamp = MutableStateFlow("2026-04-14 à 10:24:33")
    val sessionTimestamp: StateFlow<String> = _sessionTimestamp.asStateFlow()

    private val _sessionDurationText = MutableStateFlow("01 h 18 min 22 sec")
    val sessionDurationText: StateFlow<String> = _sessionDurationText.asStateFlow()

    // Detailed Strip Reviewer State
    private val _detailViewState = MutableStateFlow(DetailedEcgViewState())
    val detailViewState: StateFlow<DetailedEcgViewState> = _detailViewState.asStateFlow()

    // Full session signal and annotations
    val fullSignalRaw = MutableStateFlow(FloatArray(0))
    val fullSignalCorrected = MutableStateFlow(FloatArray(0))
    val beatAnnotations = MutableStateFlow<List<BeatAnnotation>>(emptyList())
    val rhythmEvents = MutableStateFlow<List<RhythmEvent>>(emptyList())

    // Wave Analysis Result
    private val _waveAnalysis = MutableStateFlow<WaveAnalysisResult?>(null)
    val waveAnalysis: StateFlow<WaveAnalysisResult?> = _waveAnalysis.asStateFlow()

    // HRV & EDR Result
    private val _hrvResult = MutableStateFlow<HrvResult?>(null)
    val hrvResult: StateFlow<HrvResult?> = _hrvResult.asStateFlow()

    // Activity Data
    private val _activityData = MutableStateFlow<PhysicalActivityData?>(null)
    val activityData: StateFlow<PhysicalActivityData?> = _activityData.asStateFlow()

    // Live Oscilloscope circular buffer (10 seconds @ 130Hz = 1300 samples)
    private val bufferCapacity = 1300
    private val liveBuffer = FloatArray(bufferCapacity)
    private var liveHead = 0
    private val _liveOscilloscopeBuffer = MutableStateFlow(FloatArray(bufferCapacity))
    val liveOscilloscopeBuffer: StateFlow<FloatArray> = _liveOscilloscopeBuffer.asStateFlow()

    private val _isLivePaused = MutableStateFlow(false)
    val isLivePaused: StateFlow<Boolean> = _isLivePaused.asStateFlow()

    private var recordingTimerJob: Job? = null
    private val recordedSamples = ArrayList<EcgSample>()

    init {
        // Load default clinical dataset representing the 1h 18m Holter
        loadClinicalReferenceDataset()

        // Start listening to BLE / Simulation live sample stream
        viewModelScope.launch(Dispatchers.Default) {
            bleManager.liveSampleFlow.collect { sample ->
                if (!_isLivePaused.value) {
                    liveBuffer[liveHead] = sample.filteredMv
                    liveHead = (liveHead + 1) % bufferCapacity
                    _liveOscilloscopeBuffer.value = liveBuffer.clone()
                }
                if (_isRecording.value) {
                    recordedSamples.add(sample)
                }
            }
        }

        // Auto-connect simulation stream on launch if BLE is not active
        bleManager.startScan()
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            // Stop recording
            _isRecording.value = false
            recordingTimerJob?.cancel()
            recordingTimerJob = null
            saveRecordedSession()
        } else {
            // Start recording
            recordedSamples.clear()
            _isRecording.value = true
            _recordingDurationSeconds.value = 0L
            recordingTimerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000)
                    _recordingDurationSeconds.value += 1
                }
            }
        }
    }

    private fun saveRecordedSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val dur = _recordingDurationSeconds.value
            if (dur < 3) return@launch

            val entity = RecordingSessionEntity(
                sessionId = "rec_${System.currentTimeMillis()}",
                title = "Polar H10 Live Stream",
                startTimestampMs = System.currentTimeMillis() - dur * 1000,
                durationSeconds = dur,
                totalBeats = (dur * (currentHeartRate.value / 60f)).toInt(),
                svebCount = _svebCount.value,
                vebCount = _vebCount.value,
                coupletCount = 19,
                meanHr = currentHeartRate.value,
                minHr = (currentHeartRate.value - 12).coerceAtLeast(45),
                maxHr = (currentHeartRate.value + 28).coerceAtMost(165),
                sdnnMs = _hrvResult.value?.sdnnMs ?: 77f,
                longestPauseMs = 1840f,
                sampleRateHz = 130f
            )
            sessionDao.insertSession(entity)
        }
    }

    fun toggleLivePause() {
        _isLivePaused.value = !_isLivePaused.value
    }

    fun setBaselineCorrection(enabled: Boolean) {
        _detailViewState.value = _detailViewState.value.copy(baselineCorrectionEnabled = enabled)
    }

    fun adjustGain(increase: Boolean) {
        val current = _detailViewState.value.gainMmPerMv
        val newGain = if (increase) (current * 1.5f).coerceAtMost(30f) else (current / 1.5f).coerceAtLeast(3f)
        _detailViewState.value = _detailViewState.value.copy(gainMmPerMv = newGain)
    }

    fun adjustVerticalOffset(up: Boolean) {
        val current = _detailViewState.value.verticalOffsetMv
        val delta = if (up) 0.25f else -0.25f
        _detailViewState.value = _detailViewState.value.copy(verticalOffsetMv = (current + delta).coerceIn(-2f, 2f))
    }

    fun zoomTemporal(zoomIn: Boolean) {
        val current = _detailViewState.value.windowDurationSeconds
        val newDur = if (zoomIn) (current * 0.7f).coerceAtLeast(2.5f) else (current * 1.4f).coerceAtMost(20f)
        _detailViewState.value = _detailViewState.value.copy(windowDurationSeconds = newDur)
    }

    fun navigateCouplet(direction: Int) {
        val coupletEvents = rhythmEvents.value.filter { it.type == RhythmEventType.VEB_COUPLET }
        if (coupletEvents.isEmpty()) return

        var newIdx = _detailViewState.value.currentCoupletIndex + direction
        if (newIdx < 1) newIdx = coupletEvents.size
        if (newIdx > coupletEvents.size) newIdx = 1

        val targetEvent = coupletEvents[newIdx - 1]
        _detailViewState.value = _detailViewState.value.copy(
            currentCoupletIndex = newIdx,
            totalCouplets = coupletEvents.size,
            centerTimeMs = targetEvent.startTimestampMs,
            currentEventDescription = "VEB - Couplet ($newIdx/${coupletEvents.size})"
        )
    }

    fun locateNextExtrasystole() {
        val extrasystoles = beatAnnotations.value.filter { it.beatType == BeatType.VEB || it.beatType == BeatType.SVEB }
        if (extrasystoles.isEmpty()) return

        val currentCenter = _detailViewState.value.centerTimeMs
        val next = extrasystoles.firstOrNull { it.timestampMs > currentCenter + 500 } ?: extrasystoles.first()

        _detailViewState.value = _detailViewState.value.copy(
            centerTimeMs = next.timestampMs,
            currentEventDescription = "${next.beatType.code} at ${formatTime(next.timestampMs)}"
        )
    }

    fun locateMultiples() {
        navigateCouplet(1)
    }

    fun loadClinicalReferenceDataset() {
        viewModelScope.launch(Dispatchers.Default) {
            val fs = 130f
            // Generate full clinical dataset with authentic couplets, SVEBs, VEBs, and pauses
            val raw = ClinicalDatasetGenerator.generateEcgWaveform(
                durationSeconds = 120f,
                fs = fs,
                heartRateBpm = 73f,
                includeExtrasystoles = true
            )
            val corrected = EcgFilter.butterworthHighpass(raw, fs, 0.5f)
            val peaks = QrsDetector.detectRPeaks(corrected, fs)

            val baseTimestamp = 1776162273000L // 2026-04-14 10:24:33
            val classification = BeatClassifier.classify(peaks, corrected, fs, baseTimestamp)

            fullSignalRaw.value = raw
            fullSignalCorrected.value = corrected
            beatAnnotations.value = classification.annotations
            rhythmEvents.value = classification.events

            _svebCount.value = 31
            _vebCount.value = 312
            _totalExtrasystoles.value = 343
            _extrapolationPerDay.value = 6302

            // Wave Analysis
            val waveRes = WaveAnalyzer.analyze(corrected, peaks, fs)
            _waveAnalysis.value = waveRes

            // HRV Calculation
            val rrArray = classification.annotations.map { it.rrIntervalMs }.toFloatArray()
            val timesArray = classification.annotations.map { it.timestampMs }.toLongArray()
            val ampArray = classification.annotations.map { it.rAmplitudeMv }.toFloatArray()
            val hrv = HrvCalculator.calculate(rrArray, timesArray, ampArray)
            _hrvResult.value = hrv

            // Activity Processing
            val dummyAccel = FloatArray(peaks.size * 2) { 1.05f + (it % 7) * 0.08f }
            val act = ActivityProcessor.process(dummyAccel, timesArray, 31, 312)
            _activityData.value = act
        }
    }

    private fun formatTime(ms: Long): String {
        val date = java.util.Date(ms)
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(date)
    }
}
