package com.rosyidputrar.taskify.ui.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rosyidputrar.taskify.core.designsystem.component.IndexTab
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeContrast
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeEngine
import com.rosyidputrar.taskify.core.designsystem.theme.ThemePalette
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeSpec
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeStyle
import com.rosyidputrar.taskify.core.designsystem.theme.isDark
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily

/** Palet yang bisa dipilih di perangkat ini (Material You butuh Android 12+). */
internal fun availablePalettes(sdk: Int = Build.VERSION.SDK_INT): List<ThemePalette> =
    ThemePalette.entries.filter { it != ThemePalette.Dinamis || sdk >= Build.VERSION_CODES.S }

/**
 * Pengaturan › Tampilan: palet warna (siap pakai, Material You, Kustom), tingkat kontras, dan
 * pintu ke Studio tema. Setiap pilihan langsung diterapkan ke seluruh aplikasi.
 */
@Composable
fun ThemePaletteSettings(theme: ThemeSpec, onThemeChange: (ThemeSpec) -> Unit) {
    val haptics = rememberHaptics()
    var studio by rememberSaveable { mutableStateOf(false) }
    Text("Palet warna", style = MaterialTheme.typography.titleMedium)
    Text(
        "Warna modul ikut diselaraskan; kontras teks tetap dijaga di setiap palet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    val dark = TaskifyTheme.isDark
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(availablePalettes(), key = { it.name }) { p ->
            // Kertas & Tinta terkunci di kontras Standar (lihat ThemeSpec.effectiveContrast):
            // memilihnya mengembalikan kontras ke Standar supaya tema bawaan tetap sama.
            val spec = if (p == ThemePalette.Kustom) theme.copy(palette = p) else ThemeSpec(palette = p, seed = theme.seed, style = theme.style, paper = theme.paper, contrast = if (p == ThemePalette.KertasTinta) ThemeContrast.Standard else theme.contrast)
            PaletteSwatch(
                palette = p,
                spec = spec,
                dark = dark,
                selected = theme.palette == p,
                onClick = {
                    haptics.tick()
                    if (p == ThemePalette.Kustom) studio = true else onThemeChange(spec)
                },
            )
        }
    }
    if (theme.palette == ThemePalette.Kustom) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(theme.seed)))
            Spacer(Modifier.width(8.dp))
            Text("${hex(theme.seed)} · ${theme.style.label}${if (theme.paper) " · nuansa kertas" else ""}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { studio = true }) { Text("Ubah") }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Kontras", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    // Kertas & Tinta diracik manual pada Standar (kontras terkunci), Material You memakai
    // skema wallpaper sistem (kontras tidak berlaku): kontrol dimatikan supaya tidak tampak
    // berfungsi padahal tidak berpengaruh.
    val contrastEnabled = theme.palette != ThemePalette.KertasTinta && theme.palette != ThemePalette.Dinamis
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeContrast.entries.forEachIndexed { i, c ->
            SegmentedButton(
                selected = theme.contrast == c,
                onClick = { haptics.tick(); onThemeChange(theme.copy(contrast = c)) },
                shape = SegmentedButtonDefaults.itemShape(i, ThemeContrast.entries.size),
                enabled = contrastEnabled,
                label = { Text(c.label) },
            )
        }
    }
    if (theme.palette == ThemePalette.KertasTinta) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Kertas & Tinta diracik manual pada kontras Standar, jadi kontras terkunci supaya tema bawaan tetap sama.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (theme.palette == ThemePalette.Dinamis) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Material You memakai warna wallpaper sistem; kontras tidak berlaku di sini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (studio) {
        ThemeStudio(
            initial = theme.copy(palette = ThemePalette.Kustom),
            onApply = { haptics.success(); studio = false; onThemeChange(it) },
            onDismiss = { studio = false },
        )
    }
}

/** Kartu mini sebuah palet: permukaan + warna utama, sekunder, dan satu warna modul. */
@Composable
private fun PaletteSwatch(palette: ThemePalette, spec: ThemeSpec, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val scheme = remember(spec, dark) { ThemeEngine.scheme(spec, dark) }
    val accent = remember(spec, dark, scheme) { ThemeEngine.moduleAccent(TaskifyModule.Journal, spec, dark, scheme.primary) }
    val ring = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "Palet ${palette.label}"; stateDescription = if (selected) "Dipilih" else "" }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 76.dp, height = 64.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (palette == ThemePalette.Kustom) Modifier.background(Brush.sweepGradient(rainbow()))
                    else Modifier.background(scheme.surfaceContainerLow),
                )
                .border(if (selected) 2.5.dp else 1.dp, if (selected) ring else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (palette == ThemePalette.Kustom) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.onSurface) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    Dot(scheme.primary, scheme.surfaceContainerLow)
                    Dot(scheme.tertiary, scheme.surfaceContainerLow)
                    Dot(accent, scheme.surfaceContainerLow)
                }
            }
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(palette.label, style = MaterialTheme.typography.labelMedium, maxLines = 2, textAlign = TextAlign.Center, minLines = 2)
    }
}

@Composable
private fun Dot(color: Color, edge: Color) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(edge).padding(2.dp).clip(CircleShape).background(color))
}

private fun rainbow(): List<Color> = (0..6).map { Color(ThemeEngine.seedFrom(it * 60.0, 70.0)) }

internal fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

internal fun parseHex(text: String): Int? {
    val clean = text.trim().removePrefix("#")
    if (!Regex("^[0-9a-fA-F]{6}$").matches(clean)) return null
    return (0xFF000000.toInt() or clean.toInt(16))
}

/**
 * Studio tema kustom: pilih warna dasar (contoh cepat, hue + kekuatan warna, atau hex), gaya
 * palet, dan nuansa kertas. Pratinjau terang & gelap ikut berubah langsung; palet tonal HCT
 * ditampilkan supaya terlihat warna apa saja yang akan dipakai.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeStudio(initial: ThemeSpec, onApply: (ThemeSpec) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            ThemeStudioContent(initial, onApply, onDismiss)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeStudioContent(initial: ThemeSpec, onApply: (ThemeSpec) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    var seed by rememberSaveable { mutableIntStateOf(initial.seed) }
    var hue by rememberSaveable { mutableDoubleStateOf(ThemeEngine.hueOf(initial.seed)) }
    var chroma by rememberSaveable { mutableDoubleStateOf(ThemeEngine.chromaOf(initial.seed).coerceIn(8.0, 100.0)) }
    var style by rememberSaveable { mutableStateOf(initial.style) }
    var paper by rememberSaveable { mutableStateOf(initial.paper) }
    var hexText by rememberSaveable { mutableStateOf(hex(initial.seed)) }
    val spec = ThemeSpec(ThemePalette.Kustom, seed, style, paper, initial.contrast)
    fun pick(newSeed: Int, fromSliders: Boolean = false) {
        seed = newSeed
        hexText = hex(newSeed)
        if (!fromSliders) {
            hue = ThemeEngine.hueOf(newSeed)
            chroma = ThemeEngine.chromaOf(newSeed).coerceIn(8.0, 100.0)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Tutup studio tema") }
            Text("Studio tema", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily), modifier = Modifier.weight(1f))
            Button(onClick = { onApply(spec) }) { Text("Terapkan") }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Pratinjau langsung, terang & gelap berdampingan.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemePreview(spec, dark = false, Modifier.weight(1f))
                ThemePreview(spec, dark = true, Modifier.weight(1f))
            }

            Text("Warna dasar", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeEngine.quickSeeds.forEach { s ->
                    val c = Color(s.toInt())
                    val chosen = (seed and 0xFFFFFF) == (s.toInt() and 0xFFFFFF)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (chosen) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .selectable(chosen, role = Role.RadioButton) { haptics.tick(); pick(s.toInt()) }
                            .semantics { contentDescription = "Warna ${hex(s.toInt())}" },
                    )
                }
            }
            LabeledSlider(
                label = "Hue",
                value = hue.toFloat(),
                range = 0f..360f,
                track = Brush.horizontalGradient((0..12).map { Color(ThemeEngine.seedFrom(it * 30.0, chroma.coerceAtLeast(40.0))) }),
                valueText = "${hue.toInt()}°",
            ) { hue = it.toDouble(); pick(ThemeEngine.seedFrom(hue, chroma), fromSliders = true) }
            LabeledSlider(
                label = "Kekuatan warna",
                value = chroma.toFloat(),
                range = 8f..100f,
                track = Brush.horizontalGradient(listOf(Color(ThemeEngine.seedFrom(hue, 8.0)), Color(ThemeEngine.seedFrom(hue, 100.0)))),
                valueText = chroma.toInt().toString(),
            ) { chroma = it.toDouble(); pick(ThemeEngine.seedFrom(hue, chroma), fromSliders = true) }
            OutlinedTextField(
                hexText,
                { t ->
                    hexText = t.take(7)
                    parseHex(t)?.let { pick(it); hexText = t.take(7) }
                },
                label = { Text("Kode warna (hex)") },
                singleLine = true,
                isError = parseHex(hexText) == null,
                supportingText = { Text(if (parseHex(hexText) == null) "Format #RRGGBB, mis. #1D5867" else "Warna dasar dipakai sebagai sumber palet tonal") },
                leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(seed))) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Gaya palet", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeStyle.entries.forEach { s ->
                    FilterChip(selected = style == s, onClick = { haptics.tick(); style = s }, label = { Text(s.label) })
                }
            }
            Text(style.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                Modifier.fillMaxWidth().toggleable(paper, role = Role.Switch) { haptics.toggle(it); paper = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Nuansa kertas", style = MaterialTheme.typography.titleSmall)
                    Text("Permukaan krem & tinta arang khas Taskify; warna palet dipakai untuk aksen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = paper, onCheckedChange = null)
            }

            Text("Palet tonal", style = MaterialTheme.typography.titleMedium)
            TonalStrips(seed, style)
            Text(
                "Semua warna diturunkan dari satu warna dasar lewat ruang warna HCT (Material 3), jadi pasangan teks & latar tetap terbaca di mode terang dan gelap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) { Text("Batal") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(spec) }) { Text("Terapkan") }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, track: Brush, valueText: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp).clip(CircleShape).background(track))
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun TonalStrips(seed: Int, style: ThemeStyle) {
    val dark = TaskifyTheme.isDark
    val rows = remember(seed, style, dark) { ThemeEngine.tones(seed, style, dark) }
    val names = listOf("Utama", "Sekunder", "Tersier", "Netral")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { i, tones ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(names[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                Row(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(6.dp))) {
                    tones.forEach { c -> Box(Modifier.weight(1f).fillMaxSize().background(c)) }
                }
            }
        }
    }
}

/** Potongan aplikasi bertema [spec]: header, chip modul, kartu, tombol — untuk pratinjau. */
@Composable
fun ThemePreview(spec: ThemeSpec, dark: Boolean, modifier: Modifier = Modifier) {
    TaskifyTheme(darkTheme = dark, spec = spec) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = modifier,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IndexTab(TaskifyModule.Home, text = if (dark) "Gelap" else "Terang")
                Text("Hari Ini", style = MaterialTheme.typography.headlineSmall)
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("FOKUS", style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Tasks))
                        Text("Kirim laporan", style = MaterialTheme.typography.titleSmall)
                        Text("Hari ini 09:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(TaskifyModule.Tasks, TaskifyModule.Journal, TaskifyModule.Finance, TaskifyModule.Vault, TaskifyModule.Sandi).forEach { m ->
                        Box(Modifier.size(16.dp).clip(CircleShape).background(TaskifyTheme.modules.accent(m)))
                    }
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Simpan") }
                FilledTonalButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Tonal") }
            }
        }
    }
}
