package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecordingSessionEntity
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalCardBg
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalRed
import com.example.ui.theme.MedicalTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedSessionsView(
    sessions: List<RecordingSessionEntity>,
    onSelectSession: (RecordingSessionEntity) -> Unit,
    onDeleteSession: (RecordingSessionEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var sessionToDelete by remember { mutableStateOf<RecordingSessionEntity?>(null) }

    if (sessionToDelete != null) {
        val target = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = "Delete Recording?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ClinicalTextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete session '${target.title}'? All stored ECG data points and arrhythmia logs for this session will be permanently deleted.",
                    fontSize = 13.sp,
                    color = ClinicalTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(target)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MedicalRed),
                    modifier = Modifier.testTag("btn_confirm_delete_session")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { sessionToDelete = null },
                    modifier = Modifier.testTag("btn_cancel_delete_session")
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MedicalTeal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Recorded Holter Sessions",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = ClinicalTextPrimary
            )
        }

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saved recordings yet. Record a session and tap 'Save' to review it later.",
                    fontSize = 14.sp,
                    color = ClinicalTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(sessions) { session ->
                    SessionRowItem(
                        session = session,
                        onClick = { onSelectSession(session) },
                        onDelete = { sessionToDelete = session }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRowItem(
    session: RecordingSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", Locale.getDefault())
    val dateStr = sdf.format(Date(session.startTimestampMs))

    val hrs = session.durationSeconds / 3600
    val mins = (session.durationSeconds % 3600) / 60
    val secs = session.durationSeconds % 60
    val durStr = if (hrs > 0) "${hrs}h ${mins}m ${secs}s" else "${mins}m ${secs}s"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$dateStr · Duration: $durStr",
                    fontSize = 13.sp,
                    color = ClinicalTextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Total Beats: ${session.totalBeats} · VEB: ${session.vebCount} · SVEB: ${session.svebCount}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MedicalTeal
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("btn_delete_session_${session.sessionId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Session",
                        tint = MedicalRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = "Load Session",
                    tint = MedicalTeal,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
