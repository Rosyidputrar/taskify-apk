package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.material3.ColorScheme
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
            if (p == ThemePalette.KertasTinta && c == ThemeContrast.Standard) continue // diracik & diukur manual (docs/DESIGN.md)
            check("${p.name}/${c.name}", ThemeSpec(palette = p, contrast = c), dark)
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
