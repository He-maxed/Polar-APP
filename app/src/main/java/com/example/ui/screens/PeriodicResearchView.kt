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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppTab
import com.example.ui.theme.ClinicalBg
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalCardBg
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalBlue
import com.example.ui.theme.MedicalNavy
import com.example.ui.theme.MedicalRed
import com.example.ui.theme.MedicalTeal
import com.example.ui.theme.SvebBlueBg
import com.example.ui.theme.SvebBlueText
import com.example.ui.theme.TotalGrayBg
import com.example.ui.theme.TotalGrayText
import com.example.ui.theme.VebPinkBg
import com.example.ui.theme.VebPinkText

@Composable
fun PeriodicResearchView(
    svebCount: Int,
    vebCount: Int,
    totalExtrasystoles: Int,
    extrapolationPerDay: Int,
    sessionTimestamp: String,
    sessionDuration: String,
    onNavigateTab: (AppTab) -> Unit,
    onSearchFromBeginning: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showDiagnosisCard by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClinicalBg)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main Periodic Research Card
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
                // Header chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MedicalTeal.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = MedicalTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Periodic Research",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedicalTeal
                    )
                }

                Text(
                    text = "SVEB (SupraVentricular) VEB (Ventricular)",
                    fontSize = 12.sp,
                    color = ClinicalTextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Last Analysed Period: $sessionTimestamp",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClinicalTextPrimary
                )

                Text(
                    text = "Duration: $sessionDuration",
                    fontSize = 12.sp,
                    color = ClinicalTextSecondary
                )

                // Current Period Stats: SVEB / VEB / Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPillBox(
                        label = "SVEB",
                        value = svebCount.toString(),
                        bgColor = SvebBlueBg,
                        textColor = SvebBlueText,
                        modifier = Modifier.weight(1f)
                    )
                    StatPillBox(
                        label = "VEB",
                        value = vebCount.toString(),
                        bgColor = VebPinkBg,
                        textColor = VebPinkText,
                        modifier = Modifier.weight(1f)
                    )
                    StatPillBox(
                        label = "Total",
                        value = totalExtrasystoles.toString(),
                        bgColor = TotalGrayBg,
                        textColor = TotalGrayText,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Total all periods header
                Text(
                    text = "Total all periods",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClinicalTextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPillBox(
                        label = "SVEB tot.",
                        value = svebCount.toString(),
                        bgColor = SvebBlueBg,
                        textColor = SvebBlueText,
                        modifier = Modifier.weight(1f)
                    )
                    StatPillBox(
                        label = "VEB tot.",
                        value = vebCount.toString(),
                        bgColor = VebPinkBg,
                        textColor = VebPinkText,
                        modifier = Modifier.weight(1f)
                    )
                    StatPillBox(
                        label = "Total",
                        value = totalExtrasystoles.toString(),
                        bgColor = TotalGrayBg,
                        textColor = TotalGrayText,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Extrapolation banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SvebBlueBg)
                        .border(1.dp, MedicalBlue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MedicalBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Extrapolation. Total number per day:",
                            fontSize = 12.sp,
                            color = ClinicalTextSecondary
                        )
                    }
                    Text(
                        text = extrapolationPerDay.toString(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = SvebBlueText
                    )
                }
            }
        }

        // Search from the beginning button
        Button(
            onClick = onSearchFromBeginning,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_search_from_beginning"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MedicalNavy)
        ) {
            Icon(
                imageVector = Icons.Default.QueryStats,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Search from the beginning",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Action Buttons: Wave analysis & Diagnostic
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { onNavigateTab(AppTab.WAVES) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("btn_wave_analysis"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MedicalTeal)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Wave analysis (detailed)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedButton(
                onClick = { showDiagnosisCard = !showDiagnosisCard },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("btn_diagnostic"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (showDiagnosisCard) MedicalTeal else ClinicalTextSecondary
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Diagnostic report",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Evolution of extrasystoles over time Chart Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ClinicalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Evolution of extrasystoles over time",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ClinicalTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Chart Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClinicalCardBg)
                        .border(1.dp, ClinicalBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    EvolutionExtrasystolesChart()
                }
            }
        }

        // Medical Diagnosis Clinical Card (matches Screenshot 4)
        if (showDiagnosisCard) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = MedicalTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Medical diagnosis & Electrophysiology Review",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ClinicalTextPrimary
                        )
                    }

                    Text(
                        text = "A very high number of ventricular extrasystoles have been identified in this recording (compared to the recording duration). However, it is best to record at least 24 hours, as the number of extrasystoles can vary considerably during the day and night.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = ClinicalTextPrimary
                    )

                    Text(
                        text = "The vast majority of your extrasystoles are of the ventricular type (VEB: 312 vs SVEB: 31). VEB are less common than SVEB (Supra Ventricular Ectopic Beats), but they can be more concerning, especially if they occur frequently or in certain patterns. Here are some possible interpretations of many VEB and almost no SVEB:",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = ClinicalTextPrimary
                    )

                    Text(
                        text = "• Benign VEB: In some cases, frequent VEB are simply a variation of normal heart rhythm and do not indicate any underlying disease, especially in healthy individuals without structural heart disease.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = ClinicalTextSecondary
                    )

                    Text(
                        text = "• Couplets & Complex Ectopy: 19 VEB Couplets (consecutive pairs) detected with full compensatory pauses. Burden calculation indicates ~5.9% ventricular ectopy density (extrapolating to 6,302 beats/24h). Recommend 12-lead ECG and echocardiographic correlation.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = ClinicalTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPillBox(
    label: String,
    value: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun EvolutionExtrasystolesChart() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val paddingLeft = 32f
        val paddingBottom = 20f

        // Grid lines
        val gridYSteps = 4
        for (i in 0..gridYSteps) {
            val y = (h - paddingBottom) * (i.toFloat() / gridYSteps)
            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(paddingLeft, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Stepped / time curve of extrasystoles
        val path = Path()
        val points = listOf(
            0.0f to 0.1f, 0.15f to 0.1f, 0.25f to 0.35f, 0.35f to 0.85f,
            0.5f to 0.4f, 0.65f to 0.95f, 0.75f to 0.6f, 0.85f to 0.9f, 1.0f to 0.3f
        )

        points.forEachIndexed { index, (px, py) ->
            val x = paddingLeft + px * (w - paddingLeft)
            val y = (h - paddingBottom) - py * (h - paddingBottom - 10f)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = MedicalTeal,
            style = Stroke(width = 2.5f)
        )
    }
}
