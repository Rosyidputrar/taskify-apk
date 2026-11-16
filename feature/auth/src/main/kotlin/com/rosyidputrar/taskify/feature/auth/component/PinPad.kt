package com.rosyidputrar.taskify.feature.auth.component

import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Titik PIN: terisi = sudah diketik. Jumlah titik = panjang PIN (verifikasi otomatis). */
@Composable
fun PinDots(filled: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "$filled dari $total angka" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(total) { index ->
            val on = index < filled
            Box(
                Modifier
                    .size(16.dp)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

/** Keypad angka besar ala kunci layar, dengan tombol hapus. */
@Composable
fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    @Suppress("NAME_SHADOWING") val onDigit: (Char) -> Unit = { haptics.key(); onDigit(it) }
    @Suppress("NAME_SHADOWING") val onBackspace: () -> Unit = { haptics.key(); onBackspace() }
    val rows = listOf("123", "456", "789")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { row.forEach { Key(it.toString(), enabled) { onDigit(it) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(72.dp))
            Key("0", enabled) { onDigit('0') }
            Surface(onClick = onBackspace, enabled = enabled, shape = CircleShape, color = MaterialTheme.colorScheme.background, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Hapus")
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
