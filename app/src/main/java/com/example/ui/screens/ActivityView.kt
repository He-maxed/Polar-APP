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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PhysicalActivityData
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalCardBg
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalBlue
import com.example.ui.theme.MedicalRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityView(
    activityData: PhysicalActivityData?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val steps = activityData?.steps ?: 0
    val distance = activityData?.distanceMeters ?: 0
    val velocity = String.format(Locale.US, "%.2f", activityData?.currentVelocityKmh ?: 0f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalBg)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Physical Activity Card (Screenshot 5)
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
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Physical activity",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTextPrimary
                    )
                }

                // 3 Metrics Rows: Steps, Distance, Velocity
                ActivityMetricRow(label = "Steps:", value = "$steps")
                ActivityMetricRow(label = "Distance:", value = "$distance m")
                ActivityMetricRow(label = "Velocity:", value = "$velocity km/h")

                // Legend at top of chart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE65100)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Velocity (km/h)", fontSize = 10.sp, color = ClinicalTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF1E88E5)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Cadence (/min)", fontSize = 10.sp, color = ClinicalTextSecondary)
                    }
                }

                // Activity Chart Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFAFAFA))
                        .border(1.dp, Color(0xFFCFD8DC), RoundedCornerShape(8.dp))
                ) {
                    ActivityChartCanvas(
                        velSeries = activityData?.velocityTimeSeries ?: emptyList(),
                        cadSeries = activityData?.cadenceTimeSeries ?: emptyList()
                    )
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

        // Heart Zones Card (Screenshot 5)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MedicalRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Heart zones",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTextPrimary
                    )
                }

                Text(
                    text = "Distribution of extrasystoles by zone:",
                    fontSize = 12.sp,
                    color = ClinicalTextSecondary
                )

                // Recovery Zone Row
                ZoneDistributionBar(
                    zoneName = "Recovery",
                    blueValue = 32,
                    redValue = 68
                )

                // Rest Zone Row
                ZoneDistributionBar(
                    zoneName = "Rest",
                    blueValue = 68,
                    redValue = 32
                )
            }
        }
    }
}

@Composable
private fun ActivityMetricRow(label: String, value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE5E9EE))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClinicalTextPrimary
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ClinicalTextPrimary
            )
        }
    }
}

@Composable
private fun ZoneDistributionBar(
    zoneName: String,
    blueValue: Int,
    redValue: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = zoneName,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClinicalTextPrimary
        )

        val bWeight = blueValue.toFloat().coerceAtLeast(0.01f)
        val rWeight = redValue.toFloat().coerceAtLeast(0.01f)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            // Blue portion
            Box(
                modifier = Modifier
                    .weight(bWeight)
                    .height(28.dp)
                    .background(Color(0xFF1E88E5)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$blueValue",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Red portion
            Box(
                modifier = Modifier
                    .weight(rWeight)
                    .height(28.dp)
                    .background(Color(0xFFE53935)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$redValue",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ActivityChartCanvas(
    velSeries: List<Pair<Long, Float>>,
    cadSeries: List<Pair<Long, Float>>
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val leftMargin = 38f
        val rightMargin = 38f
        val bottomMargin = 22f
        val plotW = w - leftMargin - rightMargin
        val plotH = h - bottomMargin - 10f

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 20f
            isAntiAlias = true
        }

        // Left axis: Velocity (0 to 8 km/h)
        val vTicks = listOf(0, 2, 4, 6, 8, 10, 12, 14)
        vTicks.forEach { v ->
            val frac = v / 15f
            val y = (10f + plotH) - (frac * plotH)
            drawLine(
                color = Color(0xFFECEFF1),
                start = Offset(leftMargin, y),
                end = Offset(w - rightMargin, y),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText("$v", 10f, y + 6f, paint)
        }

        // Right axis: Cadence (0 to 120 /min)
        val cTicks = listOf(0, 40, 80, 120, 160)
        cTicks.forEach { c ->
            val frac = c / 160f
            val y = (10f + plotH) - (frac * plotH)
            drawContext.canvas.nativeCanvas.drawText("$c", w - 30f, y + 6f, paint)
        }

        val velPath = Path()
        val cadPath = Path()
        
        if (velSeries.isNotEmpty() && cadSeries.isNotEmpty()) {
            val tStart = velSeries.minOf { it.first }
            val tEnd = velSeries.maxOf { it.first }
            val tSpan = (tEnd - tStart).coerceAtLeast(1000L).toFloat()

            // Draw Orange Velocity line
            var startedV = false
            velSeries.sortedBy { it.first }.forEach { (t, v) ->
                val fracX = (t - tStart) / tSpan
                val x = leftMargin + fracX * plotW
                val fracY = (v / 15f).coerceIn(0f, 1f)
                val y = (10f + plotH) - (fracY * plotH)
                if (!startedV) {
                    velPath.moveTo(x, y)
                    startedV = true
                } else velPath.lineTo(x, y)
            }
            drawPath(velPath, Color(0xFFE65100), style = Stroke(width = 2.0f))

            // Draw Blue Cadence line
            var startedC = false
            cadSeries.sortedBy { it.first }.forEach { (t, c) ->
                val fracX = (t - tStart) / tSpan
                val x = leftMargin + fracX * plotW
                val fracY = (c / 160f).coerceIn(0f, 1f)
                val y = (10f + plotH) - (fracY * plotH)
                if (!startedC) {
                    cadPath.moveTo(x, y)
                    startedC = true
                } else cadPath.lineTo(x, y)
            }
            drawPath(cadPath, Color(0xFF1E88E5), style = Stroke(width = 2.0f))

            // X-axis timestamps
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
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
