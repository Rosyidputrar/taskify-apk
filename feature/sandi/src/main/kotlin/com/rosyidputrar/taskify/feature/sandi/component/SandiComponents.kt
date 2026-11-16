package com.rosyidputrar.taskify.feature.sandi.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.sandi.Strength
import com.rosyidputrar.taskify.core.domain.sandi.avatarIndex
import com.rosyidputrar.taskify.core.domain.sandi.avatarInitial

private val AVATAR_COLORS = listOf("violet", "rose", "teal", "sky", "amber", "fuchsia")

/** Avatar huruf; warna deterministik dari judul (sama di semua perangkat). */
@Composable
fun SandiAvatar(title: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val color = namedColor(AVATAR_COLORS[avatarIndex(title, AVATAR_COLORS.size)])
    Box(modifier.size(size).background(color, RoundedCornerShape(size / 3)), contentAlignment = Alignment.Center) {
        Text(avatarInitial(title), color = if (color.luminance() > 0.45f) Color(0xFF1B1B1B) else Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Kolom sandi dengan tombol tampil/sembunyikan; papan ketik tidak menyimpan riwayat. */
@Composable
fun SecretField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    error: String? = null,
    supporting: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (visible) "Sembunyikan" else "Tampilkan")
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Meter kekuatan 4 ruas + label & bit. */
@Composable
fun StrengthMeter(strength: Strength?, modifier: Modifier = Modifier) {
    val colors = listOf(namedColor("red"), namedColor("orange"), namedColor("amber"), namedColor("emerald"), namedColor("teal"))
    val s = strength
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = s?.let { "Kekuatan ${it.label.label}, sekitar ${it.bits.toInt()} bit" } ?: "Kekuatan belum dinilai"
    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i ->
                val on = s != null && s.score > i
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (on) colors[s!!.score] else MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
        if (s != null) Text("${s.label.label} · ±${s.bits.toInt()} bit", style = MaterialTheme.typography.labelMedium, color = colors[s.score])
    }
}

/** Cincin hitung mundur kode TOTP. */
@Composable
fun CountdownRing(fraction: Float, seconds: Int, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val warn = seconds <= 5
    val color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size).clearAndSetSemantics { contentDescription = "$seconds detik lagi" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 3.dp.toPx()
            drawCircle(track, style = Stroke(stroke))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke))
        }
        Text("$seconds", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

