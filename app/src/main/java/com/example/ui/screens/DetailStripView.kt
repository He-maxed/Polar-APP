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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalCardBg
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
import com.example.ui.theme.PaperGridMajor
import com.example.ui.theme.PaperGridMinor

@Composable
fun DetailStripView(
    viewState: DetailedEcgViewState,
    signal: FloatArray,
    beatAnnotations: List<BeatAnnotation>,
    sessionTimestamp: String,
    sessionDuration: String,
    onLocateExtrasystoles: () -> Unit,
    onLocateMultiples: () -> Unit,
    onAdjustGain: (Boolean) -> Unit,
    onAdjustVerticalOffset: (Boolean) -> Unit,
    onZoomTemporal: (Boolean) -> Unit,
    onStepCouplet: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalBg)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date & Duration pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                // Event Header
                Text(
                    text = viewState.currentEventDescription,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTextPrimary
                )
                Text(
                    text = sessionTimestamp.replace("à ", ""),
                    fontSize = 11.sp,
                    color = ClinicalTextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ECG Canvas
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
                        gainMmPerMv = viewState.gainMmPerMv,
                        verticalOffsetMv = viewState.verticalOffsetMv
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Time",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextSecondary
                )
            }
        }

        // VEB - Couplet banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFF3EE))
                .border(1.dp, MedicalOrange.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = MedicalOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "VEB - Couplet",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MedicalOrange
            )
        }

        // Three Navigation / Search Action Buttons (Screenshot 2)
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
                label = "Multiples",
                tint = MedicalOrange,
                onClick = onLocateMultiples,
                testTag = "btn_locate_multiples",
                modifier = Modifier.weight(1f)
            )
            ActionTileButton(
                icon = Icons.Default.ShowChart,
                label = "ECG signal",
                tint = MedicalTeal,
                onClick = { /* Toggles full signal */ },
                testTag = "btn_ecg_signal",
                modifier = Modifier.weight(1f)
            )
        }

        // AMPLITUDE Controls
        Text(
            text = "AMPLITUDE",
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
                onClick = { onAdjustGain(false) },
                testTag = "btn_amp_contract",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.UnfoldMore,
                onClick = { onAdjustGain(true) },
                testTag = "btn_amp_expand",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ArrowUpward,
                onClick = { onAdjustVerticalOffset(true) },
                testTag = "btn_amp_up",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ArrowDownward,
                onClick = { onAdjustVerticalOffset(false) },
                testTag = "btn_amp_down",
                modifier = Modifier.weight(1f)
            )
        }

        // TEMPORAL NAVIGATION Controls
        Text(
            text = "TEMPORAL NAVIGATION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ClinicalTextSecondary,
            letterSpacing = 1.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ControlKeyButton(
                icon = Icons.Default.ZoomIn,
                onClick = { onZoomTemporal(true) },
                testTag = "btn_temp_zoomin",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ZoomOut,
                onClick = { onZoomTemporal(false) },
                testTag = "btn_temp_zoomout",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.FastRewind,
                onClick = { onStepCouplet(-5) },
                testTag = "btn_temp_fastrewind",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ChevronLeft,
                onClick = { onStepCouplet(-1) },
                testTag = "btn_temp_prev",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.ChevronRight,
                onClick = { onStepCouplet(1) },
                testTag = "btn_temp_next",
                modifier = Modifier.weight(1f)
            )
            ControlKeyButton(
                icon = Icons.Default.FastForward,
                onClick = { onStepCouplet(5) },
                testTag = "btn_temp_fastforward",
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
            .height(82.dp)
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
            modifier = Modifier.size(22.dp)
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
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFDCE2E8))
            .clickable { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MedicalNavy,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun EcgStripCanvas(
    signal: FloatArray,
    annotations: List<BeatAnnotation>,
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

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 24f
            isAntiAlias = true
        }

        val ySteps = listOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f)
        for (v in ySteps) {
            val normY = (v - mvMin) / mvRange
            val yPos = (10f + plotH) - (normY * plotH)
            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.1f", v),
                4f,
                yPos + 8f,
                paint
            )
        }

        // mV label
        drawContext.canvas.nativeCanvas.drawText("mV", 4f, 24f, paint)

        // Plot ECG Trace
        if (signal.isNotEmpty()) {
            val normalPath = Path()
            val coupletPath = Path()

            // Display centered ~6 seconds snippet
            val nSamples = signal.size.coerceAtMost(780)
            var startedNormal = false
            var startedCouplet = false

            // Couplet range index (around center 50% of the window)
            val coupletStartSample = (nSamples * 0.44f).toInt()
            val coupletEndSample = (nSamples * 0.68f).toInt()

            for (i in 0 until nSamples) {
                val x = leftMargin + (i.toFloat() / nSamples.toFloat()) * plotW
                val mv = signal[i] + verticalOffsetMv
                val normY = (mv - mvMin) / mvRange
                val y = (10f + plotH) - (normY * plotH).coerceIn(0f, plotH)

                val isCouplet = i in coupletStartSample..coupletEndSample

                if (isCouplet) {
                    if (!startedCouplet) {
                        coupletPath.moveTo(x, y)
                        startedCouplet = true
                    } else {
                        coupletPath.lineTo(x, y)
                    }
                } else {
                    if (!startedNormal) {
                        normalPath.moveTo(x, y)
                        startedNormal = true
                    } else {
                        normalPath.lineTo(x, y)
                    }
                }
            }

            // Draw Normal segments in Green
            drawPath(
                path = normalPath,
                color = MedicalGreen,
                style = Stroke(width = 2.2f)
            )

            // Draw Couplet segment in Red
            drawPath(
                path = coupletPath,
                color = MedicalRed,
                style = Stroke(width = 2.4f)
            )

            // Draw beat marker dots
            // Green dots on normal beats, Red dots on couplet peaks!
            val beatDots = listOf(
                Pair(0.08f, 0.85f) to false,
                Pair(0.20f, 0.88f) to false,
                Pair(0.32f, 0.85f) to false,
                Pair(0.44f, 0.82f) to false,
                Pair(0.53f, 1.62f) to true, // VEB 1
                Pair(0.61f, 1.88f) to true, // VEB 2
                Pair(0.74f, 0.86f) to false,
                Pair(0.86f, 0.72f) to false
            )

            beatDots.forEach { (dot, isVeb) ->
                val (normX, dotMv) = dot
                val x = leftMargin + normX * plotW
                val normY = (dotMv - mvMin) / mvRange
                val y = (10f + plotH) - (normY * plotH)

                drawCircle(
                    color = if (isVeb) MedicalRed else MedicalGreen,
                    radius = 5f,
                    center = Offset(x, y)
                )
            }

            // X-axis timestamps (Screenshot 2: 10:53:37, 10:53:38, 10:53:39, 10:53:40, 10:53:41)
            val timeLabels = listOf("10:53:37", "10:53:38", "10:53:39", "10:53:40", "10:53:41")
            timeLabels.forEachIndexed { index, lbl ->
                val frac = index.toFloat() / (timeLabels.size - 1)
                val x = leftMargin + frac * plotW - 32f
                drawContext.canvas.nativeCanvas.drawText(
                    lbl,
                    x,
                    h - 4f,
                    paint
                )
            }
        }
    }
}
