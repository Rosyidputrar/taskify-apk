package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Konfirmasi untuk penghapusan yang TIDAK bisa diurungkan (tidak lewat Sampah) — mis. berkas yang
 * ikut dihapus dari server. Penghapusan yang punya Urungkan / Sampah tidak perlu dialog ini.
 */
@Composable
fun ConfirmDeleteDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmLabel: String = "Hapus permanen") {
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { haptics.error(); onConfirm() }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
