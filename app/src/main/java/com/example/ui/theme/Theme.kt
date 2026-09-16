package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClinicalColorScheme = lightColorScheme(
    primary = MedicalTeal,
    onPrimary = Color.White,
    primaryContainer = MedicalCyan.copy(alpha = 0.15f),
    onPrimaryContainer = MedicalTeal,
    secondary = MedicalBlue,
    onSecondary = Color.White,
    background = ClinicalBg,
    onBackground = ClinicalTextPrimary,
    surface = ClinicalSurface,
    onSurface = ClinicalTextPrimary,
    surfaceVariant = ClinicalCardBg,
    onSurfaceVariant = ClinicalTextSecondary,
    outline = ClinicalBorder,
    error = MedicalRed
)

private val ClinicalDarkColorScheme = darkColorScheme(
    primary = OscPhosphorGreen,
    onPrimary = Color.Black,
    secondary = OscCaliperCyan,
    background = OscDarkBg,
    surface = Color(0xFF141D26),
    onBackground = Color(0xFFE0E6ED),
    onSurface = Color(0xFFE0E6ED),
    outline = Color(0xFF2C3E50)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Default to clinical high-contrast workstation view
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ClinicalDarkColorScheme else ClinicalColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
