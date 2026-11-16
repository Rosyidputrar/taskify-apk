package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Dialog bersama saat meninggalkan editor yang belum disimpan: Simpan / Buang / Batal.
 * Dipakai semua editor tanpa simpan otomatis supaya perilakunya seragam.
 */
@Composable
fun DiscardChangesDialog(what: String, onSave: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simpan perubahan?") },
        text = { Text("Perubahan pada $what belum disimpan. Kalau dibuang, isinya hilang.") },
        confirmButton = { Button(onClick = onSave) { Text("Simpan") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Batal") }
                TextButton(onClick = { haptics.error(); onDiscard() }) { Text("Buang", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}
