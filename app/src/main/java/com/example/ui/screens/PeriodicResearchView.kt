package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.example.model.RhythmEvent
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.example.model.RhythmEventType
import com.example.ui.AnalysisSourceMode
import com.example.ui.DetailedEcgViewState
import com.example.ui.components.HeartRateTrendChart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PeriodicResearchView(
    svebCount: Int,
    vebCount: Int,
    totalExtrasystoles: Int,
    extrapolationPerDay: Int,
    sessionTimestamp: String,
    sessionDuration: String,
    rhythmEvents: List<RhythmEvent>,
    analysisSourceMode: AnalysisSourceMode = AnalysisSourceMode.CURRENT_SESSION,
    hrHistory: List<Pair<Long, Int>> = emptyList(),
    detailViewState: DetailedEcgViewState = DetailedEcgViewState(),
    onNavigateTab: (AppTab) -> Unit,
    onSearchFromBeginning: () -> Unit,
    onJumpToSnippet: (Long) -> Unit,
    onSelectSourceMode: (AnalysisSourceMode) -> Unit = {},
    onSelectHrZoom: (Int) -> Unit = {},
    onSeekTimestamp: (Long) -> Unit = {},
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
        // Analysis Source Mode Selector (Current Session vs Saved Recording)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = analysisSourceMode == AnalysisSourceMode.CURRENT_SESSION,
                onClick = { onSelectSourceMode(AnalysisSourceMode.CURRENT_SESSION) },
                label = { Text("Current Session", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MedicalTeal,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chip_source_current")
            )
            FilterChip(
                selected = analysisSourceMode == AnalysisSourceMode.SAVED_RECORDING,
                onClick = { onNavigateTab(AppTab.SAVED_RECORDINGS) },
                label = { Text("Saved Recordings", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MedicalNavy,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chip_source_saved")
            )
        }
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

        // Heart Rate Trend Chart on Main Page with Min, Avg & Max HR values
        HeartRateTrendChart(
            hrPoints = hrHistory,
            sessionStartTimeMs = detailViewState.sessionStartTimeMs,
            sessionEndTimeMs = detailViewState.sessionEndTimeMs,
            currentCursorTimeMs = detailViewState.centerTimeMs,
            windowDurationSeconds = detailViewState.windowDurationSeconds,
            selectedZoomMinutes = detailViewState.hrChartZoomMinutes,
            onSelectZoomMinutes = onSelectHrZoom,
            onSeekTimestamp = onSeekTimestamp,
            modifier = Modifier.testTag("main_hr_trend_chart")
        )

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

        // Detected Arrhythmias & Rhythm Events Card (AFib, VTach, Couplets, Pauses, etc.)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = MedicalRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Detected Arrhythmias & Rhythm Events",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClinicalTextPrimary
                    )
                }

                // Arrhythmia Filter Chips (Toggling each hides/shows that type)
                var selectedTypes by remember {
                    mutableStateOf(RhythmEventType.entries.toSet())
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RhythmEventType.entries.forEach { type ->
                        val isSel = selectedTypes.contains(type)
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedTypes = if (isSel) selectedTypes - type else selectedTypes + type
                            },
                            label = { Text(type.title, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MedicalRed,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                val filteredEvents = rhythmEvents.filter { selectedTypes.contains(it.type) }

                if (filteredEvents.isEmpty()) {
                    Text(
                        text = if (rhythmEvents.isEmpty()) "No arrhythmias detected in this session." else "No events match the selected arrhythmia filters.",
                        fontSize = 12.sp,
                        color = ClinicalTextSecondary,
                        lineHeight = 17.sp
                    )
                } else {
                    val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    filteredEvents.forEach { event ->
                        val startStr = timeFmt.format(Date(event.startTimestampMs))
                        val endStr = timeFmt.format(Date(event.endTimestampMs))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ClinicalCardBg),
                            border = BorderStroke(1.dp, ClinicalBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = event.type.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MedicalRed
                                    )
                                    Text(
                                        text = "$startStr - $endStr",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ClinicalTextSecondary
                                    )
                                }
                                Text(
                                    text = event.details,
                                    fontSize = 12.sp,
                                    color = ClinicalTextPrimary,
                                    lineHeight = 16.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "Duration: %.1fs", event.durationSeconds),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ClinicalTextSecondary
                                    )
                                    OutlinedButton(
                                        onClick = { onJumpToSnippet(event.startTimestampMs) },
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MedicalTeal)
                                    ) {
                                        Text(
                                            text = "Jump to snippet",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                    EvolutionExtrasystolesChart(rhythmEvents = rhythmEvents)
                }
            }
        }

        // Medical Diagnosis Clinical Card
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

                    val coupletCount = rhythmEvents.count { it.type == RhythmEventType.VEB_COUPLET }
                    Text(
                        text = "Recorded Ectopy Breakdown: Ventricular (VEB: $vebCount) vs Supraventricular (SVEB: $svebCount). Total Ectopic Beats: $totalExtrasystoles.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = ClinicalTextPrimary
                    )

                    Text(
                        text = "• Clinical Summary: $coupletCount VEB Couplet(s) detected during this session. Total extrapolated daily burden: $extrapolationPerDay ectopies/24h.",
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
private fun EvolutionExtrasystolesChart(rhythmEvents: List<RhythmEvent>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val paddingLeft = 32f
        val paddingBottom = 20f

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

        val path = Path()
        if (rhythmEvents.size >= 2) {
            val sortedEvents = rhythmEvents.sortedBy { it.startTimestampMs }
            val startMs = sortedEvents.first().startTimestampMs
            val endMs = sortedEvents.last().startTimestampMs
            val spanMs = (endMs - startMs).coerceAtLeast(1000L).toFloat()

            var started = false
            sortedEvents.forEachIndexed { idx, ev ->
                val fracX = (ev.startTimestampMs - startMs).toFloat() / spanMs
                val x = paddingLeft + fracX * (w - paddingLeft)
                val normY = (idx.toFloat() / (sortedEvents.size - 1).toFloat()).coerceIn(0.1f, 0.9f)
                val y = (h - paddingBottom) - normY * (h - paddingBottom - 10f)

                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
        } else {
            // Flat baseline if no events
            val y = (h - paddingBottom) * 0.9f
            path.moveTo(paddingLeft, y)
            path.lineTo(w, y)
        }

        drawPath(
            path = path,
            color = MedicalTeal,
            style = Stroke(width = 2.5f)
        )
    }
}
