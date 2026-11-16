package com.rosyidputrar.taskify.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Warna bernama yang disimpan di DB (`task_lists.color`, `tags.color`, `categories.color`, …).
 * Nama sama dengan webapp agar data tetap cocok; nadanya versi "tinta" (lebih kalem, kontras
 * ≥ 4.5:1 sebagai teks di permukaan kertas/tinta).
 */
object NamedPalette {
    private data class Tone(val light: Color, val dark: Color)

    private val tones = linkedMapOf(
        "violet" to Tone(Color(0xFF6A3FA0), Color(0xFFC7AEF0)),
        "indigo" to Tone(Color(0xFF3F4AA0), Color(0xFFAEB6F0)),
        "sky" to Tone(Color(0xFF1C5F86), Color(0xFF8CC8EA)),
        "teal" to Tone(Color(0xFF1B6763), Color(0xFF86D3CB)),
        "emerald" to Tone(Color(0xFF2A6A44), Color(0xFF8FD6A8)),
        "amber" to Tone(Color(0xFF7A5600), Color(0xFFEBC56A)),
        "orange" to Tone(Color(0xFF94440F), Color(0xFFF2AE7E)),
        "red" to Tone(Color(0xFFA32A22), Color(0xFFF2A49C)),
        "rose" to Tone(Color(0xFF9A2C4A), Color(0xFFF2A2B8)),
        "pink" to Tone(Color(0xFF94306E), Color(0xFFF0A6D2)),
        "fuchsia" to Tone(Color(0xFF7E2E8C), Color(0xFFE6A6EF)),
        "slate" to Tone(Color(0xFF4A5560), Color(0xFFB8C2CC)),
    )

    val names: List<String> = tones.keys.toList()

    fun color(name: String?, dark: Boolean): Color {
        val tone = tones[name] ?: tones.getValue("slate")
        return if (dark) tone.dark else tone.light
    }
}

/** Warna bernama sesuai tema aktif. */
@Composable
@ReadOnlyComposable
fun namedColor(name: String?): Color = NamedPalette.color(name, TaskifyTheme.isDark)
