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
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(8.dp))
                ) {
                    SuperimposedWaveCanvas(sampleBeats = waveAnalysis?.sampleBeats ?: emptyList())
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
private fun SuperimposedWaveCanvas(sampleBeats: List<FloatArray>) {
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

        // Y-axis ticks (-400, -200, 0, 200, 400, 600, 800 µV)
        val yTicks = listOf(-400, -200, 0, 200, 400, 600, 800)
        val minUv = -400f
        val maxUv = 800f
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

        // Center line (0.0s time)
        val midX = leftMargin + (0.35f * plotW)
        drawLine(
            color = Color(0xFFB0BEC5),
            start = Offset(midX, 10f),
            end = Offset(midX, 10f + plotH),
            strokeWidth = 1.2f
        )

        // Draw multiple superimposed beat complexes in translucent gray
        val numBeatsToDraw = sampleBeats.size.coerceAtLeast(15)
        for (b in 0 until numBeatsToDraw) {
            val path = Path()
            val shift = (b - 7) * 0.008f
            val baseScale = 1.0f + (b % 5) * 0.03f

            val points = listOf(
                -0.3f to 0f,
                -0.2f to 30f,
                -0.14f to 85f,  // P wave
                -0.08f to 10f,
                -0.04f to -70f, // Q wave
                0.0f to 680f * baseScale, // R peak
                0.04f to -320f, // S wave
                0.12f to 20f,
                0.24f to 140f,  // T wave
                0.36f to 0f
            )

            points.forEachIndexed { index, (timeSec, uV) ->
                val fracX = (timeSec + 0.35f) / 0.75f
                val x = leftMargin + fracX * plotW
                val normY = (uV - minUv) / rangeUv
                val y = (10f + plotH) - (normY * plotH)

                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = Color(0xFF546E7A).copy(alpha = 0.15f),
                style = Stroke(width = 1.6f)
            )
        }

        // Draw Main Average Morphology in bolder dark gray
        val avgPath = Path()
        val avgPoints = listOf(
            -0.3f to 0f,
            -0.2f to 25f,
            -0.14f to 90f,
            -0.08f to 10f,
            -0.04f to -80f,
            0.0f to 710f,
            0.04f to -340f,
            0.12f to 20f,
            0.24f to 145f,
            0.36f to 0f
        )
        avgPoints.forEachIndexed { index, (timeSec, uV) ->
            val fracX = (timeSec + 0.35f) / 0.75f
            val x = leftMargin + fracX * plotW
            val normY = (uV - minUv) / rangeUv
            val y = (10f + plotH) - (normY * plotH)
            if (index == 0) avgPath.moveTo(x, y) else avgPath.lineTo(x, y)
        }
        drawPath(
            path = avgPath,
            color = Color(0xFF263238),
            style = Stroke(width = 2.5f)
        )

        // Draw Fiducial circles (P in blue, Q in orange, S in green, T in red)
        val pX = leftMargin + ((-0.14f + 0.35f) / 0.75f) * plotW
        val pY = (10f + plotH) - ((90f - minUv) / rangeUv * plotH)
        drawCircle(color = Color(0xFF5C93D1), radius = 7f, center = Offset(pX, pY))

        val qX = leftMargin + ((-0.04f + 0.35f) / 0.75f) * plotW
        val qY = (10f + plotH) - ((-80f - minUv) / rangeUv * plotH)
        drawCircle(color = Color(0xFFF3A45C), radius = 7f, center = Offset(qX, qY))

        val sX = leftMargin + ((0.04f + 0.35f) / 0.75f) * plotW
        val sY = (10f + plotH) - ((-340f - minUv) / rangeUv * plotH)
        drawCircle(color = Color(0xFF75BF79), radius = 7f, center = Offset(sX, sY))

        val tX = leftMargin + ((0.24f + 0.35f) / 0.75f) * plotW
        val tY = (10f + plotH) - ((145f - minUv) / rangeUv * plotH)
        drawCircle(color = Color(0xFFD66260), radius = 7f, center = Offset(tX, tY))

        // X-axis time ticks (-0.2, 0.0, 0.2, 0.4s)
        val xTicks = listOf(-0.2f, 0.0f, 0.2f, 0.4f)
        xTicks.forEach { tick ->
            val fracX = (tick + 0.35f) / 0.75f
            val x = leftMargin + fracX * plotW
            drawContext.canvas.nativeCanvas.drawText(String.format("%.1f", tick), x - 14f, h - 4f, paint)
        }
    }
}
