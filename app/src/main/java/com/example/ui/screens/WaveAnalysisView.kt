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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.WaveAnalysisItem
import com.example.model.WaveAnalysisResult
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalCardBg
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalGreen
import com.example.ui.theme.MedicalOrange
import com.example.ui.theme.MedicalTeal

@Composable
fun WaveAnalysisView(
    waveAnalysis: WaveAnalysisResult?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedItemForInfo by remember { mutableStateOf<WaveAnalysisItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalBg)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Superimposed Waveform Plot Card (Screenshot 8)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Legend at top-right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    LegendItem(color = Color(0xFF5C93D1), label = "ECG_P_Peaks")
                    Spacer(modifier = Modifier.width(8.dp))
                    LegendItem(color = Color(0xFFF3A45C), label = "ECG_Q_Peaks")
                    Spacer(modifier = Modifier.width(8.dp))
                    LegendItem(color = Color(0xFF75BF79), label = "ECG_S_Peaks")
                    Spacer(modifier = Modifier.width(8.dp))
                    LegendItem(color = Color(0xFFD66260), label = "ECG_T_Peaks")
                }

                // Wave overlay canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(8.dp))
                ) {
                    SuperimposedWaveCanvas(
                        sampleBeats = waveAnalysis?.sampleBeats ?: emptyList(),
                        averageMorphology = waveAnalysis?.averageMorphology
                    )
                }
            }
        }

        // Wave analysis Table Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = MedicalTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Wave analysis",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTextPrimary
                    )
                }

                // Table Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ECG parts", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ClinicalTextPrimary, modifier = Modifier.weight(1.3f))
                    Text("Normal\nrange", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ClinicalTextPrimary, modifier = Modifier.weight(1.2f))
                    Text("Mean", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ClinicalTextPrimary, modifier = Modifier.weight(1f))
                    Text("% in\nnormal", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ClinicalTextPrimary, modifier = Modifier.weight(0.9f))
                    Spacer(modifier = Modifier.width(36.dp))
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ClinicalBorder))

                // Table Data Rows
                val items = waveAnalysis?.items ?: emptyList()
                items.forEach { item ->
                    WaveTableRow(
                        item = item,
                        onInfoClick = { selectedItemForInfo = item }
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(0.8.dp).background(ClinicalBorder.copy(alpha = 0.5f)))
                }
            }
        }
    }

    // Clinical Information Dialog for selected wave metric
    selectedItemForInfo?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItemForInfo = null },
            title = {
                Text(
                    text = "${item.name} Clinical Criteria",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Standard Normal Range: ${item.normalRange}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(text = "Mean in Recording: ${item.meanValue}", fontSize = 13.sp)
                    Text(text = "Percentage in Normal: ${item.percentInNormal}%", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = item.description, fontSize = 12.sp, color = ClinicalTextSecondary, lineHeight = 17.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItemForInfo = null }) {
                    Text("Dismiss")
                }
            }
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(text = label, fontSize = 9.sp, color = ClinicalTextSecondary)
    }
}

@Composable
private fun WaveTableRow(
    item: WaveAnalysisItem,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = ClinicalTextPrimary,
            modifier = Modifier.weight(1.3f)
        )

        Text(
            text = item.normalRange,
            fontSize = 12.sp,
            color = ClinicalTextSecondary,
            modifier = Modifier.weight(1.2f)
        )

        // Mean value pill (Orange if abnormal/warning, Green if normal)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (item.isWarning) Color(0xFFE28169) else Color(0xFF68B27F))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.meanValue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Percentage in normal
        Text(
            text = "${item.percentInNormal}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClinicalTextPrimary,
            modifier = Modifier.weight(0.9f)
        )

        // Info Icon Button
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(32.dp).testTag("btn_info_${item.name}")
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Clinical info",
                tint = Color(0xFF4FA1D8),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SuperimposedWaveCanvas(
    sampleBeats: List<FloatArray>,
    averageMorphology: FloatArray?
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val leftMargin = 38f
        val bottomMargin = 22f
        val plotW = w - leftMargin - 10f
        val plotH = h - bottomMargin - 10f

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 22f
            isAntiAlias = true
        }

        // Y-axis ticks (-400, -200, 0, 200, 400, 600, 800, 1000 µV)
        val yTicks = listOf(-400, -200, 0, 200, 400, 600, 800)
        val minUv = -400f
        val maxUv = 900f
        val rangeUv = maxUv - minUv

        yTicks.forEach { tick ->
            val frac = (tick - minUv) / rangeUv
            val y = (10f + plotH) - (frac * plotH)
            drawLine(
                color = Color(0xFFECEFF1),
                start = Offset(leftMargin, y),
                end = Offset(w - 10f, y),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText("$tick", 4f, y + 6f, paint)
        }

        // Time domain: -0.35s to +0.45s around R-peak (0.0s)
        val timeMin = -0.35f
        val timeMax = 0.45f
        val timeRange = timeMax - timeMin

        // Center line (0.0s time - R-peak alignment)
        val midX = leftMargin + ((-timeMin) / timeRange) * plotW
        drawLine(
            color = Color(0xFFB0BEC5),
            start = Offset(midX, 10f),
            end = Offset(midX, 10f + plotH),
            strokeWidth = 1.2f
        )

        val fs = 130f
        if (sampleBeats.isNotEmpty()) {
            // Plot real superimposed beats from incoming ECG stream
            val beatsToRender = sampleBeats.takeLast(25)
            for (beat in beatsToRender) {
                if (beat.isEmpty()) continue
                val halfWin = beat.size / 2
                val path = Path()
                var started = false

                for (k in beat.indices) {
                    val timeSec = (k - halfWin) / fs
                    if (timeSec < timeMin || timeSec > timeMax) continue
                    val fracX = (timeSec - timeMin) / timeRange
                    val x = leftMargin + fracX * plotW
                    val uV = beat[k] * 1000f
                    val normY = ((uV - minUv) / rangeUv).coerceIn(0f, 1f)
                    val y = (10f + plotH) - (normY * plotH)

                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else {
                        path.lineTo(x, y)
                    }
                }

                drawPath(
                    path = path,
                    color = Color(0xFF455A64).copy(alpha = 0.22f),
                    style = Stroke(width = 1.6f)
                )
            }

            // Plot dynamic average morphology
            if (averageMorphology != null && averageMorphology.isNotEmpty()) {
                val halfWin = averageMorphology.size / 2
                val avgPath = Path()
                var started = false
                var pPeak = Offset(-1f, -1f)
                var qPeak = Offset(-1f, -1f)
                var sPeak = Offset(-1f, -1f)
                var tPeak = Offset(-1f, -1f)

                var maxP = -Float.MAX_VALUE
                var minQ = Float.MAX_VALUE
                var minS = Float.MAX_VALUE
                var maxT = -Float.MAX_VALUE

                for (k in averageMorphology.indices) {
                    val timeSec = (k - halfWin) / fs
                    if (timeSec < timeMin || timeSec > timeMax) continue
                    val fracX = (timeSec - timeMin) / timeRange
                    val x = leftMargin + fracX * plotW
                    val uV = averageMorphology[k] * 1000f
                    val normY = ((uV - minUv) / rangeUv).coerceIn(0f, 1f)
                    val y = (10f + plotH) - (normY * plotH)

                    if (!started) {
                        avgPath.moveTo(x, y)
                        started = true
                    } else {
                        avgPath.lineTo(x, y)
                    }

                    // Track fiducial points on real average morphology
                    if (timeSec in -0.25f..-0.08f && uV > maxP) {
                        maxP = uV
                        pPeak = Offset(x, y)
                    }
                    if (timeSec in -0.08f..0.0f && uV < minQ) {
                        minQ = uV
                        qPeak = Offset(x, y)
                    }
                    if (timeSec in 0.0f..0.08f && uV < minS) {
                        minS = uV
                        sPeak = Offset(x, y)
                    }
                    if (timeSec in 0.10f..0.35f && uV > maxT) {
                        maxT = uV
                        tPeak = Offset(x, y)
                    }
                }

                drawPath(
                    path = avgPath,
                    color = Color(0xFF1A237E),
                    style = Stroke(width = 2.8f)
                )

                // Draw fiducial circles if found
                if (pPeak.x >= 0) drawCircle(color = Color(0xFF5C93D1), radius = 7f, center = pPeak)
                if (qPeak.x >= 0) drawCircle(color = Color(0xFFF3A45C), radius = 7f, center = qPeak)
                if (sPeak.x >= 0) drawCircle(color = Color(0xFF75BF79), radius = 7f, center = sPeak)
                if (tPeak.x >= 0) drawCircle(color = Color(0xFFD66260), radius = 7f, center = tPeak)
            }
        } else {
            // Animated placeholder waiting for beats
            val waitText = "Awaiting continuous beats from Polar H10..."
            drawContext.canvas.nativeCanvas.drawText(
                waitText,
                leftMargin + 10f,
                10f + plotH / 2f,
                paint.apply { textSize = 26f }
            )
        }

        // X-axis time ticks (-0.2, 0.0, 0.2, 0.4s)
        val xTicks = listOf(-0.2f, 0.0f, 0.2f, 0.4f)
        xTicks.forEach { tick ->
            val fracX = (tick - timeMin) / timeRange
            val x = leftMargin + fracX * plotW
            drawContext.canvas.nativeCanvas.drawText(String.format("%.1f s", tick), x - 18f, h - 4f, paint.apply { textSize = 20f })
        }
    }
}
