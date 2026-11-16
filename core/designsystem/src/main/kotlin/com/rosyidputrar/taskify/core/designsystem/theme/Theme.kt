package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
    System("Ikuti sistem"),
    Light("Terang"),
    Dark("Gelap"),
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * Warna modul sesuai tema aktif. Desain asli memakai warna racikan manual; tema lain
 * menyelaraskannya ke warna utama lewat [ThemeEngine.moduleAccent] (dihitung sekali per tema).
 */
@Immutable
class ModuleColors(private val dark: Boolean, spec: ThemeSpec = ThemeSpec.Default, primary: Color = Color.Unspecified) {
    private val accents: Map<TaskifyModule, Color> = TaskifyModule.entries.associateWith { m ->
        if (spec.isHandcrafted || primary == Color.Unspecified) (if (dark) m.dark else m.light) else ThemeEngine.moduleAccent(m, spec, dark, primary)
    }

    /** Warna aksen modul: ikon, teks penanda, isi tab. */
    fun accent(module: TaskifyModule): Color = accents.getValue(module)

    /** Teks/ikon di atas [accent]. */
    fun onAccent(module: TaskifyModule): Color = ThemeEngine.onModuleAccent(dark)

    /** Tint lembut untuk latar kartu modul. */
    fun tint(module: TaskifyModule, surface: Color): Color = lerp(surface, accent(module), if (dark) 0.16f else 0.10f)

    private fun lerp(a: Color, b: Color, t: Float) = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f,
    )
}

val LocalModuleColors = staticCompositionLocalOf { ModuleColors(dark = false) }
val LocalThemeSpec = staticCompositionLocalOf { ThemeSpec.Default }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val TaskifyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun TaskifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    spec: ThemeSpec = ThemeSpec.Default,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamic = if (spec.palette == ThemePalette.Dinamis && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    val scheme = remember(spec, darkTheme, dynamic) { ThemeEngine.scheme(spec, darkTheme, dynamic) }
    val modules = remember(spec, darkTheme, scheme.primary) { ModuleColors(darkTheme, spec, scheme.primary) }
    CompositionLocalProvider(
        LocalModuleColors provides modules,
        LocalIsDarkTheme provides darkTheme,
        LocalThemeSpec provides spec,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TaskifyTypography,
            shapes = TaskifyShapes,
            content = content,
        )
    }
}

object TaskifyTheme {
    val modules: ModuleColors
        @Composable @ReadOnlyComposable get() = LocalModuleColors.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDarkTheme.current

    val spec: ThemeSpec
        @Composable @ReadOnlyComposable get() = LocalThemeSpec.current
}
