package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HrvResult
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalCardBg
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalGreen
import com.example.ui.theme.MedicalTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HrvView(
    hrvResult: HrvResult?,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val rmssd = hrvResult?.rmssdMs?.toInt()?.takeIf { it > 0 }?.toString() ?: "--"
    val sdnn = hrvResult?.sdnnMs?.toInt()?.takeIf { it > 0 }?.toString() ?: "--"
    val meanRr = hrvResult?.meanRrMs?.toInt()?.takeIf { it > 0 }?.toString() ?: "--"
    val avgHr = hrvResult?.averageHrBpm?.toInt()?.takeIf { it > 0 }?.toString() ?: "--"
    val pnn50 = hrvResult?.pnn50Percent?.toInt()?.takeIf { it >= 0 && hrvResult.rmssdMs > 0 }?.toString() ?: "--"
    val lnRmssd = if ((hrvResult?.lnRmssd ?: 0f) > 0f) String.format(Locale.US, "%.2f", hrvResult!!.lnRmssd) else "--"
    val avgRespRate = if ((hrvResult?.averageRespiratoryRateBpm ?: 0f) > 0f) String.format(Locale.US, "%.2f", hrvResult!!.averageRespiratoryRateBpm) else "--"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalBg)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Navigation header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Analysis Overview",
                        tint = ClinicalTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "Heart Rate Variability (HRV)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ClinicalTextPrimary
            )
        }
        // HRV Card (Screenshot 7)
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
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFF42A5F5),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HRV — Heart rate variability",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTextPrimary
                    )
                }

                // 6 Rows of HRV Parameters
                HrvPillRow(text = "RMSSD: $rmssd ms")
                HrvPillRow(text = "SDNN: $sdnn ms")
                HrvPillRow(text = "Mean RR interval: $meanRr ms")
                HrvPillRow(text = "Average heart rate: $avgHr bpm")
                HrvPillRow(text = "PNN50: $pnn50 %")
                HrvPillRow(text = "ln(RMSSD): $lnRmssd ms")
            }
        }

        // Respiratory Rate Card (Screenshot 7)
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
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = MedicalTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Respiratory rate:",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTextPrimary
                    )
                }

                // Average pill
                HrvPillRow(text = "Average respiratory rate: $avgRespRate")

                // Respiration Plot (Breaths per minute over time)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(8.dp))
                ) {
                    RespirationPlotCanvas(hrvResult?.respirationTimeSeries ?: emptyList())
                }

                Text(
                    text = "Time",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun HrvPillRow(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE5E9EE))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClinicalTextPrimary
        )
    }
}

@Composable
private fun RespirationPlotCanvas(respirationTimeSeries: List<Pair<Long, Float>>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val leftMargin = 42f
        val bottomMargin = 22f
        val plotW = w - leftMargin - 10f
        val plotH = h - bottomMargin - 10f

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 20f
            isAntiAlias = true
        }

        // Y-axis ticks (5, 10, 15, 20, 25, 30, 35, 40)
        val yTicks = listOf(5, 10, 15, 20, 25, 30, 35, 40)
        val minY = 5f
        val maxY = 40f
        val rangeY = maxY - minY

        yTicks.forEach { tick ->
            val frac = (tick - minY) / rangeY
            val y = (10f + plotH) - (frac * plotH)
            drawLine(
                color = Color(0xFFECEFF1),
                start = Offset(leftMargin, y),
                end = Offset(w - 10f, y),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText("$tick", 6f, y + 6f, paint)
        }

        // Y-axis title: "Breaths per minute" rotated
        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.rotate(-90f, 18f, h / 2f)
        drawContext.canvas.nativeCanvas.drawText("Breaths per minute", -h / 2f - 70f, 24f, paint)
        drawContext.canvas.nativeCanvas.restore()

        val path = Path()
        
        if (respirationTimeSeries.size > 1) {
            val sortedSeries = respirationTimeSeries.sortedBy { it.first }
            val tStart = sortedSeries.first().first
            val tEnd = sortedSeries.last().first
            val tSpan = (tEnd - tStart).coerceAtLeast(1000L).toFloat()

            var started = false
            sortedSeries.forEach { (t, rate) ->
                val fracX = (t - tStart).toFloat() / tSpan
                val x = leftMargin + fracX * plotW
                val fracY = (rate - minY).coerceIn(0f, rangeY) / rangeY
                val y = (10f + plotH) - (fracY * plotH)

                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
        } else {
            // Draw baseline if no data
            val y = (10f + plotH) - ((14.5f - minY) / rangeY) * plotH
            path.moveTo(leftMargin, y)
            path.lineTo(leftMargin + plotW, y)
        }

        drawPath(
            path = path,
            color = Color(0xFF3B873E),
            style = Stroke(width = 2.0f)
        )

        // X-axis timestamps
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        if (respirationTimeSeries.size > 1) {
            val sortedSeries = respirationTimeSeries.sortedBy { it.first }
            val tStart = sortedSeries.first().first
            val tEnd = sortedSeries.last().first
            val numTicks = 5
            for (i in 0 until numTicks) {
                val fracX = i.toFloat() / (numTicks - 1)
                val t = tStart + (fracX * (tEnd - tStart)).toLong()
                val lbl = sdf.format(Date(t))
                val x = leftMargin + fracX * plotW - 14f
                drawContext.canvas.nativeCanvas.drawText(lbl, x, h - 4f, paint)
            }
        }
    }
}
