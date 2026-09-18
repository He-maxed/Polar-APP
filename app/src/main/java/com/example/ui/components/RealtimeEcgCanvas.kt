package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalGreen
import com.example.ui.theme.MedicalTeal
import java.util.Locale
import kotlin.math.roundToInt

enum class EcgDisplayMode(val label: String) {
    SINGLE_SWEEP("Single Sweep"),
    MULTI_ROW_60S("60s (6 Rows)")
}

enum class EcgTimeScale(val label: String, val windowSeconds: Float, val speedMmPerSec: Float) {
    SPEED_12_5("12.5 mm/s", 10.0f, 12.5f),
    SPEED_25("25 mm/s", 5.0f, 25.0f),
    SPEED_50("50 mm/s", 2.5f, 50.0f)
}

enum class EcgVoltageScale(val label: String, val gainFactor: Float) {
    GAIN_5("0.5x", 0.5f),
    GAIN_10("1.0x", 1.0f),
    GAIN_20("2.0x", 2.0f)
}

/**
 * High-Performance Clinical Canvas ECG Stream Renderer.
 * Supports:
 * - 60-Second Multi-Row Holter view (6 stacked rows of 10s each showing at least 60s of previous ECG)
 * - Single Sweep mode with adjustable sweep speeds (12.5, 25, 50 mm/s)
 * - Calibrated Millimeter Grid
 * - Freeze Frame inspection with interactive Caliper crosshairs
 */
@Composable
fun RealtimeEcgCanvas(
    buffer: FloatArray,
    sampleRateHz: Float = 130f,
    heartRateBpm: Int = 0,
    isPaused: Boolean = false,
    onTogglePause: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var displayMode by remember { mutableStateOf(EcgDisplayMode.SINGLE_SWEEP) }
    var selectedTimeScale by remember { mutableStateOf(EcgTimeScale.SPEED_25) }
    var selectedVoltageScale by remember { mutableStateOf(EcgVoltageScale.GAIN_10) }
    var caliperX by remember { mutableFloatStateOf(-1f) }
    var caliperY by remember { mutableFloatStateOf(-1f) }

    val waveformPath = remember { Path() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.5.dp, ClinicalBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Toolbar: HR Badge & Display Mode Selector (Single Sweep vs 60s 6-Row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (heartRateBpm > 0) MedicalTeal.copy(alpha = 0.15f) else Color(0xFFEEEEEE),
                    border = BorderStroke(
                        1.dp,
                        if (heartRateBpm > 0) MedicalTeal else Color(0xFFCCCCCC)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = if (heartRateBpm > 0) MedicalGreen else Color.Gray,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (heartRateBpm > 0) "$heartRateBpm BPM" else "-- BPM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ClinicalTextPrimary
                        )
                    }
                }

                Text(
                    text = "Polar H10 (130 Hz)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClinicalTextSecondary
                )
            }

            // Mode Selector: Single Sweep vs 60s 6-Row Panel
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = displayMode == EcgDisplayMode.SINGLE_SWEEP,
                    onClick = { displayMode = EcgDisplayMode.SINGLE_SWEEP },
                    label = { Text("Single Sweep", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MedicalTeal,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("chip_mode_sweep")
                )
                FilterChip(
                    selected = displayMode == EcgDisplayMode.MULTI_ROW_60S,
                    onClick = { displayMode = EcgDisplayMode.MULTI_ROW_60S },
                    label = { Text("60s (6 Rows)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.TableRows,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MedicalTeal,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("chip_mode_60s")
                )
            }
        }

        // Main Drawing Canvas
        val canvasHeight = if (displayMode == EcgDisplayMode.MULTI_ROW_60S) 320.dp else 260.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFAFAFA))
                .border(1.dp, Color(0xFF455A64), RoundedCornerShape(8.dp))
                .pointerInput(isPaused) {
                    if (isPaused) {
                        detectDragGestures { change, _ ->
                            caliperX = change.position.x
                            caliperY = change.position.y
                        }
                    }
                }
                .pointerInput(isPaused) {
                    if (isPaused) {
                        detectTapGestures { offset ->
                            caliperX = offset.x
                            caliperY = offset.y
                        }
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("realtime_ecg_canvas")
            ) {
                val w = size.width
                val h = size.height

                // Draw clinical millimeter background grid
                drawClinicalGrid(w, h)

                if (displayMode == EcgDisplayMode.MULTI_ROW_60S) {
                    // 6 Rows, 10 seconds each = 60 Seconds Buffer
                    val numRows = 6
                    val rowHeight = h / numRows.toFloat()
                    val samplesPerRow = (10f * sampleRateHz).toInt() // 1300 samples
                    val totalRequired = numRows * samplesPerRow // 7800 samples

                    val bufferLen = buffer.size
                    val offsetStart = (bufferLen - totalRequired).coerceAtLeast(0)

                    val textPaint = Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 18f
                        isAntiAlias = true
                    }

                    for (r in 0 until numRows) {
                        val rowTop = r * rowHeight
                        val rowMidY = rowTop + rowHeight / 2f
                        val timeOffsetSec = (numRows - 1 - r) * 10

                        // Baseline for this row
                        drawLine(
                            color = Color(0xFFB0BEC5),
                            start = Offset(0f, rowMidY),
                            end = Offset(w, rowMidY),
                            strokeWidth = 0.8f
                        )

                        // Row boundary divider
                        if (r > 0) {
                            drawLine(
                                color = Color(0xFFCFD8DC),
                                start = Offset(0f, rowTop),
                                end = Offset(w, rowTop),
                                strokeWidth = 1f
                            )
                        }

                        // Row tag label: e.g. -50s, -40s ... LIVE
                        val tag = if (timeOffsetSec == 0) "LIVE" else "-${timeOffsetSec}s"
                        drawContext.canvas.nativeCanvas.drawText(tag, 6f, rowMidY - 8f, textPaint)

                        // Calibration pulse on leftmost margin (1 mV step)
                        val calX = 42f
                        val calW = 14f
                        val calH = (rowHeight * 0.35f) * selectedVoltageScale.gainFactor
                        val calPath = Path().apply {
                            moveTo(calX, rowMidY)
                            lineTo(calX + 2f, rowMidY)
                            lineTo(calX + 2f, rowMidY - calH)
                            lineTo(calX + calW - 2f, rowMidY - calH)
                            lineTo(calX + calW - 2f, rowMidY)
                            lineTo(calX + calW, rowMidY)
                        }
                        drawPath(path = calPath, color = Color(0xFF37474F), style = Stroke(width = 1.5f))

                        // Draw ECG signal for this row
                        val rowSampleStart = offsetStart + r * samplesPerRow
                        val startPlotX = calX + calW + 6f
                        val plotWidth = w - startPlotX - 6f
                        val stepX = plotWidth / samplesPerRow.toFloat()
                        val scaleY = (rowHeight * 0.40f) * selectedVoltageScale.gainFactor

                        waveformPath.reset()
                        var rowStarted = false

                        for (s in 0 until samplesPerRow) {
                            val idx = rowSampleStart + s
                            if (idx in buffer.indices) {
                                val mv = buffer[idx]
                                val px = startPlotX + s * stepX
                                val py = (rowMidY - (mv * scaleY)).coerceIn(rowTop + 2f, rowTop + rowHeight - 2f)

                                if (!rowStarted) {
                                    waveformPath.moveTo(px, py)
                                    rowStarted = true
                                } else {
                                    waveformPath.lineTo(px, py)
                                }
                            }
                        }

                        if (rowStarted) {
                            drawPath(
                                path = waveformPath,
                                color = if (r == numRows - 1) MedicalTeal else Color(0xFF1E293B),
                                style = Stroke(width = 1.8f)
                            )
                        }
                    }
                } else {
                    // Single Continuous Sweep Mode
                    val midY = h / 2f
                    drawLine(
                        color = Color(0xFF90A4AE),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        strokeWidth = 1.0f
                    )

                    val calStartX = 18f
                    val calWidth = 24f
                    val calStepHeight = 36f * selectedVoltageScale.gainFactor
                    val calPath = Path().apply {
                        moveTo(calStartX, midY)
                        lineTo(calStartX + 4f, midY)
                        lineTo(calStartX + 4f, midY - calStepHeight)
                        lineTo(calStartX + calWidth - 4f, midY - calStepHeight)
                        lineTo(calStartX + calWidth - 4f, midY)
                        lineTo(calStartX + calWidth, midY)
                    }
                    drawPath(path = calPath, color = Color(0xFF263238), style = Stroke(width = 2.0f))

                    if (buffer.isNotEmpty()) {
                        waveformPath.reset()
                        val totalBufferSamples = buffer.size
                        val visibleSamples = (selectedTimeScale.windowSeconds * sampleRateHz).roundToInt().coerceIn(65, totalBufferSamples)
                        val startIndex = (totalBufferSamples - visibleSamples).coerceAtLeast(0)
                        val effectiveCount = totalBufferSamples - startIndex
                        val pixelStep = (w - (calStartX + calWidth + 10f)) / effectiveCount.toFloat()
                        val startX = calStartX + calWidth + 10f
                        val baseScaleY = (h / 3.2f) * selectedVoltageScale.gainFactor

                        var started = false
                        for (i in 0 until effectiveCount) {
                            val sampleIdx = startIndex + i
                            val rawMv = buffer[sampleIdx]
                            val x = startX + (i * pixelStep)
                            val y = midY - (rawMv * baseScaleY)

                            if (!started) {
                                waveformPath.moveTo(x, y.coerceIn(4f, h - 4f))
                                started = true
                            } else {
                                waveformPath.lineTo(x, y.coerceIn(4f, h - 4f))
                            }
                        }

                        drawPath(path = waveformPath, color = Color(0xFF0D1B2A), style = Stroke(width = 2.2f))
                    }

                    if (isPaused && caliperX >= 0f && caliperY >= 0f) {
                        drawCaliperCrosshair(caliperX, caliperY, w, h, midY, selectedVoltageScale.gainFactor)
                    }
                }
            }

            // Top-left Status Badge
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC263238))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (displayMode == EcgDisplayMode.MULTI_ROW_60S) "60s Multi-Row" else "${selectedTimeScale.speedMmPerSec.toInt()} mm/s",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(text = "•", color = Color.Gray, fontSize = 10.sp)
                Text(
                    text = "Gain ${selectedVoltageScale.label}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(text = "•", color = Color.Gray, fontSize = 10.sp)
                Text(
                    text = if (isPaused) "FROZEN" else "LIVE (130 Hz)",
                    color = if (isPaused) Color(0xFFFFD54F) else MedicalGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Bottom-right Freeze / Resume Button
            IconButton(
                onClick = onTogglePause,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isPaused) MedicalTeal else Color(0xDD37474F))
                    .testTag("btn_realtime_pause")
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = "Freeze or Resume Stream",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Bottom Controls: Time scale & Gain selectors
        if (displayMode == EcgDisplayMode.SINGLE_SWEEP) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EcgTimeScale.entries.forEach { scale ->
                        FilterChip(
                            selected = selectedTimeScale == scale,
                            onClick = { selectedTimeScale = scale },
                            label = { Text(scale.label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MedicalTeal,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EcgVoltageScale.entries.forEach { voltage ->
                        FilterChip(
                            selected = selectedVoltageScale == voltage,
                            onClick = { selectedVoltageScale = voltage },
                            label = { Text(voltage.label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF37474F),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawClinicalGrid(width: Float, height: Float) {
    val pxPerMm = 4.2f
    val minorStep = pxPerMm * 1f
    val majorStep = pxPerMm * 5f

    val minorGridColor = Color(0xFFFFEBEE)
    val majorGridColor = Color(0xFFFFCDD2)

    var x = 0f
    while (x <= width) {
        val isMajor = (x / majorStep).roundToInt() * majorStep == x
        drawLine(
            color = if (isMajor) majorGridColor else minorGridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = if (isMajor) 0.8f else 0.4f
        )
        x += minorStep
    }

    var y = 0f
    while (y <= height) {
        val isMajor = (y / majorStep).roundToInt() * majorStep == y
        drawLine(
            color = if (isMajor) majorGridColor else minorGridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = if (isMajor) 0.8f else 0.4f
        )
        y += minorStep
    }
}

private fun DrawScope.drawCaliperCrosshair(
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    midY: Float,
    gainFactor: Float
) {
    val caliperColor = Color(0xFFFFD54F)

    drawLine(
        color = caliperColor,
        start = Offset(cx, 0f),
        end = Offset(cx, height),
        strokeWidth = 1.2f
    )

    drawLine(
        color = caliperColor,
        start = Offset(0f, cy),
        end = Offset(width, cy),
        strokeWidth = 1.2f
    )

    drawCircle(color = caliperColor, radius = 5f, center = Offset(cx, cy))

    val measuredMv = (midY - cy) / ((height / 3.2f) * gainFactor)
    val paint = Paint().apply {
        color = android.graphics.Color.YELLOW
        textSize = 22f
        isFakeBoldText = true
        isAntiAlias = true
    }

    drawContext.canvas.nativeCanvas.drawText(
        String.format(Locale.US, "%.2f mV", measuredMv),
        (cx + 10f).coerceAtMost(width - 90f),
        (cy - 10f).coerceAtLeast(24f),
        paint
    )
}
