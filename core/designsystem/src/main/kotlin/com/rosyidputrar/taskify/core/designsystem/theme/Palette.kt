package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * "Kertas & Tinta": permukaan solid. Terang = kertas krem hangat, gelap = tinta arang kebiruan.
 * Semua pasangan teks/latar sudah diukur ≥ 4.5:1 (lihat docs/DESIGN.md).
 */
internal object Paper {
    val background = Color(0xFFF3EEE4)
    val surface = Color(0xFFFAF7F1)
    val surfaceDim = Color(0xFFE7DFD1)
    val containerLowest = Color(0xFFFFFDF9)
    val containerLow = Color(0xFFF6F1E8)
    val container = Color(0xFFEEE7DB)
    val containerHigh = Color(0xFFE7DFD1)
    val containerHighest = Color(0xFFDFD6C6)
    val ink = Color(0xFF1F1C17)
    val inkSoft = Color(0xFF5A5348)
    val outline = Color(0xFF8C8373)
    val outlineVariant = Color(0xFFD3CABA)
}

internal object Ink {
    val background = Color(0xFF121518)
    val surface = Color(0xFF171B1F)
    val surfaceBright = Color(0xFF30383F)
    val containerLowest = Color(0xFF0E1013)
    val containerLow = Color(0xFF1A1F24)
    val container = Color(0xFF1F252B)
    val containerHigh = Color(0xFF272E35)
    val containerHighest = Color(0xFF30383F)
    val paperText = Color(0xFFECE5D8)
    val paperTextSoft = Color(0xFFB9B1A3)
    val outline = Color(0xFF857E72)
    val outlineVariant = Color(0xFF3A4148)
}

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF1D5867),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCBE6EC),
    onPrimaryContainer = Color(0xFF0B2F38),
    inversePrimary = Color(0xFF8FD0DF),
    secondary = Color(0xFF9C4A27),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6D9CB),
    onSecondaryContainer = Color(0xFF3D1806),
    tertiary = Color(0xFF7A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E1A4),
    onTertiaryContainer = Color(0xFF2A1E00),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Paper.background,
    onBackground = Paper.ink,
    surface = Paper.surface,
    onSurface = Paper.ink,
    surfaceVariant = Paper.container,
    onSurfaceVariant = Paper.inkSoft,
    surfaceTint = Color(0xFF1D5867),
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF6F0E6),
    outline = Paper.outline,
    outlineVariant = Paper.outlineVariant,
    scrim = Color.Black,
    surfaceBright = Paper.containerLowest,
    surfaceDim = Paper.surfaceDim,
    surfaceContainerLowest = Paper.containerLowest,
    surfaceContainerLow = Paper.containerLow,
    surfaceContainer = Paper.container,
    surfaceContainerHigh = Paper.containerHigh,
    surfaceContainerHighest = Paper.containerHighest,
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8FD0DF),
    onPrimary = Color(0xFF003641),
    primaryContainer = Color(0xFF1D4E5B),
    onPrimaryContainer = Color(0xFFCBEFF7),
    inversePrimary = Color(0xFF1D5867),
    secondary = Color(0xFFF2B394),
    onSecondary = Color(0xFF4F1F08),
    secondaryContainer = Color(0xFF7A3517),
    onSecondaryContainer = Color(0xFFFFDCCB),
    tertiary = Color(0xFFE7C45A),
    onTertiary = Color(0xFF3E2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFE7A6),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Ink.background,
    onBackground = Ink.paperText,
    surface = Ink.surface,
    onSurface = Ink.paperText,
    surfaceVariant = Ink.container,
    onSurfaceVariant = Ink.paperTextSoft,
    surfaceTint = Color(0xFF8FD0DF),
    inverseSurface = Color(0xFFECE5D8),
    inverseOnSurface = Color(0xFF2A2A28),
    outline = Ink.outline,
    outlineVariant = Ink.outlineVariant,
    scrim = Color.Black,
    surfaceBright = Ink.surfaceBright,
    surfaceDim = Ink.background,
    surfaceContainerLowest = Ink.containerLowest,
    surfaceContainerLow = Ink.containerLow,
    surfaceContainer = Ink.container,
    surfaceContainerHigh = Ink.containerHigh,
    surfaceContainerHighest = Ink.containerHighest,
)
