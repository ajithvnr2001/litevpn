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

private val TacticalDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = EmeraldProtected,
    onSecondary = Color(0xFF002110),
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = CloudflareAmber,
    onTertiary = Color(0xFF2B1700),
    tertiaryContainer = CloudflareAmberContainer,
    onTertiaryContainer = Color(0xFFFFDDB5),
    background = ObsidianBg,
    onBackground = InkLight,
    surface = ObsidianSurface,
    onSurface = InkLight,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = Color(0xFF1D2942),
    error = CoralError,
    onError = Color(0xFF3E0006),
    errorContainer = CoralErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val CrispLightColorScheme = lightColorScheme(
    primary = OceanPrimary,
    onPrimary = Color.White,
    primaryContainer = OceanPrimaryContainer,
    onPrimaryContainer = OnOceanPrimaryContainer,
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldLightContainer,
    onSecondaryContainer = Color(0xFF002111),
    tertiary = Color(0xFFD16D00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE5C7),
    onTertiaryContainer = Color(0xFF2D1600),
    background = FrostBg,
    onBackground = InkDark,
    surface = FrostSurface,
    onSurface = InkDark,
    surfaceVariant = FrostSurfaceHigh,
    onSurfaceVariant = InkDarkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = FrostSurface,
    surfaceContainer = FrostSurfaceHigh,
    surfaceContainerHigh = Color(0xFFDDE6F5),
    outline = FrostOutline,
    outlineVariant = Color(0xFFDCE4F2),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SwiftTunnelTheme(
    darkTheme: Boolean = true,
    followSystem: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = if (followSystem) isSystemInDarkTheme() else darkTheme
    val colorScheme = if (useDark) TacticalDarkColorScheme else CrispLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
