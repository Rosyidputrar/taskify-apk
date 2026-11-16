package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.blend.Blend
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/** Cara palet diturunkan dari satu warna dasar (varian skema Material 3). */
enum class ThemeStyle(val label: String, val hint: String) {
    TonalSpot("Seimbang", "Warna dasar lembut, aksen kalem — paling nyaman dipakai lama"),
    Vibrant("Cerah", "Warna lebih jenuh dan hidup"),
    Expressive("Ekspresif", "Aksen sekunder berani dengan hue bergeser"),
    Neutral("Kalem", "Hampir netral, warna hanya sebagai sentuhan"),
    Fidelity("Setia warna", "Sedekat mungkin dengan warna yang dipilih"),
    Monochrome("Monokrom", "Hitam-putih bernuansa tinta, tanpa warna"),
}

/** Tingkat kontras (Material 3 contrast level). */
enum class ThemeContrast(val label: String, val level: Double) {
    Standard("Standar", 0.0),
    Medium("Sedang", 0.5),
    High("Tinggi", 1.0),
}

/** Palet siap pakai. [KertasTinta] = desain asli (warna diracik manual), sisanya diturunkan HCT. */
enum class ThemePalette(val label: String, val seed: Long, val style: ThemeStyle, val paper: Boolean) {
    KertasTinta("Kertas & Tinta", 0xFF1D5867, ThemeStyle.TonalSpot, true),
    Laut("Laut", 0xFF1F6FA0, ThemeStyle.TonalSpot, true),
    Hutan("Hutan", 0xFF2E6B45, ThemeStyle.TonalSpot, true),
    Senja("Senja", 0xFFC8553D, ThemeStyle.Vibrant, true),
    Lavender("Lavender", 0xFF7559B8, ThemeStyle.TonalSpot, true),
    Sakura("Sakura", 0xFFC2457A, ThemeStyle.Expressive, true),
    Grafit("Grafit", 0xFF5B6470, ThemeStyle.Neutral, false),
    /** Warna wallpaper (Android 12+). */
    Dinamis("Material You", 0xFF1D5867, ThemeStyle.TonalSpot, false),
    /** Warna dasar, gaya, dan nuansa pilihan pengguna. */
    Kustom("Kustom", 0xFF1D5867, ThemeStyle.TonalSpot, true),
    ;

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: KertasTinta
    }
}

/**
 * Tema yang dipilih pengguna. Untuk palet siap pakai, [seed]/[style]/[paper] milik palet; untuk
 * [ThemePalette.Kustom] dipakai nilai di sini.
 */
@Immutable
data class ThemeSpec(
    val palette: ThemePalette = ThemePalette.KertasTinta,
    val seed: Int = ThemePalette.KertasTinta.seed.toInt(),
    val style: ThemeStyle = ThemeStyle.TonalSpot,
    val paper: Boolean = true,
    val contrast: ThemeContrast = ThemeContrast.Standard,
) {
    val effectiveSeed: Int get() = if (palette == ThemePalette.Kustom) seed else palette.seed.toInt()
    val effectiveStyle: ThemeStyle get() = if (palette == ThemePalette.Kustom) style else palette.style
    val effectivePaper: Boolean get() = if (palette == ThemePalette.Kustom) paper else palette.paper

    /**
     * Kontras yang benar-benar dipakai mesin tema. Kertas & Tinta diracik manual pada kontras
     * Standar: menaikkan kontras tidak boleh me-regenerasi paletnya lewat HCT (itu mengganti
     * tema bawaan tanpa pemberitahuan), jadi kontrasnya terkunci di Standar. Palet lain dan
     * Kustom memakai kontras apa adanya.
     */
    val effectiveContrast: ThemeContrast get() = if (palette == ThemePalette.KertasTinta) ThemeContrast.Standard else contrast

    /** Desain asli tanpa perubahan apa pun (kontras standar — terkunci untuk Kertas & Tinta). */
    val isHandcrafted: Boolean get() = palette == ThemePalette.KertasTinta && effectiveContrast == ThemeContrast.Standard

    companion object {
        val Default = ThemeSpec()
    }
}

/**
 * Mesin tema: [ThemeSpec] → ColorScheme Material 3 + warna modul yang selaras. Semua peran
 * warna diturunkan dari palet tonal HCT, jadi pasangan teks/latar tetap memenuhi kontras pada
 * warna dasar apa pun (diuji di ThemeEngineTest).
 */
object ThemeEngine {
    fun scheme(spec: ThemeSpec, dark: Boolean, dynamic: ColorScheme? = null): ColorScheme {
        // Material You memakai skema wallpaper dari sistem apa adanya: sistem tidak
        // menyediakan skema yang sama dengan tingkat kontras lain, jadi kontras tidak
        // berlaku di sini (kontrol kontras dimatikan untuk Dinamis di ThemePaletteSettings).
        if (spec.palette == ThemePalette.Dinamis && dynamic != null) return dynamic
        if (spec.isHandcrafted) return if (dark) DarkScheme else LightScheme
        val ds = dynamicScheme(spec.effectiveSeed, spec.effectiveStyle, dark, spec.effectiveContrast.level)
        val generated = ds.toColorScheme(dark)
        return if (spec.effectivePaper) generated.withPaper(dark, ds) else generated
    }

    fun dynamicScheme(seed: Int, style: ThemeStyle, dark: Boolean, contrast: Double = 0.0): DynamicScheme {
        val hct = Hct.fromInt(seed)
        return when (style) {
            ThemeStyle.TonalSpot -> SchemeTonalSpot(hct, dark, contrast)
            ThemeStyle.Vibrant -> SchemeVibrant(hct, dark, contrast)
            ThemeStyle.Expressive -> SchemeExpressive(hct, dark, contrast)
            ThemeStyle.Neutral -> SchemeNeutral(hct, dark, contrast)
            ThemeStyle.Fidelity -> SchemeFidelity(hct, dark, contrast)
            ThemeStyle.Monochrome -> SchemeMonochrome(hct, dark, contrast)
        }
    }

    /**
     * Warna aksen modul untuk tema ini. Desain asli memakai warna racikan manual; tema lain
     * menyelaraskan hue modul ke warna utama (maks. ±15°, identitas modul tetap terbaca), lalu
     * mengunci tone supaya kontras teks/ikon di atas permukaan tetap aman.
     */
    fun moduleAccent(module: TaskifyModule, spec: ThemeSpec, dark: Boolean, primary: Color): Color {
        if (spec.isHandcrafted) return if (dark) module.dark else module.light
        val primaryHct = Hct.fromInt(primary.toArgb())
        // Hari Ini = warna utama aplikasi (seperti di desain asli); modul lain diselaraskan.
        val base = if (module == TaskifyModule.Home) primaryHct else Hct.fromInt((if (dark) module.dark else module.light).toArgb())
        val harmonized = if (module == TaskifyModule.Home) primaryHct else Blend.harmonize(base, primaryHct)
        val chromaScale = when (spec.effectiveStyle) {
            ThemeStyle.Monochrome -> 0.0
            ThemeStyle.Neutral -> 0.55
            else -> 1.0
        }
        val tone = when (spec.effectiveContrast) {
            ThemeContrast.Standard -> if (dark) 80.0 else 40.0
            ThemeContrast.Medium -> if (dark) 85.0 else 35.0
            ThemeContrast.High -> if (dark) 90.0 else 28.0
        }
        return Color(Hct.from(harmonized.hue, (base.chroma * chromaScale).coerceAtMost(72.0), tone).toInt())
    }

    /** Teks/ikon di atas aksen modul (tone aksen dikunci → putih / hampir hitam selalu cukup kontras). */
    fun onModuleAccent(dark: Boolean): Color = if (dark) TaskifyModule.onDark else TaskifyModule.onLight

    /** Contoh warna dasar cepat untuk pemilih tema kustom. */
    val quickSeeds: List<Long> = listOf(
        0xFF1D5867, 0xFF1F6FA0, 0xFF3F55A0, 0xFF7559B8, 0xFFC2457A, 0xFFB3261E,
        0xFFC8553D, 0xFFB7791F, 0xFF7A8A1F, 0xFF2E6B45, 0xFF1B7A6E, 0xFF5B6470,
    )

    /** Warna dasar dari hue (0–360) & kekuatan warna / chroma (0–120) — tone tengah. */
    fun seedFrom(hue: Double, chroma: Double): Int = Hct.from(hue, chroma, 50.0).toInt()

    fun hueOf(seed: Int): Double = Hct.fromInt(seed).hue

    fun chromaOf(seed: Int): Double = Hct.fromInt(seed).chroma

    /** Strip tonal (10…99) sebuah palet — untuk pratinjau di studio tema. */
    fun tones(seed: Int, style: ThemeStyle, dark: Boolean): List<List<Color>> {
        val ds = dynamicScheme(seed, style, dark)
        val steps = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99)
        return listOf(ds.primaryPalette, ds.secondaryPalette, ds.tertiaryPalette, ds.neutralPalette).map { p -> steps.map { Color(p.tone(it)) } }
    }

    /** Semua peran diisi eksplisit dari skema (gelap/terang sudah ditentukan oleh [DynamicScheme]). */
    @Suppress("UNUSED_PARAMETER")
    private fun DynamicScheme.toColorScheme(dark: Boolean): ColorScheme {
        fun c(argb: Int) = Color(argb)
        return lightColorScheme(
            primary = c(primary), onPrimary = c(onPrimary), primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
            inversePrimary = c(inversePrimary),
            secondary = c(secondary), onSecondary = c(onSecondary), secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
            tertiary = c(tertiary), onTertiary = c(onTertiary), tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
            background = c(background), onBackground = c(onBackground), surface = c(surface), onSurface = c(onSurface),
            surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant), surfaceTint = c(surfaceTint),
            inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
            error = c(error), onError = c(onError), errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
            outline = c(outline), outlineVariant = c(outlineVariant), scrim = c(scrim),
            surfaceBright = c(surfaceBright), surfaceContainer = c(surfaceContainer), surfaceContainerHigh = c(surfaceContainerHigh),
            surfaceContainerHighest = c(surfaceContainerHighest), surfaceContainerLow = c(surfaceContainerLow),
            surfaceContainerLowest = c(surfaceContainerLowest), surfaceDim = c(surfaceDim),
            primaryFixed = c(primaryFixed), primaryFixedDim = c(primaryFixedDim), onPrimaryFixed = c(onPrimaryFixed), onPrimaryFixedVariant = c(onPrimaryFixedVariant),
            secondaryFixed = c(secondaryFixed), secondaryFixedDim = c(secondaryFixedDim), onSecondaryFixed = c(onSecondaryFixed), onSecondaryFixedVariant = c(onSecondaryFixedVariant),
            tertiaryFixed = c(tertiaryFixed), tertiaryFixedDim = c(tertiaryFixedDim), onTertiaryFixed = c(onTertiaryFixed), onTertiaryFixedVariant = c(onTertiaryFixedVariant),
        )
    }

    /**
     * "Nuansa kertas": permukaan & teks netral memakai kertas krem / tinta arang khas Taskify,
     * sementara warna utama, sekunder, tersier, dan kontainernya dari palet yang dipilih.
     * Kontras tinggi → teks netral dari skema (lebih pekat) dipertahankan.
     */
    private fun ColorScheme.withPaper(dark: Boolean, ds: DynamicScheme): ColorScheme {
        val base = if (dark) DarkScheme else LightScheme
        val strong = ds.contrastLevel > 0.0
        return copy(
            background = base.background,
            onBackground = if (strong) onBackground else base.onBackground,
            surface = base.surface,
            onSurface = if (strong) onSurface else base.onSurface,
            surfaceVariant = base.surfaceVariant,
            onSurfaceVariant = if (strong) onSurfaceVariant else base.onSurfaceVariant,
            outline = if (strong) outline else base.outline,
            outlineVariant = base.outlineVariant,
            surfaceBright = base.surfaceBright,
            surfaceDim = base.surfaceDim,
            surfaceContainerLowest = base.surfaceContainerLowest,
            surfaceContainerLow = base.surfaceContainerLow,
            surfaceContainer = base.surfaceContainer,
            surfaceContainerHigh = base.surfaceContainerHigh,
            surfaceContainerHighest = base.surfaceContainerHighest,
            inverseSurface = base.inverseSurface,
            inverseOnSurface = base.inverseOnSurface,
        )
    }
}
