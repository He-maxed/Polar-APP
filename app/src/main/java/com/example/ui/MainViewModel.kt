package com.example.ui

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ble.BleConnectionState
import com.example.ble.HolterTelemetry
import com.example.ble.PolarH10BleService
import com.example.ble.PolarBleSdkWrapper
import com.example.ble.PolarH10BleManager
import com.example.data.AppDatabase
import com.example.data.DetectionLogEntity
import com.example.data.EcgRepository
import com.example.data.RecordingSessionEntity
import com.example.dsp.ActivityProcessor
import com.example.dsp.BeatClassifier
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private const val DISPLAY_FRAME_INTERVAL_MS = 66L

private data class LiveDisplayFrame(val buffer: FloatArray)

enum class AnalysisSourceMode(val label: String) {
    CURRENT_SESSION("Current Recording"),
    SAVED_RECORDING("Saved Recording")
}

enum class AppTab(val title: String) {
    LIVE_OSCILLOSCOPE("Live View"),
    SAVED_RECORDINGS("Recordings"),
    PERIODIC("Analysis"),
    ECG_STRIP("Strip Review"),
    HRV("HRV & EDR"),
    ACTIVITY("Activity"),
    WAVES("Waves")
}

data class DetailedEcgViewState(
    val currentCoupletIndex: Int = 0,
    val totalCouplets: Int = 0,
    val windowDurationSeconds: Float = 6.0f,
    val centerTimeMs: Long = 0L,
    val sessionStartTimeMs: Long = 0L,
    val sessionEndTimeMs: Long = 0L,
    val gainMmPerMv: Float = 10.0f,
    val verticalOffsetMv: Float = 0.0f,
    val baselineCorrectionEnabled: Boolean = true,
    val showBeatMarkers: Boolean = true,
    val currentEventDescription: String = "No Ectopy Detected",
    val hrChartZoomMinutes: Int = 60
) {
    val windowStartMs: Long
        get() {
            val halfWin = (windowDurationSeconds * 500f).toLong()
            val start = centerTimeMs - halfWin
            return if (sessionStartTimeMs > 0) start.coerceAtLeast(sessionStartTimeMs) else start
        }
    val windowEndMs: Long
        get() {
            val halfWin = (windowDurationSeconds * 500f).toLong()
            val end = centerTimeMs + halfWin
            return if (sessionEndTimeMs > 0) end.coerceAtMost(sessionEndTimeMs) else end
        }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val ecgRepository = EcgRepository(db.sessionDao(), db.ecgDataPointDao(), db.detectionLogDao())

    // Official Polar BLE SDK Wrapper
    val polarSdkWrapper = PolarBleSdkWrapper(application)
    // Direct BLE fallback driver
    val bleManager = PolarH10BleManager(application)

    // Unified Connection State & Telemetry
    val connectionState: StateFlow<BleConnectionState> = combine(
        polarSdkWrapper.connectionState,
        bleManager.connectionState
    ) { sdkState, nativeState ->
        if (sdkState !is BleConnectionState.Disconnected) sdkState else nativeState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BleConnectionState.Disconnected)

    val batteryLevel: StateFlow<Int> = combine(
        polarSdkWrapper.batteryLevel,
        bleManager.batteryLevel
    ) { b1, b2 ->
        if (b1 > 0) b1 else b2
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val currentHeartRate: StateFlow<Int> = combine(
        polarSdkWrapper.currentHeartRate,
        bleManager.currentHeartRate
    ) { h1, h2 ->
        if (h1 > 0) h1 else h2
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedSessions: StateFlow<List<RecordingSessionEntity>> = ecgRepository.allSessions
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

    // Windowed signal and annotations for lightweight ECG strip rendering
    val windowSignal = MutableStateFlow(FloatArray(0))
    val windowAnnotations = MutableStateFlow<List<BeatAnnotation>>(emptyList())
    val windowTimestamps = MutableStateFlow<LongArray>(LongArray(0))

    // Full session signal and annotations
    val fullSignalRaw = MutableStateFlow(FloatArray(0))
    val fullSignalCorrected = MutableStateFlow(FloatArray(0))
    val beatAnnotations = MutableStateFlow<List<BeatAnnotation>>(emptyList())
    val rhythmEvents = MutableStateFlow<List<RhythmEvent>>(emptyList())

    // Heart Rate history (timestampMs to BPM) for trend chart
    private val _hrHistory = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val hrHistory: StateFlow<List<Pair<Long, Int>>> = _hrHistory.asStateFlow()

    // Wave Analysis Result
    private val _waveAnalysis = MutableStateFlow<WaveAnalysisResult?>(null)
    val waveAnalysis: StateFlow<WaveAnalysisResult?> = _waveAnalysis.asStateFlow()

    // HRV & EDR Result
    private val _hrvResult = MutableStateFlow<HrvResult?>(null)
    val hrvResult: StateFlow<HrvResult?> = _hrvResult.asStateFlow()

    // Activity Data
    private val _activityData = MutableStateFlow<PhysicalActivityData?>(null)
    val activityData: StateFlow<PhysicalActivityData?> = _activityData.asStateFlow()

    // Analysis Source Mode (Current Session vs Saved Recording)
    val analysisSourceMode = MutableStateFlow(AnalysisSourceMode.CURRENT_SESSION)

    private val bufferSize = 7800
    private val _liveOscilloscopeBuffer = MutableStateFlow(FloatArray(bufferSize))
    val liveOscilloscopeBuffer: StateFlow<FloatArray> = _liveOscilloscopeBuffer.asStateFlow()

    private val displayHistory = ArrayDeque<Float>(bufferSize)
    private val displayHistoryLock = Any()
    private var lastDisplayFrameTimeMs = 0L
    @Volatile
    private var isDisplayFlowSuspended = false
    private val _liveDisplayFrames = MutableSharedFlow<LiveDisplayFrame>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val liveDisplayFrames: SharedFlow<LiveDisplayFrame> = _liveDisplayFrames.asSharedFlow()

    private val _isLivePaused = MutableStateFlow(false)
    val isLivePaused: StateFlow<Boolean> = _isLivePaused.asStateFlow()

    // Real recorded stream accumulation & continuous Room flush with thread safety
    private val sampleLock = Any()
    private val recordedRawSamples = ArrayList<Float>()
    private val recordedCorrectedSamples = ArrayList<Float>()
    private val recordedTimestamps = ArrayList<Long>()
    private val continuousDbBuffer = ArrayList<EcgSample>()
    private val hrAccumulator = ArrayList<Pair<Long, Int>>()
    private var activeSessionId = UUID.randomUUID().toString()
    private var currentSequenceIndex = 0L
    private var recordingStartTimeMs = 0L
    private var recordingTimerJob: Job? = null
    private var lastHrRecordTimeMs = 0L

    init {
        // Storage pipeline: HR history, recording accumulation, continuous Room flush (decoupled from UI)
        viewModelScope.launch(Dispatchers.Default) {
            merge(polarSdkWrapper.liveSampleFlow, bleManager.liveSampleFlow).collect { sample ->
                val hr = currentHeartRate.value
                val now = sample.timestampMs

                synchronized(sampleLock) {
                    if (now - lastHrRecordTimeMs >= 2000L) {
                        lastHrRecordTimeMs = now
                        if (hr > 0) {
                            hrAccumulator.add(Pair(now, hr))
                            if (hrAccumulator.size > 86400) {
                                hrAccumulator.removeAt(0)
                            }
                            if (analysisSourceMode.value == AnalysisSourceMode.CURRENT_SESSION) {
                                _hrHistory.value = ArrayList(hrAccumulator)
                            }
                        }
                    }

                    if (_isRecording.value) {
                        recordedRawSamples.add(sample.microvolts / 1000f)
                        recordedCorrectedSamples.add(sample.filteredMv)
                        recordedTimestamps.add(sample.timestampMs)
                        continuousDbBuffer.add(sample)

                        if (analysisSourceMode.value == AnalysisSourceMode.CURRENT_SESSION) {
                            _detailViewState.value = _detailViewState.value.copy(
                                sessionEndTimeMs = now
                            )
                        }

                        if (continuousDbBuffer.size >= 130) {
                            flushBufferToRoom()
                        }
                    }
                }
            }
        }

        // Display pipeline: DROP_OLDEST ring of recent samples throttled into replay frames
        viewModelScope.launch(Dispatchers.Default) {
            merge(polarSdkWrapper.liveSampleFlow, bleManager.liveSampleFlow).collect { sample ->
                synchronized(displayHistoryLock) {
                    if (displayHistory.size >= bufferSize) {
                        displayHistory.removeFirst()
                    }
                    displayHistory.addLast(sample.filteredMv)
                }
                val now = System.currentTimeMillis()
                if (!isDisplayFlowSuspended && !_isLivePaused.value && now - lastDisplayFrameTimeMs >= DISPLAY_FRAME_INTERVAL_MS) {
                    lastDisplayFrameTimeMs = now
                    emitLatestDisplayFrame()
                }
            }
        }

        // Main-thread bridge: pushes the latest replay frame into the oscilloscope buffer
        viewModelScope.launch(Dispatchers.Main.immediate) {
            liveDisplayFrames.collect { frame ->
                _liveOscilloscopeBuffer.value = frame.buffer
            }
        }

        // Telemetry relay: pushes live status into the always-on Holter foreground service
        viewModelScope.launch(Dispatchers.Default) {
            combine(
                connectionState,
                isRecording,
                currentHeartRate,
                recordingDurationSeconds
            ) { state, recording, hr, elapsed ->
                HolterTelemetry(
                    isStreaming = state is BleConnectionState.Connected,
                    isRecording = recording,
                    heartRateBpm = hr,
                    elapsedSeconds = elapsed
                )
            }.distinctUntilChanged().collect { t ->
                PolarH10BleService.updateTelemetry(t)
            }
        }

        startPeriodicAnalysisLoop()
        startHolterService()
    }

    private fun startPeriodicAnalysisLoop() {
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(3500L)
                if (analysisSourceMode.value != AnalysisSourceMode.CURRENT_SESSION) {
                    continue
                }
                try {
                    val (signal, times) = synchronized(sampleLock) {
                        if (recordedCorrectedSamples.size >= 260) {
                            val count = recordedCorrectedSamples.size.coerceAtMost(5200)
                            val start = recordedCorrectedSamples.size - count
                            Pair(
                                recordedCorrectedSamples.subList(start, recordedCorrectedSamples.size).toFloatArray(),
                                recordedTimestamps.subList(start, recordedTimestamps.size).toLongArray()
                            )
                        } else {
                            synchronized(displayHistoryLock) {
                                if (displayHistory.size >= 260) {
                                    val count = displayHistory.size.coerceAtMost(1300)
                                    val base = System.currentTimeMillis()
                                    val list = displayHistory.toList()
                                    Pair(
                                        list.takeLast(count).toFloatArray(),
                                        LongArray(count) { base - (count - 1 - it) * 8L }
                                    )
                                } else {
                                    Pair(FloatArray(0), LongArray(0))
                                }
                            }
                        }
                    }

                    if (signal.size >= 260) {
                        val fs = 130f
                        val peaks = QrsDetector.detectRPeaks(signal, fs)
                        if (peaks.size >= 2) {
                            val startTime = if (times.isNotEmpty()) times.first() else System.currentTimeMillis()
                            val classification = BeatClassifier.classify(peaks, signal, fs, startTime)
                            val waveRes = WaveAnalyzer.analyze(signal, peaks, fs)

                            _svebCount.value = classification.svebCount
                            _vebCount.value = classification.vebCount
                            val totalEctopies = classification.svebCount + classification.vebCount
                            _totalExtrasystoles.value = totalEctopies

                            val durSec = signal.size / fs
                            val extrap = if (durSec > 0) ((totalEctopies.toDouble() / durSec) * 86400.0).toInt() else 0
                            _extrapolationPerDay.value = extrap

                            _waveAnalysis.value = waveRes

                            val rrArray = classification.annotations.map { it.rrIntervalMs }.toFloatArray()
                            val timesArray = classification.annotations.map { it.timestampMs }.toLongArray()
                            val ampArray = classification.annotations.map { it.rAmplitudeMv }.toFloatArray()
                            _hrvResult.value = HrvCalculator.calculate(rrArray, timesArray, ampArray)

                            beatAnnotations.value = classification.annotations
                            rhythmEvents.value = classification.events
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Periodic analysis error: ${e.message}")
                }
            }
        }
    }

    fun startScan() {
        polarSdkWrapper.startDiscovery()
        bleManager.startScan()
    }

    fun stopScan() {
        polarSdkWrapper.stopDiscovery()
        bleManager.stopScan()
    }

    fun disconnect() {
        polarSdkWrapper.disconnect()
        bleManager.disconnect()
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
        if (tab == AppTab.ECG_STRIP) {
            val (latestTime, startTime) = synchronized(sampleLock) {
                val latest = if (recordedTimestamps.isNotEmpty()) recordedTimestamps.last() else System.currentTimeMillis()
                val start = if (recordedTimestamps.isNotEmpty()) recordedTimestamps.first() else latest
                Pair(latest, start)
            }
            // Default to opening of app (startTime) to prevent auto live-streaming until "Latest" is pressed
            _detailViewState.value = _detailViewState.value.copy(
                centerTimeMs = startTime,
                sessionStartTimeMs = startTime,
                sessionEndTimeMs = latestTime
            )
            updateVisibleWindow(startTime, _detailViewState.value.windowDurationSeconds)
        }
    }

    fun jumpToSnippet(timestampMs: Long) {
        _detailViewState.value = _detailViewState.value.copy(
            centerTimeMs = timestampMs
        )
        updateVisibleWindow(timestampMs, _detailViewState.value.windowDurationSeconds)
        _selectedTab.value = AppTab.ECG_STRIP
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            // Stop recording & run complete DSP analysis pipeline
            _isRecording.value = false
            recordingTimerJob?.cancel()
            recordingTimerJob = null
            // Final buffer flush to Room
            flushBufferToRoom()
            processAndSaveRecordedSession()
        } else {
            // Start recording
            synchronized(sampleLock) {
                recordedRawSamples.clear()
                recordedCorrectedSamples.clear()
                recordedTimestamps.clear()
                continuousDbBuffer.clear()
                activeSessionId = UUID.randomUUID().toString()
                currentSequenceIndex = 0L
                recordingStartTimeMs = System.currentTimeMillis()
            }
            _recordingDurationSeconds.value = 0L
            _isRecording.value = true
            _detailViewState.value = _detailViewState.value.copy(
                sessionStartTimeMs = recordingStartTimeMs,
                sessionEndTimeMs = recordingStartTimeMs,
                centerTimeMs = recordingStartTimeMs
            )

            recordingTimerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000L)
                    _recordingDurationSeconds.value += 1
                }
            }
        }
    }

    private fun flushBufferToRoom() {
        val chunk = synchronized(sampleLock) {
            if (continuousDbBuffer.isEmpty()) return
            val list = ArrayList(continuousDbBuffer)
            continuousDbBuffer.clear()
            list
        }
        val seqStart = currentSequenceIndex
        currentSequenceIndex += chunk.size
        val sid = activeSessionId

        viewModelScope.launch(Dispatchers.IO) {
            try {
                ecgRepository.insertDataPointsBatch(sid, chunk, seqStart)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error persisting batch to Room: ${e.localizedMessage}")
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

            // Real QRS detection & arrhythmia classification
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
            _sessionDurationText.value = String.format("%02d:%02d:%02d", hours, mins, secs)

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

            // Activity data
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

            // Save detection events to Room detection_logs table
            val detectionEntities = classification.annotations.filter { it.beatType != BeatType.NORMAL }.map { ann ->
                DetectionLogEntity(
                    sessionId = activeSessionId,
                    timestampMs = ann.timestampMs,
                    eventType = ann.beatType.name,
                    hrBpm = if (ann.rrIntervalMs > 0) (60000f / ann.rrIntervalMs).roundToInt() else 0,
                    rrIntervalMs = ann.rrIntervalMs,
                    rAmplitudeMv = ann.rAmplitudeMv,
                    description = "${ann.beatType.code} Extrasystole at ${formatTime(ann.timestampMs)}",
                    severity = if (ann.beatType == BeatType.VEB) "WARNING" else "INFO"
                )
            }
            ecgRepository.logDetectionEvents(detectionEntities)

            // Save complete session metadata to Room database
            val validHrs = classification.annotations.mapNotNull { if (it.rrIntervalMs > 0) (60000f / it.rrIntervalMs).roundToInt() else null }
            val meanHr = if (hrv.averageHrBpm > 0) hrv.averageHrBpm.toInt() else (if (validHrs.isNotEmpty()) validHrs.average().toInt() else 0)
            val minHr = if (validHrs.isNotEmpty()) validHrs.minOrNull()!! else meanHr
            val maxHr = if (validHrs.isNotEmpty()) validHrs.maxOrNull()!! else meanHr
            val longestPauseMs = classification.events.filter { it.type == RhythmEventType.PAUSE }
                .maxOfOrNull { it.durationSeconds * 1000f }
                ?: classification.annotations.maxOfOrNull { it.rrIntervalMs }
                ?: 0f

            val entity = RecordingSessionEntity(
                sessionId = activeSessionId,
                title = "Polar H10 Session ${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(startTime))}",
                startTimestampMs = startTime,
                durationSeconds = durationSec,
                totalBeats = classification.annotations.size,
                svebCount = sveb,
                vebCount = veb,
                coupletCount = classification.coupletCount,
                meanHr = meanHr,
                minHr = minHr,
                maxHr = maxHr,
                sdnnMs = hrv.sdnnMs,
                longestPauseMs = longestPauseMs,
                sampleRateHz = fs
            )
            ecgRepository.saveSession(entity)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ecgRepository.deleteSession(sessionId)
        }
    }

    fun setAnalysisSourceMode(mode: AnalysisSourceMode) {
        analysisSourceMode.value = mode
        if (mode == AnalysisSourceMode.CURRENT_SESSION) {
            analyzeFromBeginning()
        }
    }

    fun loadSavedSession(session: RecordingSessionEntity) {
        analysisSourceMode.value = AnalysisSourceMode.SAVED_RECORDING
        _sessionTimestamp.value = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(session.startTimestampMs))
        val hours = session.durationSeconds / 3600
        val mins = (session.durationSeconds % 3600) / 60
        val secs = session.durationSeconds % 60
        _sessionDurationText.value = String.format("%02d:%02d:%02d", hours, mins, secs)

        viewModelScope.launch(Dispatchers.Default) {
            val points = ecgRepository.getDataPointsForSession(session.sessionId)
            if (points.isNotEmpty()) {
                val corrected = points.map { it.filteredMv }.toFloatArray()
                val raw = points.map { it.rawMicrovolts / 1000f }.toFloatArray()
                fullSignalCorrected.value = corrected
                fullSignalRaw.value = raw

                val fs = session.sampleRateHz
                val peaks = QrsDetector.detectRPeaks(corrected, fs)
                val classification = BeatClassifier.classify(peaks, corrected, fs, session.startTimestampMs)
                beatAnnotations.value = classification.annotations
                rhythmEvents.value = classification.events

                val sveb = classification.svebCount
                val veb = classification.vebCount
                val total = sveb + veb
                _svebCount.value = sveb
                _vebCount.value = veb
                _totalExtrasystoles.value = total

                val extrap = if (session.durationSeconds > 0) ((total.toDouble() / session.durationSeconds.toDouble()) * 86400.0).toInt() else 0
                _extrapolationPerDay.value = extrap

                val waveRes = WaveAnalyzer.analyze(corrected, peaks, fs)
                _waveAnalysis.value = waveRes

                val rrArray = classification.annotations.map { it.rrIntervalMs }.toFloatArray()
                val timesArray = classification.annotations.map { it.timestampMs }.toLongArray()
                val ampArray = classification.annotations.map { it.rAmplitudeMv }.toFloatArray()
                val hrv = HrvCalculator.calculate(rrArray, timesArray, ampArray)
                _hrvResult.value = hrv

                val act = ActivityProcessor.process(FloatArray(0), timesArray, sveb, veb)
                _activityData.value = act

                // Build HR History for Loaded Saved Session from detected beat RR intervals
                val savedHrPoints = classification.annotations.mapNotNull { ann ->
                    if (ann.rrIntervalMs > 0) {
                        val bpm = (60000f / ann.rrIntervalMs).roundToInt().coerceIn(30, 220)
                        Pair(ann.timestampMs, bpm)
                    } else null
                }
                _hrHistory.value = savedHrPoints

                val endTime = session.startTimestampMs + (session.durationSeconds * 1000L).coerceAtLeast(1000L)
                _detailViewState.value = _detailViewState.value.copy(
                    sessionStartTimeMs = session.startTimestampMs,
                    sessionEndTimeMs = endTime,
                    centerTimeMs = session.startTimestampMs,
                    currentEventDescription = "Recording: ${session.title}"
                )
                updateVisibleWindow(session.startTimestampMs, _detailViewState.value.windowDurationSeconds)
            }
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
        updateVisibleWindow(_detailViewState.value.centerTimeMs, newDur)
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

    fun updateVisibleWindow(centerMs: Long, durationSec: Float) {
        val halfWinMs = (durationSec * 500f).toLong()
        val startMs = centerMs - halfWinMs
        val endMs = centerMs + halfWinMs

        val (rawSlice, correctedSlice, timeSlice) = if (analysisSourceMode.value == AnalysisSourceMode.SAVED_RECORDING) {
            val rawFull = fullSignalRaw.value
            val corrFull = fullSignalCorrected.value
            val totalSamples = corrFull.size
            if (totalSamples > 0 && _detailViewState.value.sessionEndTimeMs > _detailViewState.value.sessionStartTimeMs) {
                val totalSpanMs = (_detailViewState.value.sessionEndTimeMs - _detailViewState.value.sessionStartTimeMs).coerceAtLeast(1L)
                val startFrac = (startMs - _detailViewState.value.sessionStartTimeMs).toFloat() / totalSpanMs.toFloat()
                val endFrac = (endMs - _detailViewState.value.sessionStartTimeMs).toFloat() / totalSpanMs.toFloat()

                val startIdx = (startFrac * totalSamples).toInt().coerceIn(0, totalSamples)
                val endIdx = (endFrac * totalSamples).toInt().coerceIn(startIdx, totalSamples)

                val raw = if (rawFull.size == totalSamples) rawFull.copyOfRange(startIdx, endIdx) else FloatArray(0)
                val corr = corrFull.copyOfRange(startIdx, endIdx)
                val tms = LongArray(endIdx - startIdx) { idx ->
                    startMs + (idx.toFloat() / (endIdx - startIdx).coerceAtLeast(1) * (endMs - startMs)).toLong()
                }
                Triple(raw, corr, tms)
            } else {
                Triple(FloatArray(0), FloatArray(0), LongArray(0))
            }
        } else {
            synchronized(sampleLock) {
                if (recordedTimestamps.isNotEmpty()) {
                    val times = recordedTimestamps
                    var startIdx = times.binarySearch(startMs)
                    if (startIdx < 0) startIdx = (-startIdx - 1).coerceIn(0, times.size)
                    var endIdx = times.binarySearch(endMs)
                    if (endIdx < 0) endIdx = (-endIdx - 1).coerceIn(0, times.size)
                    if (endIdx <= startIdx && times.isNotEmpty()) {
                        startIdx = (times.size - (durationSec * 130).toInt()).coerceAtLeast(0)
                        endIdx = times.size
                    }
                    val raw = recordedRawSamples.subList(startIdx, endIdx).toFloatArray()
                    val corr = recordedCorrectedSamples.subList(startIdx, endIdx).toFloatArray()
                    val tms = recordedTimestamps.subList(startIdx, endIdx).toLongArray()
                    Triple(raw, corr, tms)
                } else {
                    val corr = synchronized(displayHistoryLock) {
                        displayHistory.toList().toFloatArray()
                    }
                    val raw = corr.clone()
                    val tms = LongArray(corr.size) { System.currentTimeMillis() - (corr.size - 1 - it) * 8L }
                    Triple(raw, corr, tms)
                }
            }
        }

        windowSignal.value = if (_detailViewState.value.baselineCorrectionEnabled) correctedSlice else rawSlice
        windowTimestamps.value = timeSlice

        val allAnns = beatAnnotations.value
        windowAnnotations.value = allAnns.filter { it.timestampMs in startMs..endMs }
    }

    fun seekToTimestamp(timeMs: Long) {
        _detailViewState.value = _detailViewState.value.copy(centerTimeMs = timeMs)
        updateVisibleWindow(timeMs, _detailViewState.value.windowDurationSeconds)
    }

    fun stepTime(deltaSeconds: Float) {
        val newCenter = _detailViewState.value.centerTimeMs + (deltaSeconds * 1000f).toLong()
        val clamped = if (_detailViewState.value.sessionEndTimeMs > 0 && _detailViewState.value.sessionStartTimeMs > 0) {
            newCenter.coerceIn(_detailViewState.value.sessionStartTimeMs, _detailViewState.value.sessionEndTimeMs)
        } else newCenter
        seekToTimestamp(clamped)
    }

    fun setHrZoom(minutes: Int) {
        _detailViewState.value = _detailViewState.value.copy(hrChartZoomMinutes = minutes)
    }

    fun bookmarkSymptomSnippet(tag: String = "Symptom Bookmark") {
        viewModelScope.launch(Dispatchers.IO) {
            val (snippetSamples, _) = synchronized(sampleLock) {
                val takeCount = 1300.coerceAtMost(recordedCorrectedSamples.size)
                if (takeCount > 0) {
                    val start = recordedCorrectedSamples.size - takeCount
                    Pair(
                        recordedCorrectedSamples.subList(start, recordedCorrectedSamples.size).toFloatArray(),
                        recordedTimestamps.subList(start, recordedTimestamps.size).toLongArray()
                    )
                } else {
                    synchronized(displayHistoryLock) {
                        val list = displayHistory.toList()
                        Pair(
                            list.takeLast(1300).toFloatArray(),
                            LongArray(1300) { System.currentTimeMillis() - (1300 - 1 - it) * 8L }
                        )
                    }
                }
            }

            val now = System.currentTimeMillis()
            val hr = currentHeartRate.value
            val log = DetectionLogEntity(
                sessionId = activeSessionId,
                timestampMs = now,
                eventType = "SYMPTOM_SNIPPET",
                hrBpm = hr,
                rrIntervalMs = if (hr > 0) (60000f / hr) else 0f,
                rAmplitudeMv = if (snippetSamples.isNotEmpty()) snippetSamples.maxOrNull() ?: 0f else 0f,
                description = "Patient Holter Snippet: $tag at ${formatTime(now)}",
                severity = "WARNING"
            )
            ecgRepository.logDetectionEvent(log)
        }
    }

    fun analyzeFromBeginning() {
        analysisSourceMode.value = AnalysisSourceMode.CURRENT_SESSION
        synchronized(sampleLock) {
            _hrHistory.value = ArrayList(hrAccumulator)
        }
        viewModelScope.launch(Dispatchers.Default) {
            val (raw, corrected, times) = synchronized(sampleLock) {
                if (recordedCorrectedSamples.size >= 130) {
                    Triple(
                        recordedRawSamples.toFloatArray(),
                        recordedCorrectedSamples.toFloatArray(),
                        recordedTimestamps.toLongArray()
                    )
                } else {
                    val arr = synchronized(displayHistoryLock) {
                        displayHistory.toList().toFloatArray()
                    }
                    Triple(
                        arr,
                        arr.clone(),
                        LongArray(arr.size) { System.currentTimeMillis() - (arr.size - 1 - it) * 8L }
                    )
                }
            }

            if (corrected.size >= 130) {
                val fs = 130f
                val peaks = QrsDetector.detectRPeaks(corrected, fs)
                val startTime = if (times.isNotEmpty()) times.first() else System.currentTimeMillis()
                val classification = BeatClassifier.classify(peaks, corrected, fs, startTime)

                beatAnnotations.value = classification.annotations
                rhythmEvents.value = classification.events
                _svebCount.value = classification.svebCount
                _vebCount.value = classification.vebCount
                val totalEctopies = classification.svebCount + classification.vebCount
                _totalExtrasystoles.value = totalEctopies

                val durationSec = (corrected.size / fs).toLong()
                val hours = durationSec / 3600
                val mins = (durationSec % 3600) / 60
                val secs = durationSec % 60
                _sessionDurationText.value = String.format("%02d:%02d:%02d", hours, mins, secs)

                val sdf = SimpleDateFormat("yyyy-MM-dd 'à' HH:mm:ss", Locale.getDefault())
                _sessionTimestamp.value = sdf.format(Date(startTime))

                val extrap = if (durationSec > 0) ((totalEctopies.toDouble() / durationSec.toDouble()) * 86400.0).toInt() else 0
                _extrapolationPerDay.value = extrap

                _waveAnalysis.value = WaveAnalyzer.analyze(corrected, peaks, fs)

                val rrArray = classification.annotations.map { it.rrIntervalMs }.toFloatArray()
                val timesArray = classification.annotations.map { it.timestampMs }.toLongArray()
                val ampArray = classification.annotations.map { it.rAmplitudeMv }.toFloatArray()
                _hrvResult.value = HrvCalculator.calculate(rrArray, timesArray, ampArray)

                val endTime = if (times.isNotEmpty()) times.last() else startTime
                _detailViewState.value = _detailViewState.value.copy(
                    centerTimeMs = startTime,
                    sessionStartTimeMs = startTime,
                    sessionEndTimeMs = endTime,
                    currentEventDescription = "Holter Analysis from Beginning ($totalEctopies total ectopies)"
                )
                updateVisibleWindow(startTime, _detailViewState.value.windowDurationSeconds)
                _selectedTab.value = AppTab.ECG_STRIP
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val date = Date(ms)
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(date)
    }

    fun onDisplayResumed() {
        isDisplayFlowSuspended = false
        emitLatestDisplayFrame()
    }

    fun onDisplayPaused() {
        isDisplayFlowSuspended = true
    }

    fun refreshHolterNotification() {
        PolarH10BleService.updateTelemetry(PolarH10BleService.telemetry.value)
    }

    private fun buildLatestDisplayFrame(): FloatArray {
        val snapshot: List<Float> = synchronized(displayHistoryLock) {
            displayHistory.toList()
        }
        val buffer = FloatArray(bufferSize)
        val n = snapshot.size.coerceAtMost(bufferSize)
        val offset = bufferSize - n
        for (i in 0 until n) {
            buffer[offset + i] = snapshot[snapshot.size - n + i]
        }
        return buffer
    }

    private fun emitLatestDisplayFrame() {
        _liveDisplayFrames.tryEmit(LiveDisplayFrame(buildLatestDisplayFrame()))
    }

    fun startHolterService() {
        try {
            val context = getApplication<Application>()
            val canStart = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!canStart) return

            val intent = Intent(context, PolarH10BleService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Failed to start Holter service: ${e.message}")
        }
    }

    override fun onCleared() {
        PolarH10BleService.updateTelemetry(HolterTelemetry())
        polarSdkWrapper.cleanup()
        bleManager.cleanup()
        super.onCleared()
    }
}
