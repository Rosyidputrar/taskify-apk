package com.rosyidputrar.taskify.ui.settings

import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.AppConfig
import com.rosyidputrar.taskify.BuildConfig
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeMode
import com.rosyidputrar.taskify.ui.placeholder.BackButton
import org.koin.compose.viewmodel.koinViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.auth.AuthenticatorSettings

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    themeMode: ThemeMode,
    pinnedModule: TaskifyModule?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPinnedModuleChange: (TaskifyModule?) -> Unit,
    haptics: Boolean = true,
    onHapticsChange: (Boolean) -> Unit = {},
    onBack: (() -> Unit)?,
    onOpenModule: (TaskifyModule) -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val tick = rememberHaptics()
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::export) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            ModuleHeader(
                TaskifyModule.Settings,
                title = "Pengaturan",
                subtitle = "Berlaku untuk perangkat ini",
                navigation = onBack?.let { { BackButton(it) } },
            )
        }
        if (viewModel.hasAccount) item { AccountSection(viewModel) }
        item { SecuritySection(viewModel) }
        if (viewModel.hasAccount) item {
            SettingsSection("Verifikasi dua langkah") { AuthenticatorSettings(onMessage = viewModel::show) }
        }
        item { NotificationSection(viewModel) }
        if (viewModel.hasAccount) item { SyncSection(viewModel) }
        item {
            SettingsSection("Tampilan") {
                Text("Tema", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == themeMode,
                            onClick = { tick.tick(); onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            label = { Text(mode.label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = "Getaran",
                    subtitle = "Getar halus saat menyelesaikan tugas, menyalin, memilih, menggeser, dan saat PIN salah.",
                    checked = haptics,
                    onChange = onHapticsChange,
                )
            }
        }
        item {
            SettingsSection("Dok") {
                Text(
                    pinnedModule?.let { "${it.label} disematkan di dok." }
                        ?: "Belum ada modul yang disematkan. Tahan kartu di Ruang untuk menyematkan.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (pinnedModule != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { onPinnedModuleChange(null) }) { Text("Lepas sematan") }
                }
            }
        }
        item {
            SettingsSection("Server") {
                Text(
                    if (AppConfig.isServerReady) {
                        "Konfigurasi wajib lengkap. Login & sinkronisasi aktif."
                    } else {
                        "Konfigurasi wajib belum lengkap. Isi GitHub Secrets atau local.properties lalu build ulang."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                AppConfig.entries.forEach { entry -> ConfigRow(entry) }
            }
        }
        if (viewModel.hasAccount) item {
            SettingsSection("Data, tag & Sampah") {
                Text("Ekspor semua data", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Satu file JSON berisi seluruh data di perangkat ini (termasuk Sampah), format sama dengan ekspor webapp. Jalan offline; isi lampiran tetap di server.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { exportTo.launch("taskify-ekspor-${Dates.today()}.json") }, enabled = !exporting) {
                    Text(if (exporting) "Mengekspor…" else "Ekspor JSON")
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onOpenModule(TaskifyModule.Tags) }) { Text("Kelola tag") }
                    OutlinedButton(onClick = { onOpenModule(TaskifyModule.Trash) }) { Text("Buka Sampah") }
                }
            }
        }
        item {
            SettingsSection("Tentang") {
                Text("Taskify ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (BuildConfig.DEBUG) "Build debug" else "Build rilis",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            title.uppercase(),
            style = TaskifyTextStyles.indexLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() },
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
        }
    }
}

@Composable
private fun ConfigRow(entry: AppConfig.Entry) {
    val (icon, tint, status) = when {
        entry.isSet -> Triple(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary, "terisi")
        entry.required -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, "kosong, wajib")
        else -> Triple(Icons.Outlined.RemoveCircleOutline, MaterialTheme.colorScheme.onSurfaceVariant, "kosong, fitur disembunyikan")
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.key, style = TaskifyTextStyles.code)
            Text("${entry.description} · $status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
