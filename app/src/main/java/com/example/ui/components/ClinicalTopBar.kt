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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ClinicalSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // App header title & status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Polar ECG Clinical",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ClinicalTextPrimary
            )

            // Live Recording badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isRecording) MedicalRed.copy(alpha = 0.12f) else ClinicalBorder.copy(alpha = 0.4f))
                    .clickable { onToggleRecording() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) MedicalRed else ClinicalTextSecondary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRecording) "REC" else "STANDBY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) MedicalRed else ClinicalTextSecondary
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
