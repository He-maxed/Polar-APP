package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppTab
import com.example.ui.theme.ClinicalBorder
import com.example.ui.theme.ClinicalSurface
import com.example.ui.theme.ClinicalTextPrimary
import com.example.ui.theme.ClinicalTextSecondary
import com.example.ui.theme.MedicalTeal

@Composable
fun ClinicalBottomNavigation(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ClinicalSurface)
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTabItem(
            icon = Icons.Default.FiberManualRecord,
            label = "Live",
            isSelected = selectedTab == AppTab.LIVE_OSCILLOSCOPE,
            onClick = { onTabSelected(AppTab.LIVE_OSCILLOSCOPE) },
            testTag = "tab_live"
        )
        NavTabItem(
            icon = Icons.Default.Assessment,
            label = "Analyze",
            isSelected = selectedTab == AppTab.PERIODIC,
            onClick = { onTabSelected(AppTab.PERIODIC) },
            testTag = "tab_analyze"
        )
        NavTabItem(
            icon = Icons.Default.ShowChart,
            label = "ECG Strip",
            isSelected = selectedTab == AppTab.ECG_STRIP,
            onClick = { onTabSelected(AppTab.ECG_STRIP) },
            testTag = "tab_ecg"
        )
        NavTabItem(
            icon = Icons.Default.History,
            label = "Recordings",
            isSelected = selectedTab == AppTab.SAVED_RECORDINGS,
            onClick = { onTabSelected(AppTab.SAVED_RECORDINGS) },
            testTag = "tab_saved"
        )
        NavTabItem(
            icon = Icons.Default.Air,
            label = "HRV",
            isSelected = selectedTab == AppTab.HRV,
            onClick = { onTabSelected(AppTab.HRV) },
            testTag = "tab_hrv"
        )
        NavTabItem(
            icon = Icons.Default.DirectionsRun,
            label = "Activity",
            isSelected = selectedTab == AppTab.ACTIVITY,
            onClick = { onTabSelected(AppTab.ACTIVITY) },
            testTag = "tab_activity"
        )
    }
}

@Composable
private fun NavTabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MedicalTeal else ClinicalTextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MedicalTeal else ClinicalTextSecondary
        )
    }
}
