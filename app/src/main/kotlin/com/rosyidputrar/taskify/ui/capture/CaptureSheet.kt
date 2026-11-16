package com.rosyidputrar.taskify.ui.capture

import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.domain.capture.CaptureType
import com.rosyidputrar.taskify.core.domain.capture.parseCapture
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import org.koin.compose.viewmodel.koinViewModel

/** Jenis hasil tangkapan. [Auto] = ditebak dari teks (nominal → transaksi, "j:" → jurnal, …). */
enum class CaptureKind(val label: String, val module: TaskifyModule?) {
    Auto("Otomatis", null),
    Task("Tugas", TaskifyModule.Tasks),
    Note("Catatan", TaskifyModule.Notes),
    Journal("Jurnal", TaskifyModule.Journal),
    Transaction("Transaksi", TaskifyModule.Finance),
}

/**
 * Sheet Tangkap: satu kotak teks untuk semua jenis catatan cepat.
 * Otomatis: "j: …" → jurnal, "c: …" → catatan, ada nominal → transaksi, selain itu tugas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(
    initialText: String,
    startWithTypePicker: Boolean,
    onDismiss: () -> Unit,
    viewModel: CaptureViewModel = koinViewModel(),
) {
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf(initialText) }
    var kind by rememberSaveable { mutableStateOf(CaptureKind.Auto) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!startWithTypePicker) focusRequester.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("TANGKAP", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.primary)
            Text(
                if (startWithTypePicker) "Mau menangkap apa?" else "Tulis apa saja",
                style = MaterialTheme.typography.headlineSmall,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CaptureKind.entries, key = { it.name }) { option ->
                    val accent = option.module?.let { TaskifyTheme.modules.accent(it) } ?: MaterialTheme.colorScheme.primary
                    FilterChip(
                        selected = option == kind,
                        onClick = {
                            kind = option
                            focusRequester.requestFocus()
                        },
                        label = { Text(option.label) },
                        leadingIcon = { Icon(option.icon(), contentDescription = null) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.16f),
                            selectedLabelColor = accent,
                            selectedLeadingIconColor = accent,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(kind.placeholder()) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Text(
                "Contoh: \"besok jam 9 rapat #kerja\", \"makan siang 35rb\", \"j: hari yang tenang\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    kind.hint(text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Tutup") }
                Button(onClick = { viewModel.save(kind, text) { haptics.success(); onDismiss() } }, enabled = text.isNotBlank()) { Text("Simpan") }
            }
        }
    }
}

private fun CaptureKind.icon(): ImageVector = module?.icon ?: Icons.Outlined.AutoAwesome

private fun CaptureKind.placeholder(): String = when (this) {
    CaptureKind.Auto -> "besok jam 9 rapat #kerja"
    CaptureKind.Task -> "kirim laporan jumat !tinggi"
    CaptureKind.Note -> "ide: fitur ekspor ke PDF"
    CaptureKind.Journal -> "hari ini terasa ringan…"
    CaptureKind.Transaction -> "kopi 25rb"
}

/** Keterangan tujuan simpan di bawah kotak teks (pratinjau hasil parser). */
private fun CaptureKind.hint(text: String): String {
    val c = parseCapture(text, Dates.today())
    return when (this) {
        CaptureKind.Auto -> when (c.type) {
            CaptureType.Journal -> "Disimpan ke jurnal hari ini."
            CaptureType.Note -> "Disimpan sebagai catatan bebas."
            CaptureType.Expense -> "Pengeluaran ${Rupiah.format(c.amount ?: 0)}" + (c.categoryHint?.let { " · kategori $it" } ?: "") + "."
            CaptureType.Income -> "Pemasukan ${Rupiah.format(c.amount ?: 0)}."
            CaptureType.Task -> if (text.isBlank()) "Awali \"j:\" untuk jurnal, \"c:\" untuk catatan; nominal jadi transaksi." else "Disimpan sebagai tugas."
        }
        CaptureKind.Task -> "Disimpan sebagai tugas."
        CaptureKind.Journal -> "Disimpan ke jurnal hari ini."
        CaptureKind.Note -> "Disimpan sebagai catatan bebas."
        CaptureKind.Transaction -> c.amount?.let { "${if (c.type == CaptureType.Income) "Pemasukan" else "Pengeluaran"} ${Rupiah.format(it)} ke dompet pertama." } ?: "Sertakan nominal, mis. \"kopi 25rb\"."
    }
}
