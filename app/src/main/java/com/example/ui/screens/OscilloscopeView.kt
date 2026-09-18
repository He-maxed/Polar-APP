package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.example.model.RhythmEvent
import com.example.ui.components.RealtimeEcgCanvas
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalCardBg
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalNavy
import com.example.ui.theme.MedicalRed
import com.example.ui.theme.MedicalTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OscilloscopeView(
    liveBuffer: FloatArray,
    isPaused: Boolean,
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    onTogglePause: () -> Unit,
    onBookmarkSnippet: (String) -> Unit = {},
    heartRateBpm: Int = 0,
    rhythmEvents: List<RhythmEvent> = emptyList(),
    onJumpToSnippet: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showSnippetDialog by remember { mutableStateOf(false) }
    var selectedSymptom by remember { mutableStateOf("Palpitations") }
    var snippetSavedToast by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val symptoms = listOf(
        "Palpitations",
        "Flutter / Skipped Beat",
        "Dizziness / Presyncope",
        "Chest Discomfort",
        "Shortness of Breath",
        "Fatigue",
        "Routine Clinical Check"
    )

    if (showSnippetDialog) {
        AlertDialog(
            onDismissRequest = { showSnippetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = MedicalTeal,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bookmark 10s Snippet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = ClinicalTextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Capture the last 10 seconds of ECG and tag patient symptoms into the permanent Holter log:",
                        fontSize = 13.sp,
                        color = ClinicalTextSecondary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        symptoms.forEach { symptom ->
                            val isSel = selectedSymptom == symptom
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedSymptom = symptom },
                                label = { Text(symptom, fontSize = 12.sp) },
                                leadingIcon = if (isSel) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MedicalTeal,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chip_symptom_$symptom")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSnippetDialog = false
                        onBookmarkSnippet(selectedSymptom)
                        snippetSavedToast = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                    modifier = Modifier.testTag("btn_confirm_save_snippet")
                ) {
                    Text("Save 10s Snippet", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSnippetDialog = false },
                    modifier = Modifier.testTag("btn_cancel_snippet")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalBg)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live Header Banner with Snippet Record Control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ECG Live Stream",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTextPrimary
                )
            }

            // Snippet Record Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MedicalNavy)
                    .clickable { showSnippetDialog = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("btn_snippet_record"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkBorder,
                    contentDescription = "Bookmark 10s Snippet",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SNIPPET (10s)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Snippet Saved Confirmation Toast
        if (snippetSavedToast) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE8F5E9))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "10s ECG Snippet bookmarked to Holter session log",
                    fontSize = 11.sp,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // High-performance Canvas with 60-Second Multi-Row Holter & Sweep Toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            RealtimeEcgCanvas(
                buffer = liveBuffer,
                sampleRateHz = 130f,
                heartRateBpm = heartRateBpm,
                isPaused = isPaused,
                onTogglePause = onTogglePause,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Clinical Stream Specs Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sampling: 130 Hz",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextSecondary,
                    maxLines = 1
                )
                Text(
                    text = "Filter: 0.05-40Hz",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextSecondary,
                    maxLines = 1
                )
            }
        }

        // Live Detected Anomalies Card (Live Stream Ectopy / Distortion Logs)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = MedicalRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live Detected Anomalies & Arrhythmia Logs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTextPrimary
                    )
                }

                if (rhythmEvents.isEmpty()) {
                    Text(
                        text = "No live anomalies detected yet. Incoming real-time events (SVEB, VEB, VTach, Pauses, AFib) will appear here with clickable jump-to-snippet buttons.",
                        fontSize = 12.sp,
                        color = ClinicalTextSecondary,
                        lineHeight = 16.sp
                    )
                } else {
                    val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    rhythmEvents.takeLast(10).reversed().forEach { event ->
                        val startStr = timeFmt.format(Date(event.startTimestampMs))
                        val endStr = timeFmt.format(Date(event.endTimestampMs))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ClinicalCardBg),
                            border = BorderStroke(1.dp, ClinicalBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = event.type.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MedicalRed
                                    )
                                    Text(
                                        text = "$startStr - $endStr",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ClinicalTextSecondary
                                    )
                                }
                                Text(
                                    text = event.details,
                                    fontSize = 12.sp,
                                    color = ClinicalTextPrimary,
                                    lineHeight = 16.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "Duration: %.1fs", event.durationSeconds),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ClinicalTextSecondary
                                    )
                                    OutlinedButton(
                                        onClick = { onJumpToSnippet(event.startTimestampMs) },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MedicalTeal)
                                    ) {
                                        Text(
                                            text = "Review Snippet",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
