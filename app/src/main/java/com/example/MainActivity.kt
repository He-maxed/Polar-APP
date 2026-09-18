package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ble.BleConnectionState
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.components.ClinicalBottomNavigation
import com.example.ui.components.ClinicalTopBar
import com.example.ui.screens.SavedSessionsView
import com.example.ui.screens.ActivityView
import com.example.ui.screens.DetailStripView
import com.example.ui.screens.HrvView
import com.example.ui.screens.OscilloscopeView
import com.example.ui.screens.PeriodicResearchView
import com.example.ui.screens.WaveAnalysisView
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.MyApplicationTheme

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.requestDisableBatteryOptimizations() {
    try {
        startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
        )
    } catch (e: Exception) {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

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
    val analysisSourceMode by viewModel.analysisSourceMode.collectAsState()
    val rhythmEvents by viewModel.rhythmEvents.collectAsState()

    // var showSessionsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onDisplayResumed()
                    isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations()
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.onDisplayPaused()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val postNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startHolterService()
            viewModel.refreshHolterNotification()
        }
    }

    val batteryPromptPrefs = remember {
        context.getSharedPreferences("polar_holter_prefs", Context.MODE_PRIVATE)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            postNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!isIgnoringBatteryOptimizations && !batteryPromptPrefs.getBoolean("battery_opt_prompted", false)) {
            batteryPromptPrefs.edit().putBoolean("battery_opt_prompted", true).apply()
            context.requestDisableBatteryOptimizations()
        }
    }

    // BLE Permission Launcher for Android 12+
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.startHolterService()
            viewModel.startScan()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ClinicalBg,
        topBar = {
            Column {
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
                                        Manifest.permission.BLUETOOTH_CONNECT,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                )
                            } else {
                                viewModel.startScan()
                            }
                        }
                    },
                    onOpenRecordings = { viewModel.selectTab(AppTab.SAVED_RECORDINGS) },
                    onNewDetection = { viewModel.analyzeFromBeginning() },
                    onToggleRecording = { viewModel.toggleRecording() },
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                    onRequestBatteryOptimizations = { context.requestDisableBatteryOptimizations() },
                    modifier = Modifier.statusBarsPadding()
                )
                
                // NEW: 2 Tabs on top where 1 is live and 1 for previous recordings/analysis
                TabRow(
                    selectedTabIndex = if (selectedTab == AppTab.LIVE_OSCILLOSCOPE) 0 else 1,
                    containerColor = ClinicalSurface,
                    contentColor = MedicalTeal,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(
                                currentTabPosition = tabPositions[if (selectedTab == AppTab.LIVE_OSCILLOSCOPE) 0 else 1]
                            ),
                            color = MedicalTeal
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == AppTab.LIVE_OSCILLOSCOPE,
                        onClick = { viewModel.selectTab(AppTab.LIVE_OSCILLOSCOPE) },
                        text = { Text("Live Stream", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab != AppTab.LIVE_OSCILLOSCOPE,
                        onClick = { 
                            if (selectedTab == AppTab.LIVE_OSCILLOSCOPE) {
                                viewModel.selectTab(AppTab.PERIODIC)
                            }
                        },
                        text = { Text("Recordings & Analysis", fontWeight = FontWeight.Bold) }
                    )
                }
            }
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
                        rhythmEvents = rhythmEvents,
                        analysisSourceMode = analysisSourceMode,
                        hrHistory = hrHistory,
                        detailViewState = detailViewState,
                        onNavigateTab = { viewModel.selectTab(it) },
                        onSearchFromBeginning = {
                            viewModel.analyzeFromBeginning()
                        },
                        onJumpToSnippet = { timestamp ->
                            viewModel.jumpToSnippet(timestamp)
                        },
                        onSelectSourceMode = { mode ->
                            viewModel.setAnalysisSourceMode(mode)
                        },
                        onSelectHrZoom = { minutes ->
                            viewModel.setHrZoom(minutes)
                        },
                        onSeekTimestamp = { timestamp ->
                            viewModel.seekToTimestamp(timestamp)
                            viewModel.selectTab(AppTab.ECG_STRIP)
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
                        onBookmarkSnippet = { viewModel.bookmarkSymptomSnippet(it) },
                        rhythmEvents = rhythmEvents,
                        onJumpToSnippet = { timestamp ->
                            viewModel.jumpToSnippet(timestamp)
                        }
                    )
                }
                AppTab.SAVED_RECORDINGS -> {
                    SavedSessionsView(
                        sessions = savedSessions,
                        onSelectSession = { session ->
                            viewModel.loadSavedSession(session)
                            viewModel.selectTab(AppTab.PERIODIC)
                        },
                        onDeleteSession = { session ->
                            viewModel.deleteSession(session.sessionId)
                        }
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
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}
