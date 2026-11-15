package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rosyidputrar.taskify.core.designsystem.R

@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: FontWeight) = Font(
    R.font.fraunces,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        // Sumbu SOFT membuat serif lebih bulat — terasa seperti tinta di kertas, bukan koran.
        FontVariation.Setting("SOFT", 60f),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun sans(weight: FontWeight) = Font(
    R.font.instrument_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun mono(weight: FontWeight) = Font(
    R.font.jetbrains_mono,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Serif display: tanggal besar, judul layar, angka saldo. */
val SerifFamily = FontFamily(
    fraunces(FontWeight.Normal),
    fraunces(FontWeight.Medium),
    fraunces(FontWeight.SemiBold),
    fraunces(FontWeight.Bold),
)

/** Sans untuk isi & kontrol. */
val SansFamily = FontFamily(
    sans(FontWeight.Normal),
    sans(FontWeight.Medium),
    sans(FontWeight.SemiBold),
    sans(FontWeight.Bold),
)

/** Monospace: kode, sandi, TOTP. */
val MonoFamily = FontFamily(
    mono(FontWeight.Normal),
    mono(FontWeight.Medium),
    mono(FontWeight.Bold),
)

internal val TaskifyTypography = Typography(
    displayLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.02).em),
    displaySmall = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.01).em),
    headlineLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.01.em),
    titleSmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    bodyLarge = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.04.em),
    labelSmall = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.06.em),
)

/** Gaya tambahan di luar skala Material. */
object TaskifyTextStyles {
    /** Label tab indeks: huruf kapital renggang di atas judul layar. */
    val indexLabel = TextStyle(fontFamily = SansFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.14.em)
    val code = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp)
    val numeric = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em)
}
