package com.gympulse.app.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object GymPulsePalette {
    val Volt = Color(0xFFD4FF3A)
    val VoltDeep = Color(0xFF4A6A00)
    val Cyan = Color(0xFF48D8FF)
    val CyanDeep = Color(0xFF006684)
    val Coral = Color(0xFFFF7A45)
    val CoralDeep = Color(0xFFB5420F)
    val Gold = Color(0xFFFFC94D)
    val Mint = Color(0xFF3DDC97)
    val Violet = Color(0xFF9C8CFF)
    val Rose = Color(0xFFFF5D8F)
    val Night = Color(0xFF0B0D12)
}

val DarkColors: ColorScheme = darkColorScheme(
    primary = GymPulsePalette.Volt,
    onPrimary = Color(0xFF141A00),
    primaryContainer = Color(0xFF2B3A00),
    onPrimaryContainer = Color(0xFFE2FF8A),
    inversePrimary = GymPulsePalette.VoltDeep,
    secondary = GymPulsePalette.Cyan,
    onSecondary = Color(0xFF00222D),
    secondaryContainer = Color(0xFF0B3A4A),
    onSecondaryContainer = Color(0xFFB8EEFF),
    tertiary = GymPulsePalette.Coral,
    onTertiary = Color(0xFF2E0E00),
    tertiaryContainer = Color(0xFF5A2410),
    onTertiaryContainer = Color(0xFFFFD3C2),
    error = Color(0xFFFF5D6C),
    onError = Color(0xFF3B0009),
    errorContainer = Color(0xFF5C1520),
    onErrorContainer = Color(0xFFFFDADC),
    background = GymPulsePalette.Night,
    onBackground = Color(0xFFEEF1F6),
    surface = GymPulsePalette.Night,
    onSurface = Color(0xFFEEF1F6),
    surfaceVariant = Color(0xFF1D222C),
    onSurfaceVariant = Color(0xFF9AA3B2),
    surfaceTint = GymPulsePalette.Volt,
    inverseSurface = Color(0xFFEEF1F6),
    inverseOnSurface = Color(0xFF14171D),
    outline = Color(0xFF3A4150),
    outlineVariant = Color(0xFF262C37),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2A303C),
    surfaceDim = GymPulsePalette.Night,
    surfaceContainerLowest = Color(0xFF07080B),
    surfaceContainerLow = Color(0xFF111419),
    surfaceContainer = Color(0xFF161A21),
    surfaceContainerHigh = Color(0xFF1D222B),
    surfaceContainerHighest = Color(0xFF262C37),
)

val LightColors: ColorScheme = lightColorScheme(
    primary = GymPulsePalette.VoltDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4FF3A),
    onPrimaryContainer = Color(0xFF1A2400),
    inversePrimary = GymPulsePalette.Volt,
    secondary = GymPulsePalette.CyanDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC2EBFF),
    onSecondaryContainer = Color(0xFF001F2A),
    tertiary = GymPulsePalette.CoralDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCD),
    onTertiaryContainer = Color(0xFF380D00),
    error = Color(0xFFBA1A2A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDADB),
    onErrorContainer = Color(0xFF40000A),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF12151B),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF12151B),
    surfaceVariant = Color(0xFFE3E6EC),
    onSurfaceVariant = Color(0xFF545C6A),
    surfaceTint = GymPulsePalette.VoltDeep,
    inverseSurface = Color(0xFF22262E),
    inverseOnSurface = Color(0xFFF0F2F6),
    outline = Color(0xFFB9BFCA),
    outlineVariant = Color(0xFFDCE0E7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDCDFE5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF1F5),
    surfaceContainerHighest = Color(0xFFE6E9EF),
)

/** Colors outside of the Material scheme: semantic states and data visualisation. */
@Immutable
data class GymPulseExtendedColors(
    val success: Color,
    val warning: Color,
    val record: Color,
    val rest: Color,
    val chart: List<Color>,
    val heroGradient: List<Color>,
)

val DarkExtendedColors = GymPulseExtendedColors(
    success = GymPulsePalette.Mint,
    warning = GymPulsePalette.Gold,
    record = GymPulsePalette.Gold,
    rest = GymPulsePalette.Cyan,
    chart = listOf(GymPulsePalette.Volt, GymPulsePalette.Cyan, GymPulsePalette.Coral, GymPulsePalette.Violet, GymPulsePalette.Gold, GymPulsePalette.Rose),
    heroGradient = listOf(Color(0xFF26330A), Color(0xFF12221F), Color(0xFF0F141C)),
)

val LightExtendedColors = GymPulseExtendedColors(
    success = Color(0xFF0E8A57),
    warning = Color(0xFFB07800),
    record = Color(0xFFB07800),
    rest = GymPulsePalette.CyanDeep,
    chart = listOf(GymPulsePalette.VoltDeep, GymPulsePalette.CyanDeep, GymPulsePalette.CoralDeep, Color(0xFF5B4BD6), Color(0xFFB07800), Color(0xFFC2185B)),
    heroGradient = listOf(Color(0xFFE7FFB0), Color(0xFFD5F3FF), Color(0xFFFFFFFF)),
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }
