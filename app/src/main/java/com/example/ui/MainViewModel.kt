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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    val currentCoupletIndex: Int = 0,
    val totalCouplets: Int = 0,
    val windowDurationSeconds: Float = 6.0f,
    val centerTimeMs: Long = 0L,
    val gainMmPerMv: Float = 10.0f,
    val verticalOffsetMv: Float = 0.0f,
    val baselineCorrectionEnabled: Boolean = true,
    val showBeatMarkers: Boolean = true,
    val currentEventDescription: String = "No Ectopy Detected"
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

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    // Active Holter Analysis Data
    private val _svebCount = MutableStateFlow(0)
    val svebCount: StateFlow<Int> = _svebCount.asStateFlow()

    private val _vebCount = MutableStateFlow(0)
    val vebCount: StateFlow<Int> = _vebCount.asStateFlow()

    private val _totalExtrasystoles = MutableStateFlow(0)
    val totalExtrasystoles: StateFlow<Int> = _totalExtrasystoles.asStateFlow()

    private val _extrapolationPerDay = MutableStateFlow(0)
    val extrapolationPerDay: StateFlow<Int> = _extrapolationPerDay.asStateFlow()

    private val _sessionTimestamp = MutableStateFlow("Awaiting Recording")
    val sessionTimestamp: StateFlow<String> = _sessionTimestamp.asStateFlow()

    private val _sessionDurationText = MutableStateFlow("00 h 00 min 00 sec")
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

    // Live streaming circular buffer for Oscilloscope (130 Hz, 5 seconds window = 650 points)
    private val bufferSize = 650
    private val _liveOscilloscopeBuffer = MutableStateFlow(FloatArray(bufferSize))
    val liveOscilloscopeBuffer: StateFlow<FloatArray> = _liveOscilloscopeBuffer.asStateFlow()
    private val tempLiveArray = FloatArray(bufferSize)
    private var writeHead = 0

    private val _isLivePaused = MutableStateFlow(false)
    val isLivePaused: StateFlow<Boolean> = _isLivePaused.asStateFlow()

    // Real recorded stream accumulation
    private val recordedRawSamples = ArrayList<Float>()
    private val recordedCorrectedSamples = ArrayList<Float>()
    private val recordedTimestamps = ArrayList<Long>()
    private var recordingStartTimeMs = 0L
    private var recordingTimerJob: Job? = null

    init {
        // Collect real incoming BLE samples from Polar H10
        viewModelScope.launch(Dispatchers.Default) {
            bleManager.liveSampleFlow.collect { sample ->
                if (!_isLivePaused.value) {
                    tempLiveArray[writeHead] = sample.filteredMv
                    writeHead = (writeHead + 1) % bufferSize
                    _liveOscilloscopeBuffer.value = tempLiveArray.clone()
                }

                if (_isRecording.value) {
                    recordedRawSamples.add(sample.microvolts / 1000f)
                    recordedCorrectedSamples.add(sample.filteredMv)
                    recordedTimestamps.add(sample.timestampMs)
                }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            // Stop recording & run real DSP pipeline
            _isRecording.value = false
            recordingTimerJob?.cancel()
            recordingTimerJob = null
            processAndSaveRecordedSession()
        } else {
            // Start recording
            recordedRawSamples.clear()
            recordedCorrectedSamples.clear()
            recordedTimestamps.clear()
            recordingStartTimeMs = System.currentTimeMillis()
            _recordingDurationSeconds.value = 0L
            _isRecording.value = true

            recordingTimerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000L)
                    _recordingDurationSeconds.value += 1
                }
            }
        }
    }

    private fun processAndSaveRecordedSession() {
        if (recordedCorrectedSamples.size < 130) {
            // Under 1 second of data
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val fs = 130f
            val raw = recordedRawSamples.toFloatArray()
            val corrected = recordedCorrectedSamples.toFloatArray()
            val timestamps = recordedTimestamps.toLongArray()

            fullSignalRaw.value = raw
            fullSignalCorrected.value = corrected

            // Real QRS detection & classification
            val peaks = QrsDetector.detectRPeaks(corrected, fs)
            val startTime = if (timestamps.isNotEmpty()) timestamps.first() else recordingStartTimeMs
            val classification = BeatClassifier.classify(peaks, corrected, fs, startTime)

            beatAnnotations.value = classification.annotations
            rhythmEvents.value = classification.events

            val sveb = classification.svebCount
            val veb = classification.vebCount
            val total = sveb + veb
            _svebCount.value = sveb
            _vebCount.value = veb
            _totalExtrasystoles.value = total

            val durationSec = (recordedCorrectedSamples.size / fs).toLong()
            val hours = durationSec / 3600
            val mins = (durationSec % 3600) / 60
            val secs = durationSec % 60
            _sessionDurationText.value = String.format("%02d h %02d min %02d sec", hours, mins, secs)

            val sdf = SimpleDateFormat("yyyy-MM-dd 'à' HH:mm:ss", Locale.getDefault())
            _sessionTimestamp.value = sdf.format(Date(startTime))

            // Extrapolation to 24h (86400 seconds)
            val extrap = if (durationSec > 0) ((total.toDouble() / durationSec.toDouble()) * 86400.0).toInt() else 0
            _extrapolationPerDay.value = extrap

            // Wave Analysis
            val waveRes = WaveAnalyzer.analyze(corrected, peaks, fs)
            _waveAnalysis.value = waveRes

            // HRV
            val rrArray = classification.annotations.map { it.rrIntervalMs }.toFloatArray()
            val timesArray = classification.annotations.map { it.timestampMs }.toLongArray()
            val ampArray = classification.annotations.map { it.rAmplitudeMv }.toFloatArray()
            val hrv = HrvCalculator.calculate(rrArray, timesArray, ampArray)
            _hrvResult.value = hrv

            // Activity (from motion if available or empty)
            val act = ActivityProcessor.process(FloatArray(0), timesArray, sveb, veb)
            _activityData.value = act

            // Update strip view state
            val couplets = classification.events.filter { it.type == RhythmEventType.VEB_COUPLET }
            _detailViewState.value = _detailViewState.value.copy(
                currentCoupletIndex = if (couplets.isNotEmpty()) 1 else 0,
                totalCouplets = couplets.size,
                centerTimeMs = if (couplets.isNotEmpty()) couplets.first().startTimestampMs else startTime,
                currentEventDescription = if (couplets.isNotEmpty()) "VEB - Couplet (1/${couplets.size})" else "Normal Sinus Rhythm"
            )

            // Save session to Room database
            val meanHr = if (hrv.averageHrBpm > 0) hrv.averageHrBpm.toInt() else 72
            val entity = RecordingSessionEntity(
                sessionId = UUID.randomUUID().toString(),
                title = "Polar H10 Session ${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(startTime))}",
                startTimestampMs = startTime,
                durationSeconds = durationSec,
                totalBeats = classification.annotations.size,
                svebCount = sveb,
                vebCount = veb,
                coupletCount = classification.coupletCount,
                meanHr = meanHr,
                minHr = meanHr,
                maxHr = meanHr,
                sdnnMs = hrv.sdnnMs,
                longestPauseMs = 0f,
                sampleRateHz = fs
            )
            sessionDao.insertSession(entity)
        }
    }

    fun loadSavedSession(session: RecordingSessionEntity) {
        _sessionTimestamp.value = SimpleDateFormat("yyyy-MM-dd 'à' HH:mm:ss", Locale.getDefault()).format(Date(session.startTimestampMs))
        val hours = session.durationSeconds / 3600
        val mins = (session.durationSeconds % 3600) / 60
        val secs = session.durationSeconds % 60
        _sessionDurationText.value = String.format("%02d h %02d min %02d sec", hours, mins, secs)
        _svebCount.value = session.svebCount
        _vebCount.value = session.vebCount
        val total = session.svebCount + session.vebCount
        _totalExtrasystoles.value = total
        val extrap = if (session.durationSeconds > 0) ((total.toDouble() / session.durationSeconds.toDouble()) * 86400.0).toInt() else 0
        _extrapolationPerDay.value = extrap
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

    private fun formatTime(ms: Long): String {
        val date = Date(ms)
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(date)
    }
}
