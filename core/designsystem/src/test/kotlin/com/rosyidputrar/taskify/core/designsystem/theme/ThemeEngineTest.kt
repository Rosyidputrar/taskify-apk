package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tema apa pun — siap pakai maupun kustom dari warna dasar sembarang — harus tetap terbaca:
 * pasangan teks/latar utama & warna modul ≥ 4.5:1 (WCAG AA teks normal).
 */
class ThemeEngineTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    private fun check(label: String, spec: ThemeSpec, dark: Boolean) {
        val s: ColorScheme = ThemeEngine.scheme(spec, dark)
        val pairs = listOf(
            "onSurface/surface" to (s.onSurface to s.surface),
            "onBackground/background" to (s.onBackground to s.background),
            "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
            "onSurface/containerHighest" to (s.onSurface to s.surfaceContainerHighest),
            "onPrimary/primary" to (s.onPrimary to s.primary),
            "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
            "onTertiary/tertiary" to (s.onTertiary to s.tertiary),
            "onError/error" to (s.onError to s.error),
            "primary/surface" to (s.primary to s.surface),
        )
        for ((name, pair) in pairs) {
            val r = ratio(pair.first, pair.second)
            assertTrue(r >= 4.5, "$label ${if (dark) "gelap" else "terang"}: $name = %.2f".format(r))
        }
        val modules = ModuleColors(dark, spec, s.primary)
        for (m in TaskifyModule.entries) {
            val accent = modules.accent(m)
            assertTrue(ratio(accent, s.background) >= 4.5, "$label ${m.name} vs background = %.2f".format(ratio(accent, s.background)))
            assertTrue(ratio(accent, s.surface) >= 4.5, "$label ${m.name} vs surface = %.2f".format(ratio(accent, s.surface)))
            assertTrue(ratio(modules.onAccent(m), accent) >= 4.5, "$label ${m.name} onAccent = %.2f".format(ratio(modules.onAccent(m), accent)))
        }
    }

    @Test fun everyPresetIsReadableAtEveryContrast() {
        for (p in ThemePalette.entries) for (c in ThemeContrast.entries) for (dark in listOf(false, true)) {
            // KertasTinta/Standar dilewati di sini karena diuji eksplisit di
            // kertasTintaStandardIsExplicitlyHandcrafted (harus SAMA PERSIS dengan skema racikan
            // manual, bukan sekadar terbaca).
            if (p == ThemePalette.KertasTinta && c == ThemeContrast.Standard) continue
            check("${p.name}/${c.name}", ThemeSpec(palette = p, contrast = c), dark)
        }
    }

    /** KertasTinta/Standar = desain asli, diuji eksplisit (bukan dilewati diam-diam). */
    @Test fun kertasTintaStandardIsExplicitlyHandcrafted() {
        for (dark in listOf(false, true)) {
            val spec = ThemeSpec(palette = ThemePalette.KertasTinta, contrast = ThemeContrast.Standard)
            assertTrue(spec.isHandcrafted, "KertasTinta/Standar = desain asli")
            assertEquals(if (dark) DarkScheme else LightScheme, ThemeEngine.scheme(spec, dark))
            val modules = ModuleColors(dark, spec, (if (dark) DarkScheme else LightScheme).primary)
            assertEquals(if (dark) TaskifyModule.Tasks.dark else TaskifyModule.Tasks.light, modules.accent(TaskifyModule.Tasks))
        }
    }

    /** KertasTinta + Sedang/Tinggi: kontras terkunci di Standar, palet TIDAK di-regenerasi. */
    @Test fun kertasTintaContrastLocksToStandard() {
        for (c in listOf(ThemeContrast.Medium, ThemeContrast.High)) for (dark in listOf(false, true)) {
            val spec = ThemeSpec(palette = ThemePalette.KertasTinta, contrast = c)
            assertEquals(ThemeContrast.Standard, spec.effectiveContrast, "kontras KertasTinta terkunci di Standar")
            assertTrue(spec.isHandcrafted, "KertasTinta/$c tetap desain asli")
            assertEquals(if (dark) DarkScheme else LightScheme, ThemeEngine.scheme(spec, dark), "KertasTinta/$c tidak me-regenerasi palet")
            val modules = ModuleColors(dark, spec, (if (dark) DarkScheme else LightScheme).primary)
            assertEquals(if (dark) TaskifyModule.Tasks.dark else TaskifyModule.Tasks.light, modules.accent(TaskifyModule.Tasks), "aksen modul KertasTinta/$c tetap racikan manual")
        }
    }

    /** Kustom + Sedang/Tinggi: kontras benar-benar diterapkan dan tetap terbaca. */
    @Test fun customThemesStayReadableAtHigherContrast() {
        val seeds = ThemeEngine.quickSeeds.map { it.toInt() }
        for (seed in seeds) for (style in ThemeStyle.entries) for (paper in listOf(true, false)) for (c in listOf(ThemeContrast.Medium, ThemeContrast.High)) for (dark in listOf(false, true)) {
            check("seed=%08X/${style.name}/paper=$paper/${c.name}".format(seed), ThemeSpec(ThemePalette.Kustom, seed, style, paper, c), dark)
        }
    }

    /**
     * Dinamis + kontras apa pun: skema dinamis dikembalikan apa adanya, kontras tidak diterapkan.
     * Skema dinamis asli (wallpaper) tidak bisa dibuat di JVM polos, jadi yang diuji di sini
     * adalah perilaku mesin terhadap skema dinamis apa pun — bukan skema wallpaper itu sendiri.
     * Skema wallpaper asli hanya dipakai lewat TaskifyTheme (dynamicLight/DarkColorScheme) dan
     * tidak diuji di Robolectric: di Robolectric WallpaperManager tidak punya warna wallpaper,
     * jadi dynamicLightColorScheme tidak menghasilkan skema wallpaper nyata.
     */
    @Test fun dynamicSchemeIsReturnedUnchangedRegardlessOfContrast() {
        val fakeDynamic = lightColorScheme(
            primary = Color(0xFF123456), onPrimary = Color.White,
            primaryContainer = Color(0xFF234567), onPrimaryContainer = Color.White,
            background = Color(0xFFF0F0F0), onBackground = Color.Black,
            surface = Color.White, onSurface = Color.Black,
        )
        for (c in ThemeContrast.entries) for (dark in listOf(false, true)) {
            assertEquals(fakeDynamic, ThemeEngine.scheme(ThemeSpec(palette = ThemePalette.Dinamis, contrast = c), dark, dynamic = fakeDynamic), "Dinamis/$c harus mengembalikan skema dinamis apa adanya")
        }
    }

    /**
     * ColorScheme tidak punya equals struktural (dua skema dengan peran warna yang sama persis
     * tetap dianggap berbeda bila instansnya berbeda), jadi kesamaan skema dibandingkan
     * peran demi peran.
     */
    private fun assertSameScheme(expected: ColorScheme, actual: ColorScheme, message: String) {
        val roles = listOf(
            ColorScheme::primary to "primary", ColorScheme::onPrimary to "onPrimary",
            ColorScheme::primaryContainer to "primaryContainer", ColorScheme::onPrimaryContainer to "onPrimaryContainer",
            ColorScheme::inversePrimary to "inversePrimary",
            ColorScheme::secondary to "secondary", ColorScheme::onSecondary to "onSecondary",
            ColorScheme::secondaryContainer to "secondaryContainer", ColorScheme::onSecondaryContainer to "onSecondaryContainer",
            ColorScheme::tertiary to "tertiary", ColorScheme::onTertiary to "onTertiary",
            ColorScheme::tertiaryContainer to "tertiaryContainer", ColorScheme::onTertiaryContainer to "onTertiaryContainer",
            ColorScheme::background to "background", ColorScheme::onBackground to "onBackground",
            ColorScheme::surface to "surface", ColorScheme::onSurface to "onSurface",
            ColorScheme::surfaceVariant to "surfaceVariant", ColorScheme::onSurfaceVariant to "onSurfaceVariant",
            ColorScheme::surfaceTint to "surfaceTint",
            ColorScheme::inverseSurface to "inverseSurface", ColorScheme::inverseOnSurface to "inverseOnSurface",
            ColorScheme::error to "error", ColorScheme::onError to "onError",
            ColorScheme::errorContainer to "errorContainer", ColorScheme::onErrorContainer to "onErrorContainer",
            ColorScheme::outline to "outline", ColorScheme::outlineVariant to "outlineVariant",
            ColorScheme::scrim to "scrim",
            ColorScheme::surfaceBright to "surfaceBright", ColorScheme::surfaceContainer to "surfaceContainer",
            ColorScheme::surfaceContainerHigh to "surfaceContainerHigh",
            ColorScheme::surfaceContainerHighest to "surfaceContainerHighest",
            ColorScheme::surfaceContainerLow to "surfaceContainerLow",
            ColorScheme::surfaceContainerLowest to "surfaceContainerLowest",
            ColorScheme::surfaceDim to "surfaceDim",
            ColorScheme::primaryFixed to "primaryFixed", ColorScheme::primaryFixedDim to "primaryFixedDim",
            ColorScheme::onPrimaryFixed to "onPrimaryFixed", ColorScheme::onPrimaryFixedVariant to "onPrimaryFixedVariant",
            ColorScheme::secondaryFixed to "secondaryFixed", ColorScheme::secondaryFixedDim to "secondaryFixedDim",
            ColorScheme::onSecondaryFixed to "onSecondaryFixed", ColorScheme::onSecondaryFixedVariant to "onSecondaryFixedVariant",
            ColorScheme::tertiaryFixed to "tertiaryFixed", ColorScheme::tertiaryFixedDim to "tertiaryFixedDim",
            ColorScheme::onTertiaryFixed to "onTertiaryFixed", ColorScheme::onTertiaryFixedVariant to "onTertiaryFixedVariant",
        )
        for ((role, name) in roles) assertEquals(role.get(expected), role.get(actual), "$message ($name)")
    }

    /**
     * Dinamis tanpa skema wallpaper (dynamic=null, mis. di bawah Android 12): fallback HCT
     * TonalSpot dari seed 0xFF1D5867 — BUKAN skema dinamis wallpaper, dan tes ini mengatakannya
     * eksplisit lewat kesamaan dengan skema Kustom yang sama.
     */
    @Test fun dynamicWithoutWallpaperFallsBackToHctTonalSpot() {
        for (dark in listOf(false, true)) {
            val fallback = ThemeEngine.scheme(ThemeSpec(palette = ThemePalette.Dinamis), dark, dynamic = null)
            val expected = ThemeEngine.scheme(ThemeSpec(ThemePalette.Kustom, 0xFF1D5867.toInt(), ThemeStyle.TonalSpot, paper = false), dark)
            assertSameScheme(expected, fallback, "fallback Dinamis = HCT TonalSpot seed 0xFF1D5867, bukan wallpaper")
        }
    }

    @Test fun customThemesFromExtremeSeedsStayReadable() {
        val seeds = ThemeEngine.quickSeeds.map { it.toInt() } + listOf(0xFFFFFF00, 0xFFFF0000, 0xFF00FF00, 0xFFF4F4F4, 0xFF000000, 0xFF00E5FF).map { it.toInt() }
        for (seed in seeds) for (style in ThemeStyle.entries) for (paper in listOf(true, false)) for (dark in listOf(false, true)) {
            check("seed=%08X/${style.name}/paper=$paper".format(seed), ThemeSpec(ThemePalette.Kustom, seed, style, paper), dark)
        }
    }

    @Test fun handcraftedThemeIsUnchanged() {
        assertEquals(LightScheme, ThemeEngine.scheme(ThemeSpec.Default, dark = false))
        assertEquals(DarkScheme, ThemeEngine.scheme(ThemeSpec.Default, dark = true))
        val modules = ModuleColors(dark = false, spec = ThemeSpec.Default, primary = LightScheme.primary)
        assertEquals(TaskifyModule.Tasks.light, modules.accent(TaskifyModule.Tasks))
    }

    @Test fun paperKeepsTaskifySurfacesButTakesPaletteColors() {
        val forest = ThemeEngine.scheme(ThemeSpec(palette = ThemePalette.Hutan), dark = false)
        assertEquals(LightScheme.background, forest.background, "kertas krem tetap")
        assertNotEquals(LightScheme.primary, forest.primary, "warna utama dari palet")
        val graphite = ThemeEngine.scheme(ThemeSpec(palette = ThemePalette.Grafit), dark = false)
        assertNotEquals(LightScheme.background, graphite.background, "Grafit memakai permukaan sendiri")
    }

    @Test fun moduleAccentsKeepTheirIdentityAfterHarmonizing() {
        val spec = ThemeSpec(palette = ThemePalette.Lavender)
        val s = ThemeEngine.scheme(spec, dark = false)
        val modules = ModuleColors(false, spec, s.primary)
        // Keuangan tetap kehijauan, Sandi tetap kemerahan.
        val finance = modules.accent(TaskifyModule.Finance)
        val sandi = modules.accent(TaskifyModule.Sandi)
        assertTrue(finance.green > finance.red, "Keuangan tetap hijau")
        assertTrue(sandi.red > sandi.green && sandi.red > sandi.blue, "Sandi tetap merah")
    }
}
