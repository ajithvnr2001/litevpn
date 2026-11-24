package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TechnicalDarkColorScheme = darkColorScheme(
    primary = InkLight,
    onPrimary = ObsidianBg,
    primaryContainer = ObsidianSurfaceHighest,
    onPrimaryContainer = InkLight,
    secondary = EmeraldProtected,
    onSecondary = Color(0xFF052E16),
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = ElectricCyan,
    onTertiary = OnElectricCyan,
    tertiaryContainer = CyanContainer,
    onTertiaryContainer = OnCyanContainer,
    background = ObsidianBg,
    onBackground = InkLight,
    surface = ObsidianSurface,
    onSurface = InkLight,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurfaceHigh,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = HairlineBorder,
    error = CoralError,
    onError = Color(0xFF2B0709),
    errorContainer = CoralErrorContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val CrispLightColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.White,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OnOceanPrimaryContainer,
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldLightContainer,
    onSecondaryContainer = Color(0xFF052E16),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0C4A6E),
    background = FrostBg,
    onBackground = InkDark,
    surface = FrostSurface,
    onSurface = InkDark,
    surfaceVariant = FrostSurfaceHigh,
    onSurfaceVariant = InkDarkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = FrostSurfaceHigh,
    surfaceContainer = FrostSurfaceHigh,
    surfaceContainerHigh = Color(0xFFE5E7EB),
    outline = FrostOutline,
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun SwiftTunnelTheme(
    darkTheme: Boolean = true,
    followSystem: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = if (followSystem) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (useDark) TechnicalDarkColorScheme else CrispLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
