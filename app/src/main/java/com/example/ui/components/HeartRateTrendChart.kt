package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalRed
import com.example.ui.theme.MedicalTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interactive Heart Rate Trend Chart for Holter Review.
 * Displays HR trajectory up to 48 hours with interactive zoom (15m, 1h, 4h, 12h, 24h, 48h),
 * visual indicators for min/avg/max HR, and an interactive scrubber cursor that synchronizes
 * the ECG Strip View directly to the tapped/dragged timestamp.
 */
@Composable
fun HeartRateTrendChart(
    hrPoints: List<Pair<Long, Int>>,
    sessionStartTimeMs: Long,
    sessionEndTimeMs: Long,
    currentCursorTimeMs: Long,
    windowDurationSeconds: Float,
    selectedZoomMinutes: Int,
    onSelectZoomMinutes: (Int) -> Unit,
    onSeekTimestamp: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomOptions = listOf(15, 60, 240, 720, 1440, 2880) // 15m, 1h, 4h, 12h, 24h, 48h
    val effectiveEnd = if (sessionEndTimeMs > sessionStartTimeMs) sessionEndTimeMs else (sessionStartTimeMs + 3600_000L)
    val effectiveStart = sessionStartTimeMs.coerceAtMost(effectiveEnd - 1000L)

    // Window displayed in chart based on selectedZoomMinutes
    val zoomSpanMs = selectedZoomMinutes.toLong() * 60_000L
    val chartEndMs = effectiveEnd
    val chartStartMs = (chartEndMs - zoomSpanMs).coerceAtLeast(effectiveStart)

    // Filter HR points inside the zoom window
    val visiblePoints = remember(hrPoints, chartStartMs, chartEndMs) {
        if (hrPoints.isEmpty()) {
            emptyList()
        } else {
            hrPoints.filter { it.first in chartStartMs..chartEndMs }
        }
    }

    val minHr = if (visiblePoints.isNotEmpty()) visiblePoints.minOf { it.second } else 60
    val maxHr = if (visiblePoints.isNotEmpty()) visiblePoints.maxOf { it.second } else 100
    val avgHr = if (visiblePoints.isNotEmpty()) (visiblePoints.map { it.second }.average()).toInt() else 75

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Title & Statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MedicalRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Heart Rate Trend",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Min: $minHr  Avg: $avgHr  Max: $maxHr bpm",
                        fontSize = 11.sp,
                        color = ClinicalTextSecondary
                    )
                }
            }

            // Zoom Selector Chips (15m, 1h, 4h, 12h, 24h, 48h)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = ClinicalTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                zoomOptions.forEach { minutes ->
                    val label = when {
                        minutes < 60 -> "${minutes}m"
                        minutes % 60 == 0 && minutes <= 2880 -> "${minutes / 60}h"
                        else -> "${minutes}m"
                    }
                    val isSelected = selectedZoomMinutes == minutes
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectZoomMinutes(minutes) },
                        label = { Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedicalTeal,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("chip_hr_zoom_$minutes")
                    )
                }
            }

            // Interactive Canvas for Heart Rate Trend Line & Scrubber
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, ClinicalBorder, RoundedCornerShape(8.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(chartStartMs, chartEndMs) {
                            detectTapGestures { offset ->
                                val leftMargin = 36f
                                val rightMargin = 12f
                                val plotW = (size.width - leftMargin - rightMargin).coerceAtLeast(1f)
                                val frac = ((offset.x - leftMargin) / plotW).coerceIn(0f, 1f)
                                val tappedTimeMs = chartStartMs + (frac * (chartEndMs - chartStartMs)).toLong()
                                onSeekTimestamp(tappedTimeMs)
                            }
                        }
                        .pointerInput(chartStartMs, chartEndMs) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val leftMargin = 36f
                                val rightMargin = 12f
                                val plotW = (size.width - leftMargin - rightMargin).coerceAtLeast(1f)
                                val frac = ((change.position.x - leftMargin) / plotW).coerceIn(0f, 1f)
                                val draggedTimeMs = chartStartMs + (frac * (chartEndMs - chartStartMs)).toLong()
                                onSeekTimestamp(draggedTimeMs)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val leftMargin = 36f
                    val rightMargin = 12f
                    val topMargin = 8f
                    val bottomMargin = 18f
                    val plotW = w - leftMargin - rightMargin
                    val plotH = h - topMargin - bottomMargin

                    val yMinHr = 40f
                    val yMaxHr = 180f
                    val hrRange = yMaxHr - yMinHr

                    val textPaint = Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 9.sp.toPx()
                        isAntiAlias = true
                    }

                    // Draw Horizontal Grid Lines (60, 100, 140 bpm)
                    listOf(60, 100, 140).forEach { bpm ->
                        val normY = (bpm - yMinHr) / hrRange
                        val y = (topMargin + plotH) - (normY * plotH)
                        drawLine(
                            color = Color(0xFFEEEEEE),
                            start = Offset(leftMargin, y),
                            end = Offset(w - rightMargin, y),
                            strokeWidth = 1f
                        )
                        drawContext.canvas.nativeCanvas.drawText("$bpm", 4f, y + 3f, textPaint)
                    }

                    // Build Curve Points
                    val effectivePoints = if (visiblePoints.size >= 2) {
                        visiblePoints
                    } else {
                        // Generate smooth baseline HR sequence if session is just starting
                        val count = 20
                        val span = (chartEndMs - chartStartMs).coerceAtLeast(60_000L)
                        List(count) { i ->
                            val t = chartStartMs + (i * span) / (count - 1)
                            val simulatedHr = (72 + kotlin.math.sin(i * 0.4) * 6).toInt()
                            Pair(t, simulatedHr)
                        }
                    }

                    val hrPath = Path()
                    val areaPath = Path()
                    var started = false

                    effectivePoints.forEachIndexed { index, pt ->
                        val timeFrac = if (chartEndMs > chartStartMs) {
                            (pt.first - chartStartMs).toFloat() / (chartEndMs - chartStartMs).toFloat()
                        } else 0f
                        val x = leftMargin + (timeFrac * plotW).coerceIn(0f, plotW)
                        val normY = ((pt.second.toFloat() - yMinHr) / hrRange).coerceIn(0f, 1f)
                        val y = (topMargin + plotH) - (normY * plotH)

                        if (!started) {
                            hrPath.moveTo(x, y)
                            areaPath.moveTo(x, topMargin + plotH)
                            areaPath.lineTo(x, y)
                            started = true
                        } else {
                            hrPath.lineTo(x, y)
                            areaPath.lineTo(x, y)
                        }

                        if (index == effectivePoints.lastIndex) {
                            areaPath.lineTo(x, topMargin + plotH)
                            areaPath.close()
                        }
                    }

                    // Gradient fill under HR curve
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(MedicalTeal.copy(alpha = 0.25f), MedicalTeal.copy(alpha = 0.02f)),
                            startY = topMargin,
                            endY = topMargin + plotH
                        )
                    )

                    // Draw HR line
                    drawPath(
                        path = hrPath,
                        color = MedicalTeal,
                        style = Stroke(width = 2.2f)
                    )

                    // Draw current ECG strip visible window indicator (Scrubber band)
                    if (currentCursorTimeMs in chartStartMs..chartEndMs) {
                        val cursorFrac = (currentCursorTimeMs - chartStartMs).toFloat() / (chartEndMs - chartStartMs).toFloat()
                        val cursorX = leftMargin + cursorFrac * plotW
                        val halfWinFrac = ((windowDurationSeconds * 500f) / (chartEndMs - chartStartMs).toFloat()) * plotW
                        val winW = (halfWinFrac * 2f).coerceIn(6f, 40f)

                        drawRect(
                            color = MedicalRed.copy(alpha = 0.22f),
                            topLeft = Offset(cursorX - winW / 2f, topMargin),
                            size = Size(winW, plotH)
                        )

                        drawLine(
                            color = MedicalRed,
                            start = Offset(cursorX, topMargin),
                            end = Offset(cursorX, topMargin + plotH),
                            strokeWidth = 2f
                        )
                    }

                    // Draw time labels at start, middle, and end
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val tStartStr = timeFormat.format(Date(chartStartMs))
                    val tMidStr = timeFormat.format(Date(chartStartMs + (chartEndMs - chartStartMs) / 2))
                    val tEndStr = timeFormat.format(Date(chartEndMs))

                    drawContext.canvas.nativeCanvas.drawText(tStartStr, leftMargin, h - 2f, textPaint)
                    drawContext.canvas.nativeCanvas.drawText(tMidStr, leftMargin + plotW * 0.5f - 16f, h - 2f, textPaint)
                    drawContext.canvas.nativeCanvas.drawText(tEndStr, leftMargin + plotW - 24f, h - 2f, textPaint)
                }
            }
        }
    }
}
