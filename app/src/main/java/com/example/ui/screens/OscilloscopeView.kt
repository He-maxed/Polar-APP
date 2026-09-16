package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalRed
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.OscDarkBg
import com.example.ui.theme.OscPhosphorGreen

@Composable
fun OscilloscopeView(
    liveBuffer: FloatArray,
    isPaused: Boolean,
    isRecording: Boolean,
    recordingDurationSeconds: Long,
    onTogglePause: () -> Unit,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live Header Banner matching Screenshot 6
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ECG Live Stream (130 Hz)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTextPrimary
                )
                Text(
                    text = "Polar H10 PMD Real-time Oscilloscope",
                    fontSize = 12.sp,
                    color = ClinicalTextSecondary
                )
            }

            // Record button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isRecording) MedicalRed else MedicalTeal)
                    .clickable { onToggleRecording() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("btn_live_record"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRecording) "RECORDING..." else "RECORD STREAM",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Live Oscilloscope Canvas Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF37474F), RoundedCornerShape(8.dp))
            ) {
                // Timer badge overlay (top-left, e.g. 00:44)
                val mins = (recordingDurationSeconds % 3600) / 60
                val secs = recordingDurationSeconds % 60
                val timerString = String.format("%02d:%02d", mins, secs)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Oscilloscope Grid Lines (light gray grid with standard spacing)
                    val gridXStep = 24f
                    val gridYStep = 24f

                    var gx = 0f
                    while (gx <= w) {
                        drawLine(
                            color = Color(0xFFE0E0E0),
                            start = Offset(gx, 0f),
                            end = Offset(gx, h),
                            strokeWidth = 1f
                        )
                        gx += gridXStep
                    }

                    var gy = 0f
                    while (gy <= h) {
                        drawLine(
                            color = Color(0xFFE0E0E0),
                            start = Offset(0f, gy),
                            end = Offset(w, gy),
                            strokeWidth = 1f
                        )
                        gy += gridYStep
                    }

                    // 0.0 mV Center isoelectric line
                    val midY = h / 2f
                    drawLine(
                        color = Color(0xFF9E9E9E),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1.2f
                    )

                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 28f
                        isAntiAlias = true
                    }

                    drawContext.canvas.nativeCanvas.drawText("0.0", 42f, midY - 6f, paint)

                    // Draw Live ECG Waveform
                    if (liveBuffer.isNotEmpty()) {
                        val path = Path()
                        val n = liveBuffer.size
                        val scaleY = (h / 3.5f) // 1 mV corresponds to scaleY

                        var started = false
                        for (i in 0 until n) {
                            val x = (i.toFloat() / n.toFloat()) * w
                            val mv = liveBuffer[i]
                            val y = midY - (mv * scaleY)

                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                            } else {
                                path.lineTo(x, y)
                            }
                        }

                        // Black/dark slate authentic ECG ink trace matching Screenshot 6
                        drawPath(
                            path = path,
                            color = Color(0xFF111820),
                            style = Stroke(width = 2.4f)
                        )
                    }

                    // Time axis markings at bottom (e.g. 0:35, 0:40)
                    drawContext.canvas.nativeCanvas.drawText("0:35", w * 0.25f, h - 16f, paint)
                    drawContext.canvas.nativeCanvas.drawText("0:40", w * 0.65f, h - 16f, paint)
                }

                // Top left timer badge
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF424242))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = timerString,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Pause / Play Button overlay (bottom-right matching screenshot)
                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE0E0E0))
                        .testTag("btn_oscilloscope_pause")
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause / Resume",
                        tint = Color(0xFF424242),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Live Calibration Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speed: 25 mm/s",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextSecondary
                )
                Text(
                    text = "Gain: 10 mm/mV",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextSecondary
                )
                Text(
                    text = "Filter: 0.5 - 40 Hz",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextSecondary
                )
            }
        }
    }
}
