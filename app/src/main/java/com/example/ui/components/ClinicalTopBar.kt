package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.BleConnectionState
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalCardBg
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalGreen
import com.example.ui.theme.MedicalRed
import com.example.ui.theme.MedicalTeal

@Composable
fun ClinicalTopBar(
    connectionState: BleConnectionState,
    batteryPercent: Int,
    heartRateBpm: Int,
    recordingDurationSeconds: Long,
    isRecording: Boolean,
    onToggleConnection: () -> Unit,
    onOpenRecordings: () -> Unit,
    onNewDetection: () -> Unit,
    onToggleRecording: () -> Unit,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestBatteryOptimizations: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showStopConfirmationDialog by remember { mutableStateOf(false) }

    if (showStopConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmationDialog = false },
            title = {
                Text(
                    text = "Stop Holter Recording?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ClinicalTextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to stop recording? All ECG data points and arrhythmia detection logs will be saved safely to the local database.",
                    fontSize = 13.sp,
                    color = ClinicalTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmationDialog = false
                        onToggleRecording()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalRed),
                    modifier = Modifier.testTag("dialog_confirm_stop_rec")
                ) {
                    Text("Stop & Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showStopConfirmationDialog = false },
                    modifier = Modifier.testTag("dialog_cancel_stop_rec")
                ) {
                    Text("Keep Recording")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ClinicalSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // App header title & Top-Right Recording Corner Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Polar ECG Clinical",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ClinicalTextPrimary
                )
                Text(
                    text = if (isRecording) "Continuous 48h Session Active" else "Ready to Record",
                    fontSize = 11.sp,
                    color = if (isRecording) MedicalRed else ClinicalTextSecondary
                )
            }

            // Prominent Top-Right Corner REC / STOP Button
            val mins = (recordingDurationSeconds % 3600) / 60
            val secs = recordingDurationSeconds % 60
            val timerString = String.format("%02d:%02d", mins, secs)

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isRecording) MedicalRed else MedicalTeal)
                    .clickable {
                        if (isRecording) {
                            showStopConfirmationDialog = true
                        } else {
                            onToggleRecording()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("topbar_corner_rec_btn"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRecording) "STOP REC ($timerString)" else "START REC",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Three Top Action Buttons: Disconnect/Connect, Recordings, Analyze/New detection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bluetooth Action
            TopActionItem(
                icon = {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = if (connectionState is BleConnectionState.Connected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth Status",
                            tint = if (connectionState is BleConnectionState.Connected) MedicalTeal else ClinicalTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (connectionState is BleConnectionState.Connected) MedicalGreen else Color.Transparent)
                        )
                    }
                },
                label = when (connectionState) {
                    is BleConnectionState.Connected -> "Disconnect"
                    is BleConnectionState.Connecting -> "Connecting"
                    is BleConnectionState.Scanning -> "Scanning"
                    else -> "Connect"
                },
                onClick = onToggleConnection,
                testTag = "topbar_btn_bluetooth"
            )

            // Recordings
            TopActionItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Recordings",
                        tint = ClinicalTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = "Recordings",
                onClick = onOpenRecordings,
                testTag = "topbar_btn_recordings"
            )

            // New detection / Analyze
            TopActionItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Analyze",
                        tint = MedicalRed,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = "Analyze",
                onClick = onNewDetection,
                testTag = "topbar_btn_analyze"
            )

            // Battery optimization (deep-sleep survival)
            TopActionItem(
                icon = {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = Icons.Outlined.BatteryFull,
                            contentDescription = "Battery Optimization",
                            tint = if (isIgnoringBatteryOptimizations) MedicalGreen else MedicalRed,
                            modifier = Modifier.size(24.dp)
                        )
                        if (!isIgnoringBatteryOptimizations) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MedicalRed)
                            )
                        }
                    }
                },
                label = if (isIgnoringBatteryOptimizations) "Battery OK" else "Battery Lock",
                onClick = onRequestBatteryOptimizations,
                testTag = "topbar_btn_battery_optimization"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Device Telemetry Pill: Battery: 100%, 73bpm, 0h 1m 25s
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(ClinicalCardBg)
                .border(1.dp, ClinicalBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Battery
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.BatteryFull,
                    contentDescription = "Battery",
                    tint = MedicalGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Battery: $batteryPercent %",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextPrimary
                )
            }

            // BPM
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "BPM",
                    tint = MedicalRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${heartRateBpm}bpm",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTextPrimary
                )
            }

            // Timer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Timer",
                    tint = ClinicalTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                val hours = recordingDurationSeconds / 3600
                val mins = (recordingDurationSeconds % 3600) / 60
                val secs = recordingDurationSeconds % 60
                Text(
                    text = "${hours} h ${mins} m ${secs} s",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextPrimary
                )
            }
        }
    }
}

@Composable
private fun TopActionItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = ClinicalTextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
