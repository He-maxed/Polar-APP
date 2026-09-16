package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.RecordingSessionEntity
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalCardBg
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedSessionsDialog(
    sessions: List<RecordingSessionEntity>,
    onSelectSession: (RecordingSessionEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MedicalTeal,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Recorded Holter Sessions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClinicalTextPrimary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ClinicalTextSecondary)
                    }
                }

                Text(
                    text = "Room database clinical logs (130 Hz Polar stream recordings):",
                    fontSize = 12.sp,
                    color = ClinicalTextSecondary
                )

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Reference Holter session loaded.\nTap REC on the top bar to record new streaming sessions.",
                            fontSize = 12.sp,
                            color = ClinicalTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions) { session ->
                            SessionRow(
                                session = session,
                                onClick = {
                                    onSelectSession(session)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = MedicalTeal, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: RecordingSessionEntity,
    onClick: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateStr = sdf.format(Date(session.startTimestampMs))

    val mins = session.durationSeconds / 60
    val secs = session.durationSeconds % 60
    val durStr = "${mins}m ${secs}s"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ClinicalCardBg)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = session.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ClinicalTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$dateStr · $durStr",
                fontSize = 11.sp,
                color = ClinicalTextSecondary
            )
            Text(
                text = "Total Beats: ${session.totalBeats} · VEB: ${session.vebCount} · SVEB: ${session.svebCount}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MedicalTeal
            )
        }

        Icon(
            imageVector = Icons.Default.PlayCircleOutline,
            contentDescription = "Load Session",
            tint = MedicalTeal,
            modifier = Modifier.size(24.dp)
        )
    }
}
