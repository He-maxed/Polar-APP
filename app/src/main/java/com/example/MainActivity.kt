package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ble.BleConnectionState
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.components.ClinicalBottomNavigation
import com.example.ui.components.ClinicalTopBar
import com.example.ui.components.SavedSessionsDialog
import com.example.ui.screens.ActivityView
import com.example.ui.screens.DetailStripView
import com.example.ui.screens.HrvView
import com.example.ui.screens.OscilloscopeView
import com.example.ui.screens.PeriodicResearchView
import com.example.ui.screens.WaveAnalysisView
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val heartRate by viewModel.currentHeartRate.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    val svebCount by viewModel.svebCount.collectAsState()
    val vebCount by viewModel.vebCount.collectAsState()
    val totalExtrasystoles by viewModel.totalExtrasystoles.collectAsState()
    val extrapolationPerDay by viewModel.extrapolationPerDay.collectAsState()
    val sessionTimestamp by viewModel.sessionTimestamp.collectAsState()
    val sessionDuration by viewModel.sessionDurationText.collectAsState()

    val detailViewState by viewModel.detailViewState.collectAsState()
    val correctedSignal by viewModel.fullSignalCorrected.collectAsState()
    val rawSignal by viewModel.fullSignalRaw.collectAsState()
    val beatAnnotations by viewModel.beatAnnotations.collectAsState()
    val windowSignal by viewModel.windowSignal.collectAsState()
    val windowAnnotations by viewModel.windowAnnotations.collectAsState()
    val hrHistory by viewModel.hrHistory.collectAsState()

    val liveBuffer by viewModel.liveOscilloscopeBuffer.collectAsState()
    val isLivePaused by viewModel.isLivePaused.collectAsState()

    val waveAnalysis by viewModel.waveAnalysis.collectAsState()
    val hrvResult by viewModel.hrvResult.collectAsState()
    val activityData by viewModel.activityData.collectAsState()
    val savedSessions by viewModel.savedSessions.collectAsState()

    var showSessionsDialog by remember { mutableStateOf(false) }

    // BLE Permission Launcher for Android 12+
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.startScan()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ClinicalBg,
        topBar = {
            ClinicalTopBar(
                connectionState = connectionState,
                batteryPercent = batteryLevel,
                heartRateBpm = heartRate,
                recordingDurationSeconds = recordingDuration,
                isRecording = isRecording,
                onToggleConnection = {
                    if (connectionState is BleConnectionState.Connected) {
                        viewModel.disconnect()
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            blePermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.BLUETOOTH_SCAN,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                )
                            )
                        } else {
                            viewModel.startScan()
                        }
                    }
                },
                onOpenRecordings = { showSessionsDialog = true },
                onNewDetection = {
                    viewModel.toggleRecording()
                },
                onToggleRecording = { viewModel.toggleRecording() },
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            ClinicalBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.PERIODIC -> {
                    PeriodicResearchView(
                        svebCount = svebCount,
                        vebCount = vebCount,
                        totalExtrasystoles = totalExtrasystoles,
                        extrapolationPerDay = extrapolationPerDay,
                        sessionTimestamp = sessionTimestamp,
                        sessionDuration = sessionDuration,
                        onNavigateTab = { viewModel.selectTab(it) },
                        onSearchFromBeginning = {
                            viewModel.analyzeFromBeginning()
                        }
                    )
                }
                AppTab.ECG_STRIP -> {
                    val activeSignal = if (windowSignal.isNotEmpty()) {
                        windowSignal
                    } else {
                        if (detailViewState.baselineCorrectionEnabled) correctedSignal else rawSignal
                    }
                    val activeAnnotations = if (windowAnnotations.isNotEmpty()) windowAnnotations else beatAnnotations
                    DetailStripView(
                        viewState = detailViewState,
                        signal = activeSignal,
                        beatAnnotations = activeAnnotations,
                        hrHistory = hrHistory,
                        sessionTimestamp = sessionTimestamp,
                        sessionDuration = sessionDuration,
                        onLocateExtrasystoles = { viewModel.locateNextExtrasystole() },
                        onLocateMultiples = { viewModel.locateMultiples() },
                        onAdjustGain = { viewModel.adjustGain(it) },
                        onAdjustVerticalOffset = { viewModel.adjustVerticalOffset(it) },
                        onZoomTemporal = { viewModel.zoomTemporal(it) },
                        onStepCouplet = { viewModel.navigateCouplet(it) },
                        onSeekTimestamp = { viewModel.seekToTimestamp(it) },
                        onStepTime = { viewModel.stepTime(it) },
                        onSelectHrZoom = { viewModel.setHrZoom(it) }
                    )
                }
                AppTab.LIVE_OSCILLOSCOPE -> {
                    OscilloscopeView(
                        liveBuffer = liveBuffer,
                        heartRateBpm = heartRate,
                        isPaused = isLivePaused,
                        isRecording = isRecording,
                        recordingDurationSeconds = recordingDuration,
                        onTogglePause = { viewModel.toggleLivePause() },
                        onBookmarkSnippet = { viewModel.bookmarkSymptomSnippet(it) }
                    )
                }
                AppTab.HRV -> {
                    HrvView(hrvResult = hrvResult)
                }
                AppTab.ACTIVITY -> {
                    ActivityView(activityData = activityData)
                }
                AppTab.WAVES -> {
                    WaveAnalysisView(waveAnalysis = waveAnalysis)
                }
            }
        }
    }

    if (showSessionsDialog) {
        SavedSessionsDialog(
            sessions = savedSessions,
            onSelectSession = { session ->
                viewModel.loadSavedSession(session)
            },
            onDismiss = { showSessionsDialog = false }
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}
