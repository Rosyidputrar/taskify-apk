package com.rosyidputrar.taskify.core.designsystem.theme

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

/** Warna modul sesuai tema aktif. */
@Immutable
class ModuleColors(private val dark: Boolean) {
    /** Warna aksen modul: ikon, teks penanda, isi tab. */
    fun accent(module: TaskifyModule): Color = if (dark) module.dark else module.light

    /** Teks/ikon di atas [accent]. */
    fun onAccent(module: TaskifyModule): Color = if (dark) TaskifyModule.onDark else TaskifyModule.onLight

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
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalModuleColors provides ModuleColors(darkTheme),
        LocalIsDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
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
}
