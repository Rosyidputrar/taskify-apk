package com.rosyidputrar.taskify.ui.gate

import com.rosyidputrar.taskify.core.designsystem.component.TaskifyLoadingIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.AppConfig
import com.rosyidputrar.taskify.BuildConfig
import com.rosyidputrar.taskify.feature.auth.component.AuthScaffold

/** Konfigurasi server belum lengkap: aplikasi tetap terbuka dengan penjelasan, bukan crash. */
@Composable
fun ConfigMissingScreen(onPreview: (() -> Unit)?) {
    AuthScaffold(
        tab = "Konfigurasi",
        title = "Server belum diatur",
        subtitle = "APK ini dibangun tanpa alamat server, jadi belum bisa masuk atau menyinkronkan data.",
    ) {
        AppConfig.entries.filter { it.required && !it.isSet }.forEach {
            Text("• ${it.key} — ${it.description}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Isi GitHub Secrets repo (atau local.properties saat build lokal), lalu build ulang APK.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onPreview != null && BuildConfig.DEBUG) {
            OutlinedButton(onClick = onPreview) { Text("Lihat tampilan tanpa akun") }
        }
    }
}

/**
 * Menyiapkan database setelah masuk. Bila gagal, galat aslinya ditampilkan (bukan berputar
 * selamanya) dengan pilihan Coba lagi atau Keluar.
 */
@Composable
fun PreparingScreen(error: String? = null, onRetry: () -> Unit = {}, onSignOut: () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
                if (error == null) {
                    TaskifyLoadingIndicator(size = 48.dp)
                    Text("Menyiapkan data di perangkat…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("Data di perangkat gagal disiapkan", style = MaterialTheme.typography.titleMedium)
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    androidx.compose.material3.Button(onClick = onRetry) { Text("Coba lagi") }
                    OutlinedButton(onClick = onSignOut) { Text("Keluar") }
                }
            }
        }
    }
}
