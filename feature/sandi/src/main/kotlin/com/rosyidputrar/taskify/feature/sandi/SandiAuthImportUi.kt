package com.rosyidputrar.taskify.feature.sandi

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.material3.ModalBottomSheetProperties
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily

/** Ringkasan impor 2FA (dipisah supaya mudah diuji). */
fun authImportLines(ui: AuthImportUi): List<String> = buildList {
    if (ui.scanned == 0) return@buildList
    add(
        if (ui.batchSize > 1) "QR terbaca: ${ui.batchSize - ui.missing.size} dari ${ui.batchSize}."
        else "QR terbaca: ${ui.scanned}.",
    )
    if (ui.missing.isNotEmpty()) add("Belum dipindai: QR " + ui.missing.joinToString(", ") + ". Akun di QR itu tidak ikut kalau dilewati.")
    if (ui.add > 0) add("${ui.add} akun 2FA baru.")
    if (ui.scannedLabels.isNotEmpty()) add("Akun terbaca: " + ui.scannedLabels.joinToString(", ") + if (ui.scannedLabels.size >= 10) ", …" else "")
    if (ui.attach.isNotEmpty()) add("${ui.attach.size} dipasang ke sandi yang sudah ada: " + ui.attach.take(5).joinToString(", ") + if (ui.attach.size > 5) ", …" else "")
    if (ui.identical > 0) add("${ui.identical} sudah ada di brankas, dilewati.")
    if (ui.duplicates > 0) add("${ui.duplicates} akun terpindai lebih dari sekali, dihitung sekali.")
    if (ui.unsupported.isNotEmpty()) add("Tidak didukung (${ui.unsupported.size}): " + ui.unsupported.joinToString(", ") { (l, r) -> "$l — $r" })
}

/** Isi lembar impor 2FA (tanpa kolom teks, jadi bisa diuji & dipotret langsung). */
@Composable
fun AuthImportContent(
    ui: AuthImportUi,
    state: SandiUiState,
    actions: SandiActions,
    onScan: () -> Unit,
    onPhoto: () -> Unit,
    onPaste: () -> Unit,
) {
    val busy = state.busy != null
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Impor 2FA dari Google Authenticator", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
        Text(
            "Di HP yang ada Google Authenticator: menu → Transfer akun → Ekspor akun → pilih akun → Berikutnya. " +
                "Pindai QR yang muncul dari HP ini. Kalau ada beberapa QR, pindai semuanya.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // D-10: QR dari sumber lain (situs, pesan, foto acak) bisa memasang kunci milik orang lain.
        Text(
            "Pindai hanya QR dari menu Transfer akun di aplikasi authenticator kamu. QR dari situs atau pesan lain bisa memasang kunci 2FA milik orang lain ke brankas ini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        FilledTonalButton(onClick = onScan, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (ui.scanned == 0) "Pindai QR" else "Pindai QR berikutnya")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPhoto, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Image, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Dari foto")
            }
            OutlinedButton(onClick = onPaste, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tempel teks")
            }
        }
        val lines = authImportLines(ui)
        if (lines.isNotEmpty()) {
            HorizontalDivider()
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (ui.attachEnabled) {
                Row(Modifier.fillMaxWidth().toggleable(ui.attachOn, role = Role.Switch) { actions.setAuthAttach(it) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pasang ke sandi yang cocok", style = MaterialTheme.typography.bodyLarge)
                        Text("Situs/judul dan nama pengguna sama, dan sandinya belum punya 2FA. Matikan untuk menyimpan sebagai item terpisah.",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(ui.attachOn, onCheckedChange = null)
                }
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text("Setelah impor, cocokkan satu kode dengan Google Authenticator sebelum menghapus akun di sana.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(12.dp))
            }
        }
        BusyLine(state.busy, state.error)
        Row {
            TextButton(onClick = actions::closeAuthImport, enabled = !busy) { Text("Batal") }
            Spacer(Modifier.weight(1f))
            Button(onClick = actions::confirmAuthImport, enabled = ui.total > 0 && !busy) { Text(if (ui.total > 0) "Impor ${ui.total}" else "Impor") }
        }
    }
}

/** Pemindai, pemilih foto, papan klip + lembar impor 2FA. Bagian dari [ExportHost]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthImportHost(state: SandiUiState, actions: SandiActions) {
    val ui = state.authImport ?: return
    val context = LocalContext.current
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { actions.addAuthImage(it?.toString()) }
    ModalBottomSheet(
        onDismissRequest = { if (state.busy == null) actions.closeAuthImport() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Label akun tidak boleh masuk screenshot / Recents.
        properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn),
    ) {
        AuthImportContent(
            ui, state, actions,
            onScan = { scanQr(context, actions) },
            onPhoto = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPaste = { pasteFromClipboard(context, actions) },
        )
    }
}

/** Pemindai QR Google Play Services: UI-nya milik Google, Taskify tidak butuh izin kamera. */
private fun scanQr(context: Context, actions: SandiActions) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
    try {
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code -> code.rawValue?.let(actions::addAuthText) ?: actions.authImportProblem("QR terbaca tapi isinya kosong.") }
            .addOnFailureListener { e ->
                actions.authImportProblem("Pemindai Google tidak tersedia di HP ini (${e.message ?: "Play Services"}). Pakai \"Dari foto\" atau \"Tempel teks\".")
            }
    } catch (e: Exception) {
        actions.authImportProblem("Pemindai Google tidak tersedia di HP ini. Pakai \"Dari foto\" atau \"Tempel teks\".")
    }
}

/** Tempel dari papan klip; teks otpauth dihapus dari papan klip setelah dibaca (isinya kunci rahasia). */
private fun pasteFromClipboard(context: Context, actions: SandiActions) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val text = runCatching { clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
    if (text.isNullOrBlank()) {
        actions.authImportProblem("Papan klip kosong. Salin dulu teks otpauth-migration:// atau otpauth://.")
        return
    }
    actions.addAuthText(text)
    if (text.contains("otpauth", ignoreCase = true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) runCatching { clipboard?.clearPrimaryClip() }
}
