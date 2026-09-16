package com.example.ui.screens

import android.graphics.Paint
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.model.BeatAnnotation
import com.example.model.BeatType
import com.example.ui.DetailedEcgViewState
import com.example.ui.components.HeartRateTrendChart
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalBlue
import com.example.ui.theme.MedicalGreen
import com.example.ui.theme.MedicalNavy
import com.example.ui.theme.MedicalOrange
import com.example.ui.theme.MedicalRed
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.PaperEcgBg
import com.example.ui.theme.PaperGridMinor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailStripView(
    viewState: DetailedEcgViewState,
    signal: FloatArray,
    beatAnnotations: List<BeatAnnotation>,
    hrHistory: List<Pair<Long, Int>>,
    sessionTimestamp: String,
    sessionDuration: String,
    onLocateExtrasystoles: () -> Unit,
    onLocateMultiples: () -> Unit,
    onAdjustGain: (Boolean) -> Unit,
    onAdjustVerticalOffset: (Boolean) -> Unit,
    onZoomTemporal: (Boolean) -> Unit,
    onStepCouplet: (Int) -> Unit,
    onSeekTimestamp: (Long) -> Unit,
    onStepTime: (Float) -> Unit,
    onSelectHrZoom: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalBg)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Date & Session Duration Badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(ClinicalBorder.copy(alpha = 0.4f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$sessionTimestamp  ·  $sessionDuration",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClinicalTextPrimary
            )
        }

        // Heart Rate Trend Chart with 1h-48h Zoom
        HeartRateTrendChart(
            hrPoints = hrHistory,
            sessionStartTimeMs = viewState.sessionStartTimeMs,
            sessionEndTimeMs = viewState.sessionEndTimeMs,
            currentCursorTimeMs = viewState.centerTimeMs,
            windowDurationSeconds = viewState.windowDurationSeconds,
            selectedZoomMinutes = viewState.hrChartZoomMinutes,
            onSelectZoomMinutes = onSelectHrZoom,
            onSeekTimestamp = onSeekTimestamp,
            modifier = Modifier.testTag("hr_trend_chart")
        )

        // Main ECG Plot Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Event Header & Window Time
                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val windowCenterStr = if (viewState.centerTimeMs > 0) timeFmt.format(Date(viewState.centerTimeMs)) else ""

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = viewState.currentEventDescription,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTextPrimary
                    )
                    Text(
                        text = if (windowCenterStr.isNotEmpty()) "Window: $windowCenterStr (±${(viewState.windowDurationSeconds / 2).toInt()}s)" else "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MedicalTeal
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ECG Canvas with real beat annotations & accurate timing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PaperEcgBg)
                        .border(1.dp, ClinicalBorder, RoundedCornerShape(8.dp))
                ) {
                    EcgStripCanvas(
                        signal = signal,
                        annotations = beatAnnotations,
                        windowStartMs = viewState.windowStartMs,
                        windowEndMs = viewState.windowEndMs,
                        gainMmPerMv = viewState.gainMmPerMv,
                        verticalOffsetMv = viewState.verticalOffsetMv
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Timeline Scrubber Slider (48h scrollable range)
                if (viewState.sessionEndTimeMs > viewState.sessionStartTimeMs) {
                    val progress = (viewState.centerTimeMs - viewState.sessionStartTimeMs).toFloat() /
                            (viewState.sessionEndTimeMs - viewState.sessionStartTimeMs).toFloat()

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = timeFmt.format(Date(viewState.sessionStartTimeMs)),
                                fontSize = 10.sp,
                                color = ClinicalTextSecondary
                            )
                            Text(
                                text = "Scrub Timeline (${String.format("%.1f%%", progress.coerceIn(0f, 1f) * 100f)})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MedicalNavy
                            )
                            Text(
                                text = timeFmt.format(Date(viewState.sessionEndTimeMs)),
                                fontSize = 10.sp,
                                color = ClinicalTextSecondary
                            )
                        }
                        Slider(
                            value = viewState.centerTimeMs.toFloat(),
                            onValueChange = { onSeekTimestamp(it.toLong()) },
                            valueRange = viewState.sessionStartTimeMs.toFloat()..viewState.sessionEndTimeMs.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MedicalTeal,
                                activeTrackColor = MedicalTeal,
                                inactiveTrackColor = ClinicalBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .testTag("slider_timeline")
                        )
                    }
                }
            }
        }

        // Stepper Navigation Controls (Fast rewind, Step, Jump to Latest)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ControlKeyButton(
                icon = Icons.Default.FirstPage,
                label = "Start",
                onClick = { onSeekTimestamp(viewState.sessionStartTimeMs) },
                testTag = "btn_nav_start",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.FastRewind,
                label = "-30s",
                onClick = { onStepTime(-30f) },
                testTag = "btn_nav_prev_30",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ChevronLeft,
                label = "-5s",
                onClick = { onStepTime(-5f) },
                testTag = "btn_nav_prev_5",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ChevronRight,
                label = "+5s",
                onClick = { onStepTime(5f) },
                testTag = "btn_nav_next_5",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.FastForward,
                label = "+30s",
                onClick = { onStepTime(30f) },
                testTag = "btn_nav_next_30",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.LastPage,
                label = "Latest",
                onClick = { onSeekTimestamp(viewState.sessionEndTimeMs) },
                testTag = "btn_nav_latest",
                modifier = Modifier.weight(1f)
            )
        }

        // Search & Locating Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionTileButton(
                icon = Icons.Default.Search,
                label = "Locate\nExtrasystoles",
                tint = MedicalBlue,
                onClick = onLocateExtrasystoles,
                testTag = "btn_locate_extrasystoles",
                modifier = Modifier.weight(1f)
            )
            ActionTileButton(
                icon = Icons.Default.FilterList,
                label = "Multiples\n(Couplets)",
                tint = MedicalOrange,
                onClick = onLocateMultiples,
                testTag = "btn_locate_multiples",
                modifier = Modifier.weight(1f)
            )
            ActionTileButton(
                icon = Icons.Default.ShowChart,
                label = "Window Zoom\n(±3s - ±10s)",
                tint = MedicalTeal,
                onClick = { onZoomTemporal(false) },
                testTag = "btn_ecg_signal",
                modifier = Modifier.weight(1f)
            )
        }

        // AMPLITUDE Controls
        Text(
            text = "AMPLITUDE & SENSITIVITY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ClinicalTextSecondary,
            letterSpacing = 1.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlKeyButton(
                icon = Icons.Default.UnfoldLess,
                label = "Gain -",
                onClick = { onAdjustGain(false) },
                testTag = "btn_amp_contract",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.UnfoldMore,
                label = "Gain +",
                onClick = { onAdjustGain(true) },
                testTag = "btn_amp_expand",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ArrowUpward,
                label = "Base Up",
                onClick = { onAdjustVerticalOffset(true) },
                testTag = "btn_amp_up",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ArrowDownward,
                label = "Base Down",
                onClick = { onAdjustVerticalOffset(false) },
                testTag = "btn_amp_down",
                modifier = Modifier.weight(1f)
            )
        }

        // TEMPORAL ZOOM Controls
        Text(
            text = "TIME SCALE ZOOM",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ClinicalTextSecondary,
            letterSpacing = 1.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlKeyButton(
                icon = Icons.Default.ZoomIn,
                label = "Zoom In (Time)",
                onClick = { onZoomTemporal(true) },
                testTag = "btn_temp_zoomin",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ZoomOut,
                label = "Zoom Out (Time)",
                onClick = { onZoomTemporal(false) },
                testTag = "btn_temp_zoomout",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionTileButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ClinicalSurface)
            .border(1.5.dp, tint.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun ControlKeyButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String = "",
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE2E7ED))
            .clickable { onClick() }
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MedicalNavy,
            modifier = Modifier.size(18.dp)
        )
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MedicalNavy
            )
        }
    }
}

/**
 * Accurate ECG Strip Canvas.
 * Renders real calibrated ECG traces, real R-peak beat markers (VEB = Red, SVEB = Amber, Normal = Green),
 * and calibrated X-axis time ticks.
 */
@Composable
private fun EcgStripCanvas(
    signal: FloatArray,
    annotations: List<BeatAnnotation>,
    windowStartMs: Long,
    windowEndMs: Long,
    gainMmPerMv: Float,
    verticalOffsetMv: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val leftMargin = 38f
        val bottomMargin = 24f
        val plotW = w - leftMargin - 10f
        val plotH = h - bottomMargin - 10f

        // Draw Holter Paper ECG Grid (5mm major, 1mm minor)
        val pxPerMm = 4.2f
        val xGridStep = pxPerMm * 5f
        val yGridStep = pxPerMm * 5f

        // Major grid lines
        var gx = leftMargin
        while (gx <= w - 10f) {
            drawLine(
                color = PaperGridMinor,
                start = Offset(gx, 10f),
                end = Offset(gx, 10f + plotH),
                strokeWidth = 0.75f
            )
            gx += xGridStep
        }

        var gy = 10f
        while (gy <= 10f + plotH) {
            drawLine(
                color = PaperGridMinor,
                start = Offset(leftMargin, gy),
                end = Offset(w - 10f, gy),
                strokeWidth = 0.75f
            )
            gy += yGridStep
        }

        // Y-axis ticks and scale (-1.0 to +2.0 mV)
        val mvMin = -1.0f
        val mvMax = 2.0f
        val mvRange = mvMax - mvMin

        val paint = Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 22f
            isAntiAlias = true
        }

        val ySteps = listOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f)
        for (v in ySteps) {
            val normY = (v - mvMin) / mvRange
            val yPos = (10f + plotH) - (normY * plotH)
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.1f", v),
                4f,
                yPos + 7f,
                paint
            )
        }

        // mV label
        drawContext.canvas.nativeCanvas.drawText("mV", 4f, 22f, paint)

        // Plot ECG Trace
        if (signal.isNotEmpty()) {
            val nSamples = signal.size
            val ecgPath = Path()
            var started = false

            for (i in 0 until nSamples) {
                val x = leftMargin + (i.toFloat() / (nSamples - 1).coerceAtLeast(1).toFloat()) * plotW
                val mv = (signal[i] * (gainMmPerMv / 10.0f)) + verticalOffsetMv
                val normY = (mv - mvMin) / mvRange
                val y = (10f + plotH) - (normY * plotH).coerceIn(0f, plotH)

                if (!started) {
                    ecgPath.moveTo(x, y)
                    started = true
                } else {
                    ecgPath.lineTo(x, y)
                }
            }

            drawPath(
                path = ecgPath,
                color = MedicalTeal,
                style = Stroke(width = 2.2f)
            )
        }

        // Draw Accurate Detected Beat Marker Dots (V, S, N)
        val spanMs = (windowEndMs - windowStartMs).coerceAtLeast(1000L)
        annotations.forEach { ann ->
            val frac = (ann.timestampMs - windowStartMs).toFloat() / spanMs.toFloat()
            if (frac in 0.02f..0.98f) {
                val dotX = leftMargin + frac * plotW
                val dotMv = (ann.rAmplitudeMv * (gainMmPerMv / 10.0f)) + verticalOffsetMv
                val normY = (dotMv - mvMin) / mvRange
                val dotY = (10f + plotH) - (normY * plotH).coerceIn(10f, 10f + plotH)

                val dotColor = when (ann.beatType) {
                    BeatType.VEB -> MedicalRed
                    BeatType.SVEB -> Color(0xFFFFA000)
                    BeatType.NORMAL -> MedicalGreen
                    else -> MedicalTeal
                }

                drawCircle(
                    color = dotColor,
                    radius = 5.5f,
                    center = Offset(dotX, dotY)
                )

                // Label text above marker (V, S, N)
                val dotTagPaint = Paint().apply {
                    color = when (ann.beatType) {
                        BeatType.VEB -> android.graphics.Color.RED
                        BeatType.SVEB -> android.graphics.Color.rgb(240, 140, 0)
                        else -> android.graphics.Color.rgb(0, 140, 60)
                    }
                    textSize = 20f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    ann.beatType.code,
                    dotX - 6f,
                    dotY - 10f,
                    dotTagPaint
                )
            }
        }

        // Calibrated X-axis Timestamps across visible window
        val numTicks = 5
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        for (i in 0 until numTicks) {
            val frac = i.toFloat() / (numTicks - 1).toFloat()
            val tickTimeMs = windowStartMs + (frac * spanMs).toLong()
            val lbl = timeFmt.format(Date(tickTimeMs))
            val x = leftMargin + frac * plotW - 28f
            drawContext.canvas.nativeCanvas.drawText(
                lbl,
                x.coerceAtLeast(leftMargin),
                h - 4f,
                paint
            )
        }
    }
}
